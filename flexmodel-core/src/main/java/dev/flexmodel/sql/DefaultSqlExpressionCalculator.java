package dev.flexmodel.sql;

import dev.flexmodel.ExpressionCalculatorException;
import dev.flexmodel.condition.ConditionNode;
import dev.flexmodel.sql.condition.InlinePlaceholderHandler;
import dev.flexmodel.sql.condition.NamedPlaceholderHandler;
import dev.flexmodel.sql.condition.SqlConditionRenderer;
import dev.flexmodel.sql.condition.SqlRenderContext;
import dev.flexmodel.sql.dialect.SqlDialect;

import java.util.Map;

/**
 * @author cjbi
 */
public class DefaultSqlExpressionCalculator extends SqlExpressionCalculator {

  public DefaultSqlExpressionCalculator(SqlDialect sqlDialect) {
    super(sqlDialect);
  }

  @Override
  public String calculateIncludeValue(String expression) throws ExpressionCalculatorException {
    if (expression == null) {
      throw new ExpressionCalculatorException("Expression is null");
    }
    try {
      ConditionNode condition = parseCondition(expression);
      SqlRenderContext context = new SqlRenderContext(sqlDialect.getIdentifierQuoteString(), new InlinePlaceholderHandler());
      return SqlConditionRenderer.render(condition, context);
    } catch (RuntimeException e) {
      throw new ExpressionCalculatorException(e.getMessage(), e);
    }
  }

  @Override
  public SqlClauseResult calculate(String expression, Map<String, Object> dataMap) throws ExpressionCalculatorException {
    if (expression == null) {
      throw new ExpressionCalculatorException("Expression is null");
    }
    try {
      ConditionNode condition = parseCondition(expression);
      NamedPlaceholderHandler placeholderHandler = new NamedPlaceholderHandler();
      SqlRenderContext context = new SqlRenderContext(sqlDialect.getIdentifierQuoteString(), placeholderHandler);
      String sql = SqlConditionRenderer.render(condition, context);
      return new SqlClauseResult(sql, placeholderHandler.getParameters());
    } catch (RuntimeException e) {
      throw new ExpressionCalculatorException(e.getMessage(), e);
    }
  }

}
