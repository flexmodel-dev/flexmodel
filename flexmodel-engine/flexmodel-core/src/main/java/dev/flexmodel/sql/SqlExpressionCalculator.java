package dev.flexmodel.sql;

import dev.flexmodel.AbstractExpressionCalculator;
import dev.flexmodel.ExpressionCalculatorException;
import dev.flexmodel.sql.dialect.SqlDialect;

/**
 * @author cjbi
 */
public abstract class SqlExpressionCalculator extends AbstractExpressionCalculator<SqlClauseResult> {

  protected final SqlDialect sqlDialect;

  public SqlExpressionCalculator(SqlDialect sqlDialect) {
    this.sqlDialect = sqlDialect;
  }

  public SqlDialect getSqlDialect() {
    return sqlDialect;
  }

  public abstract String calculateIncludeValue(String expression) throws ExpressionCalculatorException;
}
