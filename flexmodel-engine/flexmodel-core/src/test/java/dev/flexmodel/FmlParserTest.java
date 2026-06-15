package dev.flexmodel;

import org.junit.jupiter.api.Test;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.parser.impl.ModelParser;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试FML解析功能
 *
 * @author cjbi
 */
public class FmlParserTest {

    @Test
    void testParseSimpleModel() throws Exception {
        String fmlString = """
            model User {
              id: String @id @default(uuid()),
              name: String @length(255),
              age: Int,
              email: String @unique,
              createdAt: DateTime @default(now()),
            }
            """;

        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        assertNotNull(ast);
        assertEquals(1, ast.size());

        ModelParser.ASTNode node = ast.get(0);
        assertTrue(node instanceof ModelParser.Model);

        ModelParser.Model model = (ModelParser.Model) node;
        assertEquals("User", model.name);
        assertEquals(5, model.fields.size());

        // 验证字段
        var idField = model.fields.stream()
            .filter(f -> "id".equals(f.name))
            .findFirst()
            .orElse(null);
        assertNotNull(idField);
        assertEquals("String", idField.type);
        assertFalse(idField.optional);
        assertEquals(2, idField.annotations.size());
        assertEquals("id", idField.annotations.get(0).name);
    }

    @Test
    void testParseModelWithEnum() throws Exception {
        String fmlString = """
            enum UserStatus {
              ACTIVE,
              INACTIVE,
              PENDING
            }

            model User {
              id: String @id @default(uuid()),
              name: String,
              status: UserStatus @default(ACTIVE),
            }
            """;

        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        assertNotNull(ast);
        assertEquals(2, ast.size());

        // 验证枚举
        ModelParser.ASTNode enumNode = ast.get(0);
        assertTrue(enumNode instanceof ModelParser.Enumeration);

        ModelParser.Enumeration enumeration = (ModelParser.Enumeration) enumNode;
        assertEquals("UserStatus", enumeration.name);
        assertEquals(3, enumeration.elements.size());
        assertTrue(enumeration.elements.contains("ACTIVE"));
        assertTrue(enumeration.elements.contains("INACTIVE"));
        assertTrue(enumeration.elements.contains("PENDING"));

        // 验证模型
        ModelParser.ASTNode modelNode = ast.get(1);
        assertTrue(modelNode instanceof ModelParser.Model);

        ModelParser.Model model = (ModelParser.Model) modelNode;
        assertEquals("User", model.name);
        assertEquals(3, model.fields.size());
    }

    @Test
    void testParseModelWithRelations() throws Exception {
        String fmlString = """
            model Post {
              id: String @id @default(uuid()),
              title: String,
              content: String,
              authorId: String,
              author: User @relation(localField: "authorId", foreignField: "id"),
            }

            model User {
              id: String @id @default(uuid()),
              name: String,
              posts: Post[] @relation(localField: "id", foreignField: "authorId"),
            }
            """;

        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        assertNotNull(ast);
        assertEquals(2, ast.size());

        // 验证Post模型
        ModelParser.Model postModel = (ModelParser.Model) ast.get(0);
        assertEquals("Post", postModel.name);
        assertEquals(5, postModel.fields.size());

        // 验证关系字段
        var authorField = postModel.fields.stream()
            .filter(f -> "author".equals(f.name))
            .findFirst()
            .orElse(null);
        assertNotNull(authorField);
        assertEquals("User", authorField.type);
        assertEquals(1, authorField.annotations.size());
        assertEquals("relation", authorField.annotations.get(0).name);

        var relationParams = authorField.annotations.get(0).parameters;
        assertEquals("authorId", relationParams.get("localField"));
        assertEquals("id", relationParams.get("foreignField"));
    }

    @Test
    void testASTNodeConverter() throws Exception {
        String fmlString = """
            model User {
              id: String @id @default(uuid()),
              name: String @length(255),
              age: Int,
            }
            """;

        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        // 转换为SchemaObject
        ModelParser.ASTNode node = ast.get(0);
        SchemaObject schemaObject = ASTNodeConverter.toSchemaObject(node);

        assertNotNull(schemaObject);
        assertTrue(schemaObject instanceof EntityDefinition);

        EntityDefinition entity = (EntityDefinition) schemaObject;
        assertEquals("User", entity.getName());
        assertEquals(3, entity.getFields().size());

        // 验证字段
        var idField = entity.getField("id");
        assertNotNull(idField);
        assertTrue(idField.isIdentity());

        var nameField = entity.getField("name");
        assertNotNull(nameField);
        assertEquals("String", nameField.getType());
    }

    @Test
    void testParseInvalidSyntax() {
        String invalidFml = """
            model User {
              id: String @id @default(uuid()),
              name: String,
              // 缺少闭合大括号
            """;

        assertThrows(Exception.class, () -> {
            ModelParser parser = new ModelParser(new StringReader(invalidFml));
            parser.CompilationUnit();
        });
    }

    @Test
    void testParseEmptyString() throws Exception {
        ModelParser parser = new ModelParser(new StringReader(""));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        assertNotNull(ast);
        assertTrue(ast.isEmpty());
    }

    @Test
    void testParseSeedWithJsonFormat() throws Exception {
        // JSON 格式 seed：键名为双引号字符串，值支持复杂嵌套 JSON
        String fmlString = """
            seed flow_model {
              {"id": "1", "name": "test", "flow_model": "{\\"nodes\\":[{\\"id\\":\\"n1\\",\\"type\\":\\"start\\"},{\\"id\\":\\"n2\\",\\"type\\":\\"end\\"}],\\"edges\\":[{\\"from\\":\\"n1\\",\\"to\\":\\"n2\\"}]}", "enabled": true, "config": {"timeout": 30, "retries": 3}},
              {"id": "2", "name": "test2", "tags": ["a", "b", "c"], "nested": {"x": 1, "y": [1, 2, 3]}},
            }
            """;

        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        assertNotNull(ast);
        assertEquals(1, ast.size());
        assertTrue(ast.get(0) instanceof ModelParser.Seed);

        ModelParser.Seed seed = (ModelParser.Seed) ast.get(0);
        assertEquals("flow_model", seed.modelName);
        assertEquals(2, seed.records.size());

        // 验证第一条记录
        var record1 = seed.records.get(0);
        assertEquals("1", record1.get("id"));
        assertEquals("test", record1.get("name"));
        assertEquals(true, record1.get("enabled"));
        // 验证 flow_model 字段是解析后的 JSON 字符串（作为字符串值保留）
        assertTrue(record1.get("flow_model") instanceof String);
        // 验证嵌套对象
        assertTrue(record1.get("config") instanceof java.util.Map);
        @SuppressWarnings("unchecked")
        var config = (java.util.Map<String, Object>) record1.get("config");
        assertEquals(30, config.get("timeout"));
        assertEquals(3, config.get("retries"));

        // 验证第二条记录
        var record2 = seed.records.get(1);
        assertEquals("2", record2.get("id"));
        assertTrue(record2.get("tags") instanceof List);
        @SuppressWarnings("unchecked")
        var tags = (List<String>) record2.get("tags");
        assertEquals(3, tags.size());
        assertTrue(record2.get("nested") instanceof java.util.Map);
    }

    @Test
    void testParseSeedWithIdlAndJsonMixed() throws Exception {
        // 同一个 FML 文件中混合使用 IDL 和 JSON 格式的 seed
        String fmlString = """
            seed User {
              {name: "张三", age: 18},
              {name: "李四", age: 20},
            }

            seed Config {
              {"key": "setting1", "value": "{\\"enabled\\":true,\\"list\\":[1,2,3]}"},
            }
            """;

        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();

        assertEquals(2, ast.size());

        // IDL 格式 seed
        ModelParser.Seed userSeed = (ModelParser.Seed) ast.get(0);
        assertEquals("User", userSeed.modelName);
        assertEquals(2, userSeed.records.size());
        assertEquals("张三", userSeed.records.get(0).get("name"));
        assertEquals(18L, userSeed.records.get(0).get("age"));

        // JSON 格式 seed
        ModelParser.Seed configSeed = (ModelParser.Seed) ast.get(1);
        assertEquals("Config", configSeed.modelName);
        assertEquals(1, configSeed.records.size());
        assertEquals("setting1", configSeed.records.get(0).get("key"));
    }

    @Test
    void testParseJsonThenIdlOnSameLine() throws Exception {
        // 模拟 project.fml 中的模式：同一行先 JSON 记录再 IDL 记录
        String fmlString = """
            seed Test {
              {"id": "1", "name": "json_record", "data": "{\\"key\\":\\"value\\"}"},  {id: "2", name: "idl_record", data: "{\\"key2\\":\\"value2\\"}"},
            }
            """;
        ModelParser parser = new ModelParser(new StringReader(fmlString));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();
        ModelParser.Seed seed = (ModelParser.Seed) ast.get(0);
        System.out.println("Same-line test records: " + seed.records.size());
        for (int i = 0; i < seed.records.size(); i++) {
            System.out.println("  Record " + i + ": " + seed.records.get(i));
        }
        assertEquals(2, seed.records.size(), "Should have 2 records (JSON + IDL)");
        assertEquals("1", seed.records.get(0).get("id"));
        assertEquals("2", seed.records.get(1).get("id"));
    }

    @Test
    void testParseProjectFml() throws Exception {
        // 解析实际的 project.fml 文件，验证包含复杂 JSON 的 seed 数据
        java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("project.fml");
        if (is == null) {
            // 尝试从 flexmodel-server 加载
            java.nio.file.Path path = java.nio.file.Paths.get("../../flexmodel-server/src/main/resources/project.fml");
            if (java.nio.file.Files.exists(path)) {
                is = java.nio.file.Files.newInputStream(path);
            }
        }
        assertNotNull(is, "project.fml not found");

        ModelParser parser = new ModelParser(new java.io.InputStreamReader(is, "UTF-8"));
        List<ModelParser.ASTNode> ast = parser.CompilationUnit();
        assertNotNull(ast);
        assertFalse(ast.isEmpty());

        // 找到 f_em_flow_definition 的 seed 声明
        ModelParser.Seed flowDefSeed = null;
        for (ModelParser.ASTNode node : ast) {
            if (node instanceof ModelParser.Seed seed && "f_em_flow_definition".equals(seed.modelName)) {
                flowDefSeed = seed;
                break;
            }
        }
        assertNotNull(flowDefSeed, "f_em_flow_definition seed not found");
        // 打印实际记录数以便调试
        System.out.println("f_em_flow_definition records: " + flowDefSeed.records.size());
        for (int i = 0; i < flowDefSeed.records.size(); i++) {
            var rec = flowDefSeed.records.get(i);
            String fmVal = rec.get("flow_model") != null ? rec.get("flow_model").toString() : "null";
            System.out.println("  Record " + i + ": id=" + rec.get("flow_module_id")
                + ", name=" + rec.get("flow_name")
                + ", flow_model_len=" + fmVal.length());
        }
        // project.fml 的 f_em_flow_definition 应有 4 条记录（全 JSON 格式）
        assertEquals(4, flowDefSeed.records.size(), "Expected 4 flow definition records (all JSON format)");

        // 验证所有记录的 flow_model 字段被正确解析
        for (var record : flowDefSeed.records) {
            assertNotNull(record.get("flow_module_id"), "flow_module_id should not be null");
            assertNotNull(record.get("flow_name"), "flow_name should not be null");
            assertNotNull(record.get("flow_model"), "flow_model should not be null");
            // flow_model 应该是一个非空 JSON 字符串
            assertTrue(record.get("flow_model") instanceof String, "flow_model should be a String");
            assertTrue(record.get("flow_model").toString().length() > 10, "flow_model should not be truncated");
        }
    }
}
