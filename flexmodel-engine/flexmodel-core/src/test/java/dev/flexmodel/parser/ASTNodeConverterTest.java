package dev.flexmodel.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.EnumElement;
import dev.flexmodel.model.field.ModelRefField;
import dev.flexmodel.model.field.RelationStrategy;
import dev.flexmodel.model.field.TextField;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.parser.impl.ModelParser;
import dev.flexmodel.parser.impl.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
public class ASTNodeConverterTest {

  @Test
  void test() throws ParseException {
    InputStream is = this.getClass().getClassLoader().getResourceAsStream("sample_input.fml");
    ModelParser modelParser = new ModelParser(is);
    List<ModelParser.ASTNode> list = modelParser.CompilationUnit();
    List<SchemaObject> objectList = new ArrayList<>();
    for (ModelParser.ASTNode astNode : list) {
      objectList.add(ASTNodeConverter.toSchemaObject(astNode));
    }
    System.out.println(JsonUtils.toJsonString(objectList));
    List<ModelParser.ASTNode> astNodeList = new ArrayList<>();
    for (SchemaObject schemaObject : objectList) {
      astNodeList.add(ASTNodeConverter.fromSchemaObject(schemaObject));
    }
    System.out.println(astNodeList);
  }

  @Test
  void test2() throws IOException {
    byte[] bytes = this.getClass().getClassLoader().getResourceAsStream("sample_input.json").readAllBytes();
    List<Map<String, Object>> list = JsonUtils.parseToMapList(new String(bytes));
    List<SchemaObject> schemaObjects = JsonUtils.convertValueList(list, SchemaObject.class);
    StringBuilder sb = new StringBuilder();
    for (SchemaObject schemaObject : schemaObjects) {
      sb.append(ASTNodeConverter.fromSchemaObject(schemaObject)).append("\n");
    }
    System.out.println(sb);
  }

  @Test
  void keyRelationFilterIsStoredInModelRefField() throws ParseException {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          localField: "id",
          foreignField: "classId",
          filter: {
            "activeStudents.status": { "_eq": "ACTIVE" }
          }
        )
      }
      """;
    List<SchemaObject> models = ASTNodeConverter.parseFML(fml).getModels();
    EntityDefinition entity = (EntityDefinition) models.get(0);
    ModelRefField relation = (ModelRefField) entity.getField("activeStudents");
    assertEquals(RelationStrategy.FOREIGN_KEY, relation.getStrategy());
    assertEquals("id", relation.getLocalField());
    assertEquals("classId", relation.getForeignField());
    assertNotNull(relation.getFilter());
    assertEquals(Map.of("activeStudents.status", Map.of("_eq", "ACTIVE")), relation.getFilter());
  }

  @Test
  void textTypeIsParsedAsTextField() throws ParseException {
    String fml = """
      model Article {
        id : Long @id,
        content : Text @comment("正文"),
        summary? : Text
      }
      """;
    List<SchemaObject> models = ASTNodeConverter.parseFML(fml).getModels();
    EntityDefinition entity = (EntityDefinition) models.get(0);
    assertInstanceOf(TextField.class, entity.getField("content"));
    assertEquals("Text", entity.getField("content").getType());
    assertTrue(entity.getField("summary").isNullable());

    // 反向序列化为 FML 时保持 Text 类型
    ModelParser.Model model = ASTNodeConverter.fromSchemaEntity(entity);
    assertEquals("Text", model.fields.get(1).type);
    assertEquals("Text", model.fields.get(2).type);
  }

  @Test
  void conditionRelationIsStoredWithoutKeyFields() throws ParseException {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          filter: {
            "_and": [
              {
                "activeStudents.classId": { "_eq": { "_field": "id" } }
              },
              {
                "activeStudents.status": { "_eq": "ACTIVE" }
              }
            ]
          }
        )
      }
      """;
    List<SchemaObject> models = ASTNodeConverter.parseFML(fml).getModels();
    EntityDefinition entity = (EntityDefinition) models.get(0);
    ModelRefField relation = (ModelRefField) entity.getField("activeStudents");
    assertEquals(RelationStrategy.CONDITION, relation.getStrategy());
    assertNull(relation.getLocalField());
    assertNull(relation.getForeignField());
    assertNotNull(relation.getFilter());
  }

  @Test
  void relationFilterRejectsCascadeDelete() {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          localField: "id",
          foreignField: "classId",
          cascadeDelete: true,
          filter: {
            "activeStudents.status": { "_eq": "ACTIVE" }
          }
        )
      }
      """;
    assertThrows(IllegalArgumentException.class, () -> ASTNodeConverter.parseFML(fml));
  }

  @Test
  void relationFilterPreservesTypedScalarValues() throws ParseException {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          localField: "id",
          foreignField: "classId",
          filter: {
            "activeStudents.age": { "_gte": 18 },
            "activeStudents.enabled": { "_eq": true }
          }
        )
      }
      """;
    EntityDefinition entity = (EntityDefinition) ASTNodeConverter.parseFML(fml).getModels().getFirst();
    ModelRefField relation = (ModelRefField) entity.getField("activeStudents");
    assertEquals(18L, ((Map<?, ?>) relation.getFilter().get("activeStudents.age")).get("_gte"));
    assertEquals(Boolean.TRUE, ((Map<?, ?>) relation.getFilter().get("activeStudents.enabled")).get("_eq"));
  }

  @Test
  void relationFilterSupportsMultipleTopLevelConditions() throws ParseException {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          localField: "id",
          foreignField: "classId",
          filter: {
            "activeStudents.status": { "_eq": "ACTIVE" },
            "activeStudents.classId": { "_eq": { "_field": "id" } }
          }
        )
      }
      """;
    EntityDefinition entity = (EntityDefinition) ASTNodeConverter.parseFML(fml).getModels().getFirst();
    ModelRefField relation = (ModelRefField) entity.getField("activeStudents");
    assertNotNull(relation.getFilter());
    assertEquals(2, relation.getFilter().size());
    assertEquals(Map.of("_eq", "ACTIVE"), relation.getFilter().get("activeStudents.status"));
    assertEquals(Map.of("_eq", Map.of("_field", "id")), relation.getFilter().get("activeStudents.classId"));
  }

  @Test
  void relationFilterStringIsParsed() throws ParseException {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          localField: "id",
          foreignField: "classId",
          filter: "{\\"activeStudents.status\\":{\\"_eq\\":\\"ACTIVE\\"}}"
        )
      }
      """;
    EntityDefinition entity = (EntityDefinition) ASTNodeConverter.parseFML(fml).getModels().getFirst();
    ModelRefField relation = (ModelRefField) entity.getField("activeStudents");
    assertEquals(Map.of("activeStudents.status", Map.of("_eq", "ACTIVE")), relation.getFilter());
  }

  @Test
  void relationFilterFmlRoundTrips() throws ParseException {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          filter: {
            "_and": [
              { "activeStudents.classId": { "_eq": { "_field": "id" } } },
              { "activeStudents.status": { "_eq": "ACTIVE" } }
            ]
          }
        )
      }
      """;
    EntityDefinition entity = (EntityDefinition) ASTNodeConverter.parseFML(fml).getModels().getFirst();
    ModelRefField relation = (ModelRefField) entity.getField("activeStudents");
    EntityDefinition reparsed =
      (EntityDefinition) ASTNodeConverter.parseFML(entity.getFml()).getModels().getFirst();
    ModelRefField reparsedRelation = (ModelRefField) reparsed.getField("activeStudents");
    assertEquals(relation.getStrategy(), reparsedRelation.getStrategy());
    assertEquals(relation.getFilter(), reparsedRelation.getFilter());
  }

  @Test
  void conditionRelationRejectsTargetOnlyFilter() {
    String fml = """
      model f_classes {
        id : Long @id,
        activeStudents : Student[] @relation(
          filter: { "activeStudents.status": { "_eq": "ACTIVE" } }
        )
      }
      """;
    assertThrows(IllegalArgumentException.class, () -> ASTNodeConverter.parseFML(fml));
  }

  @Test
  void migrationAnnotationIsStoredInAdditionalProperties() throws ParseException {
    String fml = "model f_log {\n"
            + "  id : String @id @default(uuid()),\n"
            + "  message : String,\n"
            + "  @system,\n"
            + "  @migration(enabled: false),\n"
            + "  @comment(\"日志表\")\n"
            + "}\n";
    List<SchemaObject> models = ASTNodeConverter.parseFML(fml).getModels();
    assertEquals(1, models.size());
    EntityDefinition entity = (EntityDefinition) models.get(0);
    Object migration = entity.getAdditionalProperties().get("migration");
    assertNotNull(migration, "@migration 应存入 additionalProperties");
    assertInstanceOf(Map.class, migration, "带参数的 @migration 应存为参数 Map");
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) migration;
    assertEquals("false", params.get("enabled"), "enabled 参数应以 String 形式存储");
  }


  @Test
  void enumCodeAndCommentRoundTrips() throws ParseException {
    String fml = """
      enum OrderStatus {
        PENDING @code(1) @comment("待支付"),
        PAID @code(2),
        CLOSED @code(9),
        @system
      }
      """;
    List<SchemaObject> models = ASTNodeConverter.parseFML(fml).getModels();
    EnumDefinition orderStatus = (EnumDefinition) models.getFirst();
    assertEquals(List.of("PENDING", "PAID", "CLOSED"), orderStatus.getElements().stream().map(EnumElement::getName).toList());
    List<EnumElement> definitions = orderStatus.getElements();
    assertEquals(3, definitions.size());
    assertEquals("PENDING", definitions.get(0).getName());
    assertEquals(1L, definitions.get(0).getCode());
    assertEquals("待支付", definitions.get(0).getComment());
    assertEquals(2L, definitions.get(1).getCode());
    assertNull(definitions.get(1).getComment());
    assertEquals(9L, definitions.get(2).getCode());

    String roundTrip = orderStatus.getFml();
    assertTrue(roundTrip.contains("PENDING @code(1) @comment(\"待支付\")"),
      "round-trip 必须保留元素注解，且整数编码不得退化为字符串字面量: " + roundTrip);
    assertFalse(roundTrip.contains("@code(\"1\")"), "整数 @code 不得回写为字符串");
    assertTrue(roundTrip.contains("@system"), "枚举级注解必须保留");
  }

  @Test
  void enumRejectsPartialCodeAnnotations() {
    String fml = """
      enum OrderStatus {
        PENDING @code(1),
        PAID,
        @system
      }
      """;
    assertThrows(IllegalArgumentException.class, () -> ASTNodeConverter.parseFML(fml));
  }

  @Test
  void enumRejectsDuplicateCodes() {
    String fml = """
      enum OrderStatus {
        PENDING @code(1),
        PAID @code(1)
      }
      """;
    assertThrows(IllegalArgumentException.class, () -> ASTNodeConverter.parseFML(fml));
  }

  @Test
  void enumRejectsEnumLevelCodeAnnotation() {
    String fml = """
      enum OrderStatus {
        PENDING,
        PAID,
        @code(1)
      }
      """;
    assertThrows(IllegalArgumentException.class, () -> ASTNodeConverter.parseFML(fml));
  }
}
