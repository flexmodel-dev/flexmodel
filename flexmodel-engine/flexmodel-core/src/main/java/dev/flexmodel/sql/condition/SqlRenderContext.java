package dev.flexmodel.sql.condition;

import dev.flexmodel.sql.dialect.SqlDialect;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * SQL 渲染上下文。
 */
public class SqlRenderContext {

  private final String identifierQuoteString;
  private PlaceholderHandler placeholderHandler;
  private final SqlDialect sqlDialect;

  public SqlRenderContext(String identifierQuoteString, PlaceholderHandler placeholderHandler) {
    this(identifierQuoteString, placeholderHandler, null);
  }

  public SqlRenderContext(String identifierQuoteString, PlaceholderHandler placeholderHandler, SqlDialect sqlDialect) {
    this.identifierQuoteString = identifierQuoteString == null ? "" : identifierQuoteString;
    this.placeholderHandler = placeholderHandler;
    this.sqlDialect = sqlDialect;
  }

  public String quoteIdentifier(String identifier) {
    return identifierQuoteString + identifier + identifierQuoteString;
  }

  /**
   * 将字段路径格式化为 SQL 表达式。
   * <ul>
   *   <li>普通字段（不含点号）：加引号，如 {@code `username`}</li>
   *   <li>JSON 路径（含点号）：使用方言的 JSON 提取函数，如 {@code JSON_EXTRACT(`metadata`, '$.color')}</li>
   * </ul>
   */
  public String formatFieldPath(String fieldPath) {
    if (!fieldPath.contains(".")) {
      return quoteIdentifier(fieldPath);
    }
    // 点号分割：第一段是列名，剩余段是 JSON 路径
    int dotIndex = fieldPath.indexOf('.');
    String column = quoteIdentifier(fieldPath.substring(0, dotIndex));
    String jsonPath = "$." + fieldPath.substring(dotIndex + 1);
    if (sqlDialect != null) {
      return sqlDialect.jsonExtract(column, jsonPath);
    }
    return "JSON_EXTRACT(" + column + ", '" + jsonPath + "')";
  }

  public PlaceholderHandler getPlaceholderHandler() {
    return placeholderHandler;
  }

  public void setPlaceholderHandler(PlaceholderHandler placeholderHandler) {
    this.placeholderHandler = placeholderHandler;
  }
}

