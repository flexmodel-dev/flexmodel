package dev.flexmodel.flow.validator;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.flow.dto.model.FlowElement;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.common.utils.CollectionUtils;

import java.util.List;
import java.util.Map;

@Singleton
public class StartEventValidator extends ElementValidator {

  protected static final Logger LOGGER = LoggerFactory.getLogger(StartEventValidator.class);

  @Override
  protected void checkIncoming(Map<String, FlowElement> flowElementMap, FlowElement flowElement) {
    List<String> incoming = flowElement.getIncoming();
    if (!CollectionUtils.isEmpty(incoming)) {
      recordElementValidatorException(flowElement, ErrorEnum.ELEMENT_TOO_MUCH_INCOMING);
    }
  }

}
