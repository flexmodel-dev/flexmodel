package dev.flexmodel.sql;

import dev.flexmodel.AbstractExpressionCalculator;
import dev.flexmodel.ExpressionCalculatorException;
import dev.flexmodel.sql.dialect.SqlDialect;

import java.util.Set;
import java.util.function.Function;

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

  /**
   * 计算表达式（内联值模式），使用模型解析器区分 JSON 路径和关联字段。
   *
   * @param expression    DSL 条件表达式
   * @param modelResolver 模型名称 → JSON 列名集合的解析函数
   */
  public abstract String calculateIncludeValue(String expression, Function<String, Set<String>> modelResolver) throws ExpressionCalculatorException;

  /**
   * 计算表达式（参数化模式），使用模型解析器区分 JSON 路径和关联字段。
   *
   * @param expression    DSL 条件表达式
   * @param dataMap       参数映射
   * @param modelResolver 模型名称 → JSON 列名集合的解析函数
   */
  public abstract SqlClauseResult calculate(String expression, java.util.Map<String, Object> dataMap, Function<String, Set<String>> modelResolver) throws ExpressionCalculatorException;
}
