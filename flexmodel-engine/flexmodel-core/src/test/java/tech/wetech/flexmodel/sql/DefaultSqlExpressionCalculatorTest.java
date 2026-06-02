package dev.flexmodel.sql;

import dev.flexmodel.sql.DefaultSqlExpressionCalculator;
import dev.flexmodel.sql.SqlClauseResult;
import org.junit.jupiter.api.Test;
import dev.flexmodel.ExpressionCalculatorException;
import dev.flexmodel.sql.dialect.MySQLSqlDialect;
import dev.flexmodel.sql.dialect.PostgreSQLSqlDialect;
import dev.flexmodel.sql.dialect.SQLServerSqlDialect;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSqlExpressionCalculatorTest {

  private DefaultSqlExpressionCalculator newCalculator() {
    MySQLSqlDialect dialect = new MySQLSqlDialect();
    dialect.setIdentifierQuoteString("`");
    return new DefaultSqlExpressionCalculator(dialect);
  }

  @Test
  void shouldRenderSqlWithNamedParameters() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      {
        "username": {"_eq": "john_doe"},
        "age": {"_gte": 18}
      }
      """;

    SqlClauseResult result = calculator.calculate(filter, Map.of());

    assertEquals("(`username` = :username_0 AND `age` >= :age_1)", result.sqlClause());
    assertEquals("john_doe", result.args().get("username_0"));
    assertEquals(18, result.args().get("age_1"));
  }

  @Test
  void shouldRenderSqlWithNestedLogicalOperators() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      {
        "_or": [
          {"status": {"_eq": "ACTIVE"}},
          {"status": {"_eq": "PENDING"}},
          {
            "_and": [
              {"age": {"_between": [18, 30]}},
              {"name": {"_contains": "张"}}
            ]
          }
        ]
      }
      """;

    SqlClauseResult result = calculator.calculate(filter, Map.of());

    assertEquals("(`status` = :status_0 OR `status` = :status_1 OR (`age` BETWEEN :age_start_2 AND :age_end_3 AND `name` LIKE :name_4))", result.sqlClause());
    assertEquals(List.of("ACTIVE", "PENDING"), List.of(result.args().get("status_0"), result.args().get("status_1")));
    assertEquals(18, result.args().get("age_start_2"));
    assertEquals(30, result.args().get("age_end_3"));
    assertEquals("%张%", result.args().get("name_4"));
  }

  @Test
  void shouldSupportImplicitEqSyntax() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      {
        "username": "john_doe",
        "age": 20
      }
      """;

    SqlClauseResult result = calculator.calculate(filter, Map.of());

    assertEquals("(`username` = :username_0 AND `age` = :age_1)", result.sqlClause());
    assertEquals("john_doe", result.args().get("username_0"));
    assertEquals(20, result.args().get("age_1"));
  }

  @Test
  void shouldInlineValuesForIncludeValue() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      {
        "price": {"_between": [100, 200]},
        "remark": {"_not_contains": "测试"}
      }
      """;

    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("(`price` BETWEEN 100 AND 200 AND `remark` NOT LIKE '%测试%')", inline);
  }

  @Test
  void shouldRejectNullExpression() {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    assertThrows(ExpressionCalculatorException.class, () -> calculator.calculate(null, Map.of()));
    assertThrows(ExpressionCalculatorException.class, () -> calculator.calculateIncludeValue(null));
  }

  @Test
  void shouldRenderJsonPathWithMySqlDialect() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      { "metadata.color": { "_eq": "red" } }
      """;

    // 验证 inline 模式的 SQL 输出
    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("JSON_EXTRACT(`metadata`, '$.color') = 'red'", inline);

    // 验证 prepared 模式的参数绑定
    SqlClauseResult result = calculator.calculate(filter, Map.of());
    assertEquals(1, result.args().size());
    assertEquals("red", result.args().values().iterator().next());
  }

  @Test
  void shouldRenderMultiLevelJsonPath() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      { "config.database.host": { "_eq": "localhost" } }
      """;

    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("JSON_EXTRACT(`config`, '$.database.host') = 'localhost'", inline);
  }

  @Test
  void shouldRenderMixedPlainAndJsonPathFields() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      {
        "name": { "_eq": "test" },
        "metadata.color": { "_eq": "red" }
      }
      """;

    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("(`name` = 'test' AND JSON_EXTRACT(`metadata`, '$.color') = 'red')", inline);
  }

  @Test
  void shouldRenderJsonPathWithPostgresDialect() throws ExpressionCalculatorException {
    PostgreSQLSqlDialect dialect = new PostgreSQLSqlDialect();
    dialect.setIdentifierQuoteString("\"");
    DefaultSqlExpressionCalculator calculator = new DefaultSqlExpressionCalculator(dialect);
    String filter = """
      { "metadata.color": { "_eq": "red" } }
      """;

    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("jsonb_extract_path_text(\"metadata\"::jsonb,'color') = 'red'", inline);
  }

  @Test
  void shouldRenderJsonPathWithSqlServerDialect() throws ExpressionCalculatorException {
    SQLServerSqlDialect dialect = new SQLServerSqlDialect();
    dialect.setIdentifierQuoteString("");
    DefaultSqlExpressionCalculator calculator = new DefaultSqlExpressionCalculator(dialect);
    String filter = """
      { "metadata.color": { "_eq": "red" } }
      """;

    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("JSON_VALUE(metadata, '$.color') = 'red'", inline);
  }

  // ==================== Schema 感知模式测试 ====================

  /**
   * 当模型解析器存在时，第一段是已知模型且第二段不是 JSON 列 → 渲染为 table.column
   */
  @Test
  void shouldRenderTableColumnWhenModelResolverPresent() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    // studentDetail 模型的 JSON 列集合为空，表示 description 不是 JSON 列
    Function<String, Set<String>> modelResolver = name -> {
      if ("studentDetail".equals(name)) {
        return Set.of(); // 无 JSON 列
      }
      return null; // 未知模型
    };
    String filter = """
      { "studentDetail.description": { "_eq": "some text" } }
      """;

    String inline = calculator.calculateIncludeValue(filter, modelResolver);
    assertEquals("`studentDetail`.`description` = 'some text'", inline);
  }

  /**
   * 当模型解析器存在时，第一段是已知模型且第二段是 JSON 列 → 渲染为 JSON 提取
   */
  @Test
  void shouldRenderJsonExtractWhenFieldIsJsonColumn() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    // 主模型的 metadata 字段是 JSON 类型
    Function<String, Set<String>> modelResolver = name -> {
      if ("Student".equals(name)) {
        return Set.of("metadata", "config"); // metadata 和 config 是 JSON 列
      }
      return null;
    };
    String filter = """
      { "metadata.color": { "_eq": "red" } }
      """;

    String inline = calculator.calculateIncludeValue(filter, modelResolver);
    assertEquals("JSON_EXTRACT(`metadata`, '$.color') = 'red'", inline);
  }

  /**
   * 当模型解析器存在时，第一段不是已知模型 → 向后兼容，视为 JSON 路径
   */
  @Test
  void shouldFallbackToJsonPathWhenModelUnknown() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    Function<String, Set<String>> modelResolver = name -> null; // 所有名称都未知
    String filter = """
      { "metadata.color": { "_eq": "red" } }
      """;

    String inline = calculator.calculateIncludeValue(filter, modelResolver);
    assertEquals("JSON_EXTRACT(`metadata`, '$.color') = 'red'", inline);
  }

  /**
   * Schema 感知模式下同时包含 JSON 查询和关联查询
   */
  @Test
  void shouldDistinguishJsonAndAssociationInSameQuery() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    // 主模型 Student 有 metadata JSON 列，studentDetail 模型无 JSON 列
    Function<String, Set<String>> modelResolver = name -> {
      if ("Student".equals(name)) {
        return Set.of("metadata");
      }
      if ("studentDetail".equals(name)) {
        return Set.of();
      }
      return null;
    };
    String filter = """
      {
        "metadata.color": { "_eq": "red" },
        "studentDetail.description": { "_eq": "good" }
      }
      """;

    String inline = calculator.calculateIncludeValue(filter, modelResolver);
    assertEquals("(JSON_EXTRACT(`metadata`, '$.color') = 'red' AND `studentDetail`.`description` = 'good')", inline);
  }

  /**
   * 无模型解析器时保持向后兼容行为
   */
  @Test
  void shouldMaintainBackwardCompatibilityWithoutModelResolver() throws ExpressionCalculatorException {
    DefaultSqlExpressionCalculator calculator = newCalculator();
    String filter = """
      { "metadata.color": { "_eq": "red" } }
      """;

    // 不带 modelResolver 的调用应与原来行为一致
    String inline = calculator.calculateIncludeValue(filter);
    assertEquals("JSON_EXTRACT(`metadata`, '$.color') = 'red'", inline);
  }
}

