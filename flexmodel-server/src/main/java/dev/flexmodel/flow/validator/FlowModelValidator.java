package dev.flexmodel.flow.validator;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.flow.dto.model.FlowElement;
import dev.flexmodel.flow.dto.model.FlowModel;
import dev.flexmodel.flow.dto.param.CommonParam;
import dev.flexmodel.flow.exception.DefinitionException;
import dev.flexmodel.flow.exception.ProcessException;
import dev.flexmodel.flow.common.Constants;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.flow.common.FlowElementType;
import dev.flexmodel.flow.common.util.FlowModelUtil;
import dev.flexmodel.common.utils.CollectionUtils;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class FlowModelValidator {

  protected static final Logger LOGGER = LoggerFactory.getLogger(FlowModelValidator.class);

  @Inject
  ElementValidatorFactory elementValidatorFactory;

  public void validate(FlowModel flowModel) throws ProcessException, DefinitionException {
    this.validate(flowModel, null);
  }

  public void validate(FlowModel flowModel, CommonParam commonParam) throws ProcessException, DefinitionException {
    if (flowModel == null || CollectionUtils.isEmpty(flowModel.getFlowElementList())) {
      LOGGER.warn("message={}", ErrorEnum.MODEL_EMPTY.getErrMsg());
      throw new DefinitionException(ErrorEnum.MODEL_EMPTY);
    }

    List<FlowElement> flowElementList = flowModel.getFlowElementList();
    Map<String, FlowElement> flowElementMap = new HashMap<>();

    for (FlowElement flowElement : flowElementList) {
      if (flowElementMap.containsKey(flowElement.getKey())) {
        String elementName = FlowModelUtil.getElementName(flowElement);
        String elementKey = flowElement.getKey();
        String exceptionMsg = MessageFormat.format(Constants.MODEL_DEFINITION_ERROR_MSG_FORMAT,
          ErrorEnum.ELEMENT_KEY_NOT_UNIQUE, elementName, elementKey);
        LOGGER.warn(exceptionMsg);
        throw new DefinitionException(ErrorEnum.ELEMENT_KEY_NOT_UNIQUE.getErrNo(), exceptionMsg);
      }
      flowElementMap.put(flowElement.getKey(), flowElement);
    }

    int startEventCount = 0;
    int endEventCount = 0;

    for (FlowElement flowElement : flowElementList) {

      ElementValidator elementValidator = elementValidatorFactory.getElementValidator(flowElement);
      elementValidator.validate(flowElementMap, flowElement, commonParam);

      if (FlowElementType.START_EVENT == flowElement.getType()) {
        startEventCount++;
      }

      if (FlowElementType.END_EVENT == flowElement.getType()) {
        endEventCount++;
      }
    }

    if (startEventCount != 1) {
      LOGGER.warn("message={}||startEventCount={}", ErrorEnum.START_NODE_INVALID.getErrMsg(), startEventCount);
      throw new DefinitionException(ErrorEnum.START_NODE_INVALID);
    }

    if (endEventCount < 1) {
      LOGGER.warn("message={}", ErrorEnum.END_NODE_INVALID.getErrMsg());
      throw new DefinitionException(ErrorEnum.END_NODE_INVALID);
    }
  }
}
