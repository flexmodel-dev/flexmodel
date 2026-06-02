package dev.flexmodel.sql;

import dev.flexmodel.ExpressionCalculatorException;
import dev.flexmodel.condition.ConditionNode;
import dev.flexmodel.sql.condition.InlinePlaceholderHandler;
import dev.flexmodel.sql.condition.NamedPlaceholderHandler;
import dev.flexmodel.sql.condition.SqlConditionRenderer;
import dev.flexmodel.sql.condition.SqlRenderContext;
import dev.flexmodel.sql.dialect.SqlDialect;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author cjbi
 */
public class DefaultSqlExpressionCalculator extends SqlExpressionCalculator {

  public DefaultSqlExpressionCalculator(SqlDialect sqlDialect) {
    super(sqlDialect);
  }

  @Override
  public String calculateIncludeValue(String expression) throws ExpressionCalculatorException {
    return calculateIncludeValue(expression, null);
  }

  @Override
  public String calculateIncludeValue(String expression, Function<String, Set<String>> modelResolver) throws ExpressionCalculatorException {
    if (expression == null) {
      throw new ExpressionCalculatorException("Expression is null");
    }
    try {
      ConditionNode condition = parseCondition(expression);
      SqlRenderContext context = new SqlRenderContext(sqlDialect.getIdentifierQuoteString(), new InlinePlaceholderHandler(), sqlDialect);
      if (modelResolver != null) {
        context.setModelResolver(modelResolver);
      }
      return SqlConditionRenderer.render(condition, context);
    } catch (RuntimeException e) {
      throw new ExpressionCalculatorException(e.getMessage(), e);
    }
  }

  @Override
  public SqlClauseResult calculate(String expression, Map<String, Object> dataMap) throws ExpressionCalculatorException {
    return calculate(expression, dataMap, null);
  }

  @Override
  public SqlClauseResult calculate(String expression, Map<String, Object> dataMap, Function<String, Set<String>> modelResolver) throws ExpressionCalculatorException {
    if (expression == null) {
      throw new ExpressionCalculatorException("Expression is null");
    }
    try {
      ConditionNode condition = parseCondition(expression);
      NamedPlaceholderHandler placeholderHandler = new NamedPlaceholderHandler();
      SqlRenderContext context = new SqlRenderContext(sqlDialect.getIdentifierQuoteString(), placeholderHandler, sqlDialect);
      if (modelResolver != null) {
        context.setModelResolver(modelResolver);
      }
      String sql = SqlConditionRenderer.render(condition, context);
      return new SqlClauseResult(sql, placeholderHandler.getParameters());
    } catch (RuntimeException e) {
      throw new ExpressionCalculatorException(e.getMessage(), e);
    }
  }
}
