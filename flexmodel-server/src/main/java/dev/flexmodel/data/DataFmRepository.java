package dev.flexmodel.data;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.StringUtils;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.query.DSLQueryBuilder;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
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

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<Map<String, Object>> findRecords(String projectId,
                                               String datasourceName,
                                               String modelName,
                                               Integer page,
                                               Integer size,
                                               String filter,
                                               String sortString,
                                               List<String> expand) {


    try (Session session = sessionFactory.createSession(datasourceName)) {

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
    try (Session session = sessionFactory.createSession(datasourceName)) {

      DSLQueryBuilder queryBuilder = session.dsl()
        .selectFrom(modelName);

      if (!StringUtils.isBlank(filter)) {
        queryBuilder.where(filter);
      }

      return queryBuilder.count();
    }
  }

  @Override
  public Map<String, Object> findOneRecord(String projectId, String datasourceName, String modelName, Object id, List<String> expand) {
    try (Session session = sessionFactory.createSession(datasourceName)) {

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
    try (Session session = sessionFactory.createSession(datasourceName)) {
      session.dsl()
        .insertInto(modelName)
        .values(data)
        .execute();
      return data;
    }
  }

  @Override
  public Map<String, Object> updateRecord(String projectId, String datasourceName, String modelName, Object id, Map<String, Object> data) {
    try (Session session = sessionFactory.createSession(datasourceName)) {
      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      Optional<TypedField<?, ?>> idField = entity.findIdField();

      session.dsl()
        .update(modelName)
        .values(data)
        .where(Expressions.field(idField.orElseThrow().getName()).eq(id))
        .execute();

      return data;
    }
  }

  @Override
  public void deleteRecord(String projectId, String datasourceName, String modelName, Object id) {
    try (Session session = sessionFactory.createSession(datasourceName)) {

      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      Optional<TypedField<?, ?>> idField = entity.findIdField();

      session.dsl()
        .deleteFrom(modelName)
        .where(Expressions.field(idField.orElseThrow().getName()).eq(id))
        .execute();
    }
  }

  @Override
  public List<Map<String, Object>> createRecords(String projectId, String datasourceName, String modelName, List<Map<String, Object>> data) {
    try (Session session = sessionFactory.createSession(datasourceName)) {
      session.data().insertAll(modelName, data);
      return data;
    }
  }

  @Override
  public List<Map<String, Object>> updateRecords(String projectId, String datasourceName, String modelName, List<Map<String, Object>> data) {
    try (Session session = sessionFactory.createSession(datasourceName)) {
      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      String idFieldName = entity.findIdField().orElseThrow().getName();
      for (Map<String, Object> record : data) {
        Object id = record.get(idFieldName);
        if (id == null) {
          throw new IllegalArgumentException("批量更新记录缺少 id 字段: " + modelName);
        }
        session.data().updateById(modelName, record, id);
      }
      return data;
    }
  }

  @Override
  public long deleteRecords(String projectId, String datasourceName, String modelName, List<Object> ids) {
    try (Session session = sessionFactory.createSession(datasourceName)) {
      return session.dsl().deleteFrom(modelName).where(Expressions.field("id").in(ids)).execute();
    }
  }

}
