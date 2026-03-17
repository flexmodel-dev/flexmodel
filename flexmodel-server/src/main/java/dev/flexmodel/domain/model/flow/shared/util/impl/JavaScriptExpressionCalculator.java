package dev.flexmodel.domain.model.flow.shared.util.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.domain.model.flow.exception.ProcessException;
import dev.flexmodel.domain.model.flow.shared.common.ErrorEnum;
import dev.flexmodel.domain.model.flow.shared.util.ExpressionCalculator;
import dev.flexmodel.domain.model.flow.shared.util.JavaScriptUtil;
import dev.flexmodel.shared.utils.JsonUtils;

import java.text.MessageFormat;
import java.util.Map;

public class JavaScriptExpressionCalculator implements ExpressionCalculator {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptExpressionCalculator.class);

  @Override
  public Boolean calculate(String expression, Map<String, Object> dataMap) throws ProcessException {
    if (expression.startsWith("${") && expression.endsWith("}")) {
      expression = expression.substring(2, expression.length() - 1);
    }
    Object result = null;
    try {
      result = JavaScriptUtil.execute(expression, dataMap);
      if (result instanceof Boolean) {
        return (Boolean) result;
      } else {
        LOGGER.warn("the result of expression is not boolean.||expression={}||result={}||dataMap={}",
          expression, result, JsonUtils.getInstance().stringify(dataMap));
        throw new ProcessException(ErrorEnum.MISSING_DATA.getErrNo(), "expression is not instanceof bool.");
      }
    } catch (Exception e) {
      LOGGER.error("calculate expression failed.||message={}||expression={}||dataMap={}, ", e.getMessage(), expression, dataMap, e);
      String jsExFormat = "{0}: expression={1}";
      throw new ProcessException(ErrorEnum.MISSING_DATA, MessageFormat.format(jsExFormat, e.getMessage(), expression));
    } finally {
      LOGGER.info("calculate expression.||expression={}||dataMap={}||result={}", expression, JsonUtils.getInstance().stringify(dataMap), result);
    }
  }
}
