package dev.flexmodel.data;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.StringUtils;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.query.DSLQueryBuilder;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class DataFmRepository implements DataRepository {
  private Session open(String schema) {
    return serverSessions.open(schema);
  }


  @Inject
  dev.flexmodel.common.persistence.ServerSessions serverSessions;

  @Override
  public List<Map<String, Object>> findRecords(String projectId,
                                               String datasourceName,
                                               String modelName,
                                               Integer page,
                                               Integer size,
                                               String filter,
                                               String sortString,
                                               List<String> expand) {


    try (Session session = open(datasourceName)) {

      DSLQueryBuilder queryBuilder = session.dsl()
        .selectFrom(modelName);

      if (!StringUtils.isBlank(filter)) {
        queryBuilder.where(filter);
      }

      if (size != null && page != null) {
        queryBuilder.page(page, size);
      }

      if (!StringUtils.isBlank(sortString)) {
        try {
          List<Query.OrderBy.Sort> sorts = JsonUtils.parseToList(sortString, Query.OrderBy.Sort.class);
          Query.OrderBy sort = new Query.OrderBy();
          sort.getSorts().addAll(sorts);
          queryBuilder.orderBy(sort);
        } catch (Exception e) {
          log.error("Invalid sort string: {}", sortString, e);
        }
      }
      if (expand != null && !expand.isEmpty()) {
        queryBuilder.expand(expand);
      }
      return queryBuilder.execute();
    }
  }

  @Override
  public long countRecords(String projectId, String datasourceName, String modelName, String filter) {
    try (Session session = open(datasourceName)) {

      DSLQueryBuilder queryBuilder = session.dsl()
        .selectFrom(modelName);

      if (!StringUtils.isBlank(filter)) {
        queryBuilder.where(filter);
      }

      return queryBuilder.count();
    }
  }

  @Override
  public List<Map<String, Object>> aggregateRecords(
    String projectId,
    String datasourceName,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    int limit
  ) {
    return aggregateRecords(
      projectId, datasourceName, modelName, dimensionField, measureField, aggregation, null, limit);
  }

  @Override
  public List<Map<String, Object>> aggregateRecords(
    String projectId,
    String datasourceName,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    String filter,
    int limit
  ) {
    try (Session session = open(datasourceName)) {
      boolean grouped = !StringUtils.isBlank(dimensionField);
      Query.Builder builder = Query.Builder.create()
        .select(select -> {
          if (grouped) {
            select.field("dimension", dimensionField);
          }
          switch (aggregation) {
            case "count" -> select.field("value", Query.count(Query.field("id")));
            case "sum" -> select.field("value", Query.sum(Query.field(measureField)));
            case "avg" -> select.field("value", Query.avg(Query.field(measureField)));
            default -> throw new IllegalArgumentException("不支持的聚合方式: " + aggregation);
          }
        });
      if (!StringUtils.isBlank(filter)) {
        builder.where(filter);
      }
      if (grouped) {
        builder
          .groupBy(groupBy -> groupBy.field(dimensionField))
          .orderBy(orderBy -> orderBy.asc("dimension"))
          .page(1, limit);
      } else {
        builder.page(1, 1);
      }
      return session.data().findAll(modelName, builder.build());
    }
  }

  @Override
  public Map<String, Object> findOneRecord(String projectId, String datasourceName, String modelName, Object id, List<String> expand) {
    try (Session session = open(datasourceName)) {

      DSLQueryBuilder queryBuilder = session.dsl()
        .selectFrom(modelName);

      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      Optional<TypedField<?, ?>> idField = entity.findIdField();

      DSLQueryBuilder qb = queryBuilder.where(Expressions.field(idField.orElseThrow().getName()).eq(id));
      if (expand != null && !expand.isEmpty()) {
        qb.expand(expand);
      }
      return qb.executeOne();
    }
  }

  @Override
  public Map<String, Object> createRecord(String projectId, String datasourceName, String modelName, Map<String, Object> data) {
    try (Session session = open(datasourceName)) {
      session.dsl()
        .insertInto(modelName)
        .values(data)
        .execute();
      return data;
    }
  }

  @Override
  public Map<String, Object> updateRecord(String projectId, String datasourceName, String modelName, Object id, Map<String, Object> data) {
    try (Session session = open(datasourceName)) {
      session.dsl()
        .update(modelName)
        .values(data)
        .whereId(id)
        .execute();

      return data;
    }
  }

  @Override
  public void deleteRecord(String projectId, String datasourceName, String modelName, Object id) {
    try (Session session = open(datasourceName)) {

      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      Optional<TypedField<?, ?>> idField = entity.findIdField();

      session.dsl()
        .deleteFrom(modelName)
        .whereId(id)
        .execute();
    }
  }

  @Override
  public List<Map<String, Object>> createRecords(String projectId, String datasourceName, String modelName, List<Map<String, Object>> data) {
    try (Session session = open(datasourceName)) {
      session.data().insertAll(modelName, data);
      return data;
    }
  }

  @Override
  public List<Map<String, Object>> updateRecords(String projectId, String datasourceName, String modelName, List<Map<String, Object>> data) {
    try (Session session = open(datasourceName)) {
      session.startTransaction();
      try {
        EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
        String idFieldName = entity.findIdField().orElseThrow().getName();
        for (Map<String, Object> record : data) {
          Object id = record.get(idFieldName);
          if (id == null) {
            throw new IllegalArgumentException("批量更新记录缺少 id 字段: " + modelName);
          }
          session.data().updateById(modelName, record, id);
        }
        session.commit();
        return data;
      } catch (RuntimeException | Error error) {
        session.rollback();
        throw error;
      }
    }
  }

  @Override
  public long deleteRecords(String projectId, String datasourceName, String modelName, List<Object> ids) {
    try (Session session = open(datasourceName)) {
      return session.dsl().deleteFrom(modelName).where(Expressions.field("id").in(ids)).execute();
    }
  }

}
