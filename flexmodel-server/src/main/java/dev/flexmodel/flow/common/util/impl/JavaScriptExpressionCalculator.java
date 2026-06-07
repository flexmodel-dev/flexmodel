package dev.flexmodel.flow.common.util.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.flow.exception.ProcessException;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.flow.common.util.ExpressionCalculator;
import dev.flexmodel.flow.common.util.JavaScriptUtil;
import dev.flexmodel.JsonUtils;

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
          expression, result, JsonUtils.toJsonString(dataMap));
        throw new ProcessException(ErrorEnum.MISSING_DATA.getErrNo(), "expression is not instanceof bool.");
      }
    } catch (Exception e) {
      LOGGER.error("calculate expression failed.||message={}||expression={}||dataMap={}, ", e.getMessage(), expression, dataMap, e);
      String jsExFormat = "{0}: expression={1}";
      throw new ProcessException(ErrorEnum.MISSING_DATA, MessageFormat.format(jsExFormat, e.getMessage(), expression));
    } finally {
      LOGGER.info("calculate expression.||expression={}||dataMap={}||result={}", expression, JsonUtils.toJsonString(dataMap), result);
    }
  }
}
