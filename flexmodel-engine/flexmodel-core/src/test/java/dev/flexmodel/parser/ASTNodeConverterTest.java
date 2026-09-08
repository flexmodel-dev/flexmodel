package dev.flexmodel.parser;

import dev.flexmodel.parser.ASTNodeConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.model.EntityDefinition;
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

}
