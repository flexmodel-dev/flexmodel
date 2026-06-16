package dev.flexmodel.sql;

import dev.flexmodel.ExpressionCalculatorException;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.ModelDefinition;
import dev.flexmodel.model.field.RelationField;
import dev.flexmodel.model.field.ScalarType;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.query.Query;
import dev.flexmodel.service.BaseService;
import dev.flexmodel.sql.dialect.SqlDialect;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.flexmodel.query.Query.Join.JoinType.INNER_JOIN;
import static dev.flexmodel.query.Query.Join.JoinType.LEFT_JOIN;

/**
 * @author cjbi
 */
public class SqlStatementBuilder extends BaseService {

  protected final SqlContext sqlContext;

  public SqlStatementBuilder(SqlContext sqlContext) {
    super(sqlContext);
    this.sqlContext = sqlContext;
  }

  public Pair<String, Map<String, Object>> toQuerySqlWithPrepared(String modelName, Query query) {
    return buildQuerySql(modelName, query);
  }

  private Pair<String, Map<String, Object>> buildQuerySql(String modelName, Query query) {
    validateQuery(modelName, query);
    Map<String, Object> params = new HashMap<>();
    ModelDefinition model = (ModelDefinition) sqlContext.getModelDefinition(modelName);
    if (model == null) {
      throw new IllegalArgumentException("modelName " + modelName + " not found");
    }
    Function<String, Set<String>> modelResolver = createModelResolver(modelName);
    StringBuilder sqlBuilder = new StringBuilder("\nselect ");
    Map<String, String> projectionMap = new HashMap<>();
    appendProjection(modelName, query, model, projectionMap, sqlBuilder);
    appendFromClause(modelName, sqlBuilder);
    appendJoins(modelName, query, model, sqlBuilder, params, true, modelResolver);
    appendWhereClause(query, sqlBuilder, params, true, modelResolver);
    appendGroupByClause(query, projectionMap, sqlBuilder);
    appendOrderByClause(query, sqlBuilder);
    appendLimitClause(query, sqlBuilder);
    appendForUpdateClause(query, sqlBuilder);
    return Pair.of(sqlBuilder.toString(), params);
  }

  private void appendLimitClause(Query query, StringBuilder sqlBuilder) {
    SqlDialect sqlDialect = sqlContext.getSqlDialect();
    if (query.getPage() != null) {
      String limitString = sqlDialect.getLimitString(
        sqlBuilder.toString(),
        Objects.toString(query.getPage().getOffset()),
        Objects.toString(query.getPage().getPageSize())
      );
      sqlBuilder.setLength(0);
      sqlBuilder.append(limitString);
    }
  }

  private void appendForUpdateClause(Query query, StringBuilder sqlBuilder) {
    if (query.isForUpdate() && sqlContext.getSqlDialect().supportsForUpdate()) {
      sqlBuilder.append("\nfor update");
    }
  }

  private void appendOrderByClause(Query query, StringBuilder sqlBuilder) {
    Query.OrderBy sort = query.getSort();
    if (sort != null) {
      sqlBuilder.append("\norder by ");
      StringJoiner sortColumns = new StringJoiner(", ");
      for (Query.OrderBy.Sort order : sort.getSorts()) {
        sortColumns.add(toFullColumnQuoteString(order.getField().getAliasName(), order.getField().getFieldName()) + " " + order.getDirection().name().toLowerCase());
      }
      sqlBuilder.append(sortColumns);
    }
  }

  private void appendGroupByClause(Query query, Map<String, String> projectionMap, StringBuilder sqlBuilder) {
    if (query.getGroupBy() != null) {
      SqlDialect sqlDialect = sqlContext.getSqlDialect();
      sqlBuilder.append("\ngroup by ");
      StringJoiner groupByColumns = new StringJoiner(", ");
      for (Query.QueryField field : query.getGroupBy().getFields()) {
        groupByColumns.add(sqlDialect.supportsGroupByColumnAlias() ? toFullColumnQuoteString(field.getAliasName(), field.getFieldName()) : projectionMap.getOrDefault(field.getFieldName(), toFullColumnQuoteString(field.getAliasName(), field.getFieldName())));
      }
      sqlBuilder.append(groupByColumns);
    }
  }

  private void appendWhereClause(Query query, StringBuilder sqlBuilder, Map<String, Object> params, boolean prepared, Function<String, Set<String>> modelResolver) {
    if (query.getFilter() != null) {
      if (prepared) {
        SqlClauseResult sqlClauseResult = toSqlWhereClauseWithPrepared(query.getFilter(), modelResolver);
        sqlBuilder.append("\nwhere (").append(sqlClauseResult.sqlClause()).append(")");
        params.putAll(sqlClauseResult.args());
      } else {
        sqlBuilder.append("\nwhere (").append(toSqlWhereClause(query.getFilter(), modelResolver)).append(")");
      }
    }
  }

  private void appendJoins(String modelName, Query query, ModelDefinition model, StringBuilder sqlBuilder, Map<String, Object> params, boolean prepared, Function<String, Set<String>> modelResolver) {
    Query.Joins joins = query.getJoins();
    if (joins != null) {
      StringBuilder joinCause = new StringBuilder();
      for (Query.Join joiner : joins.getJoins()) {
        String joinTableName = toPhysicalTableNameQuoteString(joiner.getFrom(), joiner.getAs());
        if (joiner.getJoinType() == LEFT_JOIN) {
          joinCause.append("\nleft join ");
        }
        if (joiner.getJoinType() == INNER_JOIN) {
          joinCause.append("\ninner join ");
        }
        String localField = joiner.getLocalField();
        String foreignField = joiner.getForeignField();
        RelationField relationField;
        if (model instanceof EntityDefinition entity && (relationField = entity.findRelationByModelName(joiner.getFrom()).orElse(null)) != null) {
          foreignField = relationField.getForeignField();
          joinCause.append(joinTableName).append(" \n on \n").append(toFullColumnQuoteString(modelName, localField)).append("=").append(toFullColumnQuoteString(joiner.getAs(), foreignField));

        } else {
          joinCause.append(joinTableName).append(" \n on \n").append(toFullColumnQuoteString(modelName, localField)).append("=").append(toFullColumnQuoteString(joiner.getAs(), foreignField));
        }
        StringBuilder joinCondition = new StringBuilder();
        if (joiner.getFilter() != null) {
          // 关联表的过滤条件使用关联表的模型解析器
          Function<String, Set<String>> joinModelResolver = createModelResolver(joiner.getAs());
          if (prepared) {
            SqlClauseResult leftSqlWhere = toSqlWhereClauseWithPrepared(joiner.getFilter(), joinModelResolver);
            joinCondition.append(" and ").append(leftSqlWhere.sqlClause());
            params.putAll(leftSqlWhere.args());
          } else {
            joinCondition.append(" and ").append(toSqlWhereClause(joiner.getFilter(), joinModelResolver));
          }
          joinCause.append(joinCondition);
        }
      }
      sqlBuilder.append(joinCause);
    }
  }

  private void appendFromClause(String modelName, StringBuilder sqlBuilder) {
    String physicalFromTableName = toPhysicalTableNameQuoteString(modelName, modelName);
    sqlBuilder.append("\nfrom ").append(physicalFromTableName);
  }

  private void appendProjection(String modelName, Query query, ModelDefinition model, Map<String, String> projectionMap, StringBuilder sqlBuilder) {
    SqlDialect sqlDialect = sqlContext.getSqlDialect();
    Query.Projection projection = query.getProjection();
    Map<String, RelationField> relationFields = findRelationFields(model, query);
    StringJoiner columns = new StringJoiner(", ");
    if (projection != null && !projection.getFields().isEmpty()) {
      projection.getFields().forEach((key, value) -> {
        if (!relationFields.containsKey(key)) {
          String sqlCall = toSqlCall(value);
          projectionMap.put(key, sqlCall);
          columns.add("\n " + sqlCall + " " + sqlDialect.quoteIdentifier(key));
        }
      });
    } else {
      model.getFields().forEach(field -> {
        if (!relationFields.containsKey(field.getName())) {
          columns.add("\n " + toFullColumnQuoteString(modelName, field.getName()) + " " + sqlDialect.quoteIdentifier(field.getName()));
          projectionMap.put(field.getName(), field.getName());
        }
      });
    }
    sqlBuilder.append(columns);
  }

  private String toSqlCall(Query.QueryCall queryCall) {
    SqlDialect sqlDialect = sqlContext.getSqlDialect();
    if (queryCall instanceof Query.QueryField field) {
      return toFullColumnQuoteString(field.getAliasName(), field.getFieldName());
    } else if (queryCall instanceof Query.QueryFunc func) {
      List<String> arguments = new ArrayList<>();
      for (Object arg : func.getArgs()) {
        if (arg instanceof Query.QueryCall callArg) {
          arguments.add(toSqlCall(callArg));
        } else {
          arguments.add(arg instanceof String str ? "'" + str + "'" : arg.toString());
        }
      }
      return sqlDialect.getFunctionString(func.getOperator(), arguments.toArray(String[]::new));
    } else if (queryCall instanceof Query.QueryValue queryValue) {
      return "'" + queryValue.value() + "'";
    }
    return null;
  }

  private String toSqlWhereClause(String condition, Function<String, Set<String>> modelResolver) {
    SqlExpressionCalculator conditionCalculator = sqlContext.getConditionCalculator();
    try {
      return conditionCalculator.calculateIncludeValue(condition, modelResolver);
    } catch (ExpressionCalculatorException e) {
      throw new SqlExecutionException("Calculate sql where error: " + e.getMessage(), e);
    }
  }

  private SqlClauseResult toSqlWhereClauseWithPrepared(String condition, Function<String, Set<String>> modelResolver) {
    SqlExpressionCalculator conditionCalculator = sqlContext.getConditionCalculator();
    try {
      return conditionCalculator.calculate(condition, null, modelResolver);
    } catch (ExpressionCalculatorException e) {
      throw new SqlExecutionException("Calculate sql where error: " + e.getMessage(), e);
    }
  }

  private String toFullColumnQuoteString(String modelName, String fieldName) {
    SqlDialect sqlDialect = sqlContext.getSqlDialect();
    if (modelName == null) {
      return sqlDialect.quoteIdentifier(fieldName);
    }
    return sqlDialect.quoteIdentifier(modelName) + "." + sqlDialect.quoteIdentifier(fieldName);
  }

  private String toPhysicalTableNameQuoteString(String name, String alias) {
    SqlDialect sqlDialect = sqlContext.getSqlDialect();
    return sqlDialect.quoteIdentifier(name) + " " + sqlDialect.quoteIdentifier(alias);
  }

  /**
   * 创建模型解析器，用于判断字段路径中的第一段是否为 JSON 类型列。
   * <p>
   * 解析器接受一个名称（表名或别名），返回该模型中 JSON 类型列名集合。
   * 返回 {@code null} 表示该名称不是已知模型。
   *
   * @param primaryModelName 主模型名称
   * @return 模型解析函数
   */
  private Function<String, Set<String>> createModelResolver(String primaryModelName) {
    return name -> {
      // 优先匹配主模型
      String lookupName = name.equals(primaryModelName) ? primaryModelName : name;
      ModelDefinition modelDef = (ModelDefinition) sqlContext.getModelDefinition(lookupName);
      if (modelDef instanceof EntityDefinition entity) {
        return entity.getFields().stream()
          .filter(f -> ScalarType.JSON_TYPE.equals(f.getType()))
          .map(TypedField::getName)
          .collect(Collectors.toSet());
      }
      return null;
    };
  }

  public record Pair<T, U>(T first, U second) {

    public static <T, U> Pair<T, U> of(T first, U second) {
      return new Pair<>(first, second);
    }
  }


}
