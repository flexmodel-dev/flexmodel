package dev.flexmodel.flow.validator;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.flow.dto.model.FlowModel;
import dev.flexmodel.flow.dto.param.CommonParam;
import dev.flexmodel.flow.exception.DefinitionException;
import dev.flexmodel.flow.exception.ProcessException;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.flow.common.util.FlowModelUtil;
import dev.flexmodel.common.utils.CollectionUtils;
import dev.flexmodel.common.utils.StringUtils;

@Singleton
public class ModelValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelValidator.class);

  @Inject
  FlowModelValidator flowModelValidator;

  public void validate(String flowModelStr) throws DefinitionException, ProcessException {
    this.validate(flowModelStr, null);
  }

  public void validate(String flowModelStr, CommonParam commonParam) throws DefinitionException, ProcessException {
    if (StringUtils.isBlank(flowModelStr)) {
      LOGGER.warn("message={}", ErrorEnum.MODEL_EMPTY.getErrMsg());
      throw new DefinitionException(ErrorEnum.MODEL_EMPTY);
    }

    FlowModel flowModel = FlowModelUtil.parseModelFromString(flowModelStr);
    if (flowModel == null || CollectionUtils.isEmpty(flowModel.getFlowElementList())) {
      LOGGER.warn("message={}||flowModelStr={}", ErrorEnum.MODEL_EMPTY.getErrMsg(), flowModelStr);
      throw new DefinitionException(ErrorEnum.MODEL_EMPTY);
    }
    flowModelValidator.validate(flowModel, commonParam);
  }
}
