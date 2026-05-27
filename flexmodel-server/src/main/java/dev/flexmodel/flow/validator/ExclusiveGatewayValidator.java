package dev.flexmodel.flow.validator;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.flow.dto.model.FlowElement;
import dev.flexmodel.flow.exception.DefinitionException;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.flow.common.util.FlowModelUtil;
import dev.flexmodel.common.utils.CollectionUtils;
import dev.flexmodel.common.utils.StringUtils;

import java.util.List;
import java.util.Map;

@Singleton
public class ExclusiveGatewayValidator extends ElementValidator {

  protected static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveGatewayValidator.class);

  @Override
  protected void checkOutgoing(Map<String, FlowElement> flowElementMap, FlowElement flowElement) throws DefinitionException {
    List<String> outgoing = flowElement.getOutgoing();

    if (CollectionUtils.isEmpty(outgoing)) {
      throwElementValidatorException(flowElement, ErrorEnum.ELEMENT_LACK_OUTGOING);
    }

    List<String> outgoingList = flowElement.getOutgoing();
    int defaultConditionCount = 0;

    for (String outgoingKey : outgoingList) {
      FlowElement outgoingSequenceFlow = FlowModelUtil.getFlowElement(flowElementMap, outgoingKey);

      String condition = FlowModelUtil.getConditionFromSequenceFlow(outgoingSequenceFlow);
      boolean isDefaultCondition = FlowModelUtil.isDefaultCondition(outgoingSequenceFlow);

      if (StringUtils.isBlank(condition) && !isDefaultCondition) {
        throwElementValidatorException(flowElement, ErrorEnum.EMPTY_SEQUENCE_OUTGOING);
      }
      if (isDefaultCondition) {
        defaultConditionCount++;
      }
    }

    if (defaultConditionCount > 1) {
      throwElementValidatorException(flowElement, ErrorEnum.TOO_MANY_DEFAULT_SEQUENCE);
    }
  }
}
