package dev.flexmodel.condition;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.model.field.RelationFilterSupport;
import dev.flexmodel.mongodb.condition.MongoConditionRenderer;
import dev.flexmodel.sql.condition.InlinePlaceholderHandler;
import dev.flexmodel.sql.condition.SqlConditionRenderer;
import dev.flexmodel.sql.condition.SqlRenderContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationFilterTest {

  @Test
  void parserCreatesFieldReferenceCondition() {
    String expression = JsonUtils.toJsonString(Map.of(
      "activeStudents.classId",
      Map.of("_eq", Map.of("_field", "id"))
    ));
    ConditionNode condition = new ConditionParser().parse(expression);
    FieldConditionNode field = (FieldConditionNode) condition;
    assertEquals(new FieldReference("id"), field.getValue());
  }

  @Test
  void sqlRendererComparesTargetAndSourceColumns() {
    Map<String, Object> filter = Map.of(
      "activeStudents.classId",
      Map.of("_eq", Map.of("_field", "id"))
    );
    Map<String, Object> normalized =
      RelationFilterSupport.replaceAliases(filter, "f_classes", "activeStudents", "f_student");
    String expression = JsonUtils.toJsonString(normalized);
    ConditionNode condition = new ConditionParser().parse(expression);
    SqlRenderContext context = new SqlRenderContext("`", new InlinePlaceholderHandler());
    context.setModelResolver(name -> "f_student".equals(name) || "f_classes".equals(name) ? Set.of() : null);
    String sql = SqlConditionRenderer.render(condition, context);
    assertEquals("`f_student`.`classId` = `f_classes`.`id`", sql);
  }

  @Test
  void mongoRendererUsesSourceVariableAndStripsRelationPrefix() {
    String expression = JsonUtils.toJsonString(Map.of(
      "activeStudents.classId",
      Map.of("_eq", Map.of("_field", "id"))
    ));
    ConditionNode condition = new ConditionParser().parse(expression);
    String mongo = MongoConditionRenderer.render(condition, "activeStudents");
    assertTrue(mongo.contains("$expr"));
    assertTrue(mongo.contains("$eq"));
    assertTrue(mongo.contains("$classId"));
    assertTrue(mongo.contains("$$id"));
  }

  @Test
  void relationFilterSupportResolvesSourceValues() {
    Map<String, Object> filter = Map.of(
      "_and",
      List.of(
        Map.of("activeStudents.classId", Map.of("_eq", Map.of("_field", "id"))),
        Map.of("activeStudents.status", Map.of("_eq", "ACTIVE"))
      )
    );
    Map<String, Object> resolved =
      RelationFilterSupport.resolveTargetFilter(filter, Map.of("id", 42L), "activeStudents");
    assertEquals(
      Map.of(
        "_and",
        List.of(
          Map.of("classId", Map.of("_eq", 42L)),
          Map.of("status", Map.of("_eq", "ACTIVE"))
        )
      ),
      resolved
    );
  }

  @Test
  void relationFilterSupportResolvesReversedSourceCondition() {
    Map<String, Object> filter = Map.of(
      "id",
      Map.of("_eq", Map.of("_field", "activeStudents.classId"))
    );
    Map<String, Object> resolved =
      RelationFilterSupport.resolveTargetFilter(filter, Map.of("id", 42L), "activeStudents");
    assertEquals(Map.of("classId", Map.of("_eq", 42L)), resolved);
  }

  @Test
  void mongoRendererKeepsManualJoinFilterOnTargetDocument() {
    ConditionNode condition = new ConditionParser().parse("{\"teacher_id\":{\"_ne\":999}}");
    assertEquals("{\"teacher_id\":{\"$ne\":999}}", MongoConditionRenderer.render(condition, null));
  }

  @Test
  void mongoRendererUsesDocumentFieldReferenceOutsideRelationContext() {
    ConditionNode condition = new ConditionParser().parse("{\"a\":{\"_eq\":{\"_field\":\"b\"}}}");
    assertEquals("{\"$expr\":{\"$eq\":[\"$a\",\"$b\"]}}", MongoConditionRenderer.render(condition, null));
  }

  @Test
  void mongoRendererCreatesValidSourceBetweenExpression() {
    ConditionNode condition = new ConditionParser().parse("{\"id\":{\"_between\":[1,5]}}");
    assertEquals(
      "{\"$expr\":{\"$and\":[{\"$gte\":[\"$$id\",1]},{\"$lte\":[\"$$id\",5]}]}}",
      MongoConditionRenderer.render(condition, "activeStudents")
    );
  }

  @Test
  void replaceAliasesIsIdempotent() {
    Map<String, Object> filter = Map.of(
      "activeStudents.classId",
      Map.of("_eq", Map.of("_field", "id"))
    );
    Map<String, Object> first = RelationFilterSupport.replaceAliases(
      filter, "f_classes", "activeStudents", "f_student");
    Map<String, Object> second = RelationFilterSupport.replaceAliases(
      first, "f_classes", "activeStudents", "f_student");
    assertEquals(first, second);
  }

  @Test
  void sqlRendererSupportsFieldReferencesInsideCollections() {
    Map<String, Object> normalized = RelationFilterSupport.replaceAliases(
      Map.of("activeStudents.classId", Map.of("_in", List.of(Map.of("_field", "id")))),
      "f_classes",
      "activeStudents",
      "f_student"
    );
    ConditionNode condition = new ConditionParser().parse(JsonUtils.toJsonString(normalized));
    SqlRenderContext context = new SqlRenderContext("`", new InlinePlaceholderHandler());
    context.setModelResolver(name -> "f_student".equals(name) || "f_classes".equals(name) ? Set.of() : null);
    assertEquals("`f_student`.`classId` IN (`f_classes`.`id`)", SqlConditionRenderer.render(condition, context));
  }

  @Test
  void relationFilterAllowsExplicitNullSourceValue() {
    Map<String, Object> sourceData = new java.util.HashMap<>();
    sourceData.put("id", null);
    Map<String, Object> resolved = RelationFilterSupport.resolveTargetFilter(
      Map.of("activeStudents.classId", Map.of("_eq", Map.of("_field", "id"))),
      sourceData,
      "activeStudents"
    );
    Map<String, Object> expectedCondition = new java.util.HashMap<>();
    expectedCondition.put("_eq", null);
    Map<String, Object> expected = new java.util.HashMap<>();
    expected.put("classId", expectedCondition);
    assertEquals(expected, resolved);
  }

  @Test
  void relationFilterRejectsMissingSourceValue() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> RelationFilterSupport.resolveTargetFilter(
        Map.of("activeStudents.classId", Map.of("_eq", Map.of("_field", "id"))),
        Map.of(),
        "activeStudents"
      ));
    assertTrue(exception.getMessage().contains("Source field id is missing"));
  }

  @Test
  void conditionFilterRequiresSourceTargetComparison() {
    Map<String, Object> filter = Map.of(
      "activeStudents.status",
      Map.of("_eq", "ACTIVE")
    );
    assertFalse(RelationFilterSupport.hasSourceTargetComparison(filter, "activeStudents"));
    assertFalse(RelationFilterSupport.isSupportedRelationFilter(
      Map.of("id", Map.of("_eq", "ACTIVE")), "activeStudents"));
    assertTrue(RelationFilterSupport.isSupportedRelationFilter(
      Map.of("activeStudents.status", Map.of("_eq", "ACTIVE")), "activeStudents"));
  }
}
