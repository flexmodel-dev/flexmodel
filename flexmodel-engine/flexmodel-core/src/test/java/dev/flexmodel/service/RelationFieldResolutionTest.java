package dev.flexmodel.service;

import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.ModelRefField;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.AbstractSessionContext;
import dev.flexmodel.type.TypeHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一目标模型存在多个关联字段时的 join 消歧测试
 */
class RelationFieldResolutionTest {

  @Test
  void aliasResolvesRelationField() {
    EntityDefinition entity = classesWith(
      relation("activeStudents", "classId", Map.of("activeStudents.status", Map.of("_eq", "ACTIVE"))),
      relation("allStudents", "classId", null)
    );

    Query.Join join = new Query.Join().setFrom("Student").setAs("activeStudents");

    Optional<ModelRefField> resolved = new TestService().findRelationField(entity, join);
    assertTrue(resolved.isPresent());
    assertEquals("activeStudents", resolved.get().getName());
  }

  @Test
  void equivalentRelationsResolveWithoutAlias() {
    EntityDefinition entity = classesWith(
      relation("activeStudents", "classId", null),
      relation("allStudents", "classId", null)
    );

    Query.Join join = new Query.Join().setFrom("Student");
    assertEquals("Student", join.getAs());

    Optional<ModelRefField> resolved = new TestService().findRelationField(entity, join);
    assertTrue(resolved.isPresent());
    assertEquals("activeStudents", resolved.get().getName());
  }

  @Test
  void keyFieldsDisambiguateRelations() {
    EntityDefinition entity = classesWith(
      relation("activeStudents", "classId", null),
      relation("monitor", "monitorId", null)
    );

    Query.Join join = new Query.Join().setFrom("Student").setLocalField("id").setForeignField("monitorId");

    Optional<ModelRefField> resolved = new TestService().findRelationField(entity, join);
    assertTrue(resolved.isPresent());
    assertEquals("monitor", resolved.get().getName());
  }

  @Test
  void ambiguousRelationsRejectedWithoutAlias() {
    EntityDefinition entity = classesWith(
      relation("activeStudents", "classId", Map.of("activeStudents.status", Map.of("_eq", "ACTIVE"))),
      relation("monitor", "monitorId", null)
    );

    Query.Join join = new Query.Join().setFrom("Student");

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
      () -> new TestService().findRelationField(entity, join));
    assertTrue(error.getMessage().contains("activeStudents"));
    assertTrue(error.getMessage().contains("monitor"));
  }

  private static EntityDefinition classesWith(ModelRefField... relationFields) {
    EntityDefinition entity = new EntityDefinition("Classes");
    for (ModelRefField relationField : relationFields) {
      entity.addField(relationField);
    }
    return entity;
  }

  private static ModelRefField relation(String name, String foreignField, Map<String, Object> filter) {
    ModelRefField relationField = new ModelRefField(name);
    relationField.setFrom("Student");
    relationField.setLocalField("id");
    relationField.setForeignField(foreignField);
    relationField.setMultiple(true);
    relationField.setFilter(filter);
    return relationField;
  }

  private static final class TestService extends BaseService {

    TestService() {
      super(new TestSessionContext());
    }
  }

  private static final class TestSessionContext extends AbstractSessionContext {

    TestSessionContext() {
      super("test-schema", null, null);
    }

    @Override
    public Map<String, ? extends TypeHandler<?>> getTypeHandlerMap() {
      return Map.of();
    }
  }
}
