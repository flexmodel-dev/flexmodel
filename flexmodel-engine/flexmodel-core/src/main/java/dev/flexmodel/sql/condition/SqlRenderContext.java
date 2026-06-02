package dev.flexmodel.sql.condition;

import dev.flexmodel.sql.dialect.SqlDialect;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

/**
 * SQL 渲染上下文。
 */
public class SqlRenderContext {

  private final String identifierQuoteString;
  private PlaceholderHandler placeholderHandler;
  private final SqlDialect sqlDialect;

  /**
   * 模型解析器：根据名称（表名或别名）返回该模型中 JSON 类型列名集合。
   * <p>
   * 返回 {@code null} 或空集表示该名称不是已知模型。
   */
  private Function<String, Set<String>> modelResolver;

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
   *   <li>关联字段（表.列）：第一段是已知模型名时，渲染为 {@code `table`.`column`}</li>
   *   <li>JSON 路径：第一段是已知 JSON 列时，使用方言的 JSON 提取函数，如 {@code JSON_EXTRACT(`metadata`, '$.color')}</li>
   * </ul>
   * <p>
   * 当未设置 {@code modelResolver} 时，保持向后兼容行为：含点号的路径一律视为 JSON 路径。
   */
  public String formatFieldPath(String fieldPath) {
    if (!fieldPath.contains(".")) {
      return quoteIdentifier(fieldPath);
    }
    int dotIndex = fieldPath.indexOf('.');
    String firstSegment = fieldPath.substring(0, dotIndex);
    String remaining = fieldPath.substring(dotIndex + 1);

    // Schema 感知模式：根据模型定义判断第一段语义
    if (modelResolver != null) {
      Set<String> jsonColumns = modelResolver.apply(firstSegment);
      if (jsonColumns != null) {
        // 第一段是已知模型/表名
        if (jsonColumns.contains(remaining) || (remaining.contains(".") && jsonColumns.contains(remaining.substring(0, remaining.indexOf('.'))))) {
          // 第二段（或其前缀）是 JSON 列 → JSON 提取
          return renderJsonExtract(firstSegment, remaining);
        }
        // 否则视为 table.column 引用
        return quoteIdentifier(firstSegment) + "." + quoteIdentifier(remaining);
      }
      // 第一段不是已知模型 → 向后兼容，回退为 JSON 路径
      return renderJsonExtract(firstSegment, remaining);
    }

    // 向后兼容模式：无 Schema 信息时，含点号路径一律视为 JSON 路径
    return renderJsonExtract(firstSegment, remaining);
  }

  private String renderJsonExtract(String column, String path) {
    String quotedColumn = quoteIdentifier(column);
    String jsonPath = "$." + path;
return sqlDialect.jsonExtract(quotedColumn, jsonPath);
  }

  public PlaceholderHandler getPlaceholderHandler() {
    return placeholderHandler;
  }

  public void setPlaceholderHandler(PlaceholderHandler placeholderHandler) {
    this.placeholderHandler = placeholderHandler;
  }

  public Function<String, Set<String>> getModelResolver() {
    return modelResolver;
  }

  /**
   * 设置模型解析器，用于区分 JSON 路径和关联表字段引用。
   *
   * @param modelResolver 函数：名称 → JSON 列名集合；返回 null 表示未知名称
   */
  public SqlRenderContext setModelResolver(Function<String, Set<String>> modelResolver) {
    this.modelResolver = modelResolver;
    return this;
  }
}

