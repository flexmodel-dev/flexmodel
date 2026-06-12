package dev.flexmodel;

import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.parser.impl.ModelParser;

import java.io.StringReader;
import java.util.List;

/**
 * FML解析功能演示
 *
 * @author cjbi
 */
public class FmlDemo {

    public static void main(String[] args) {
        try {
            // 测试FML字符串
            String fmlString = """
                // 用户模型
                model User {
                  id: String @id @default(uuid()),
                  name: String @length(255),
                  age: Int,
                  email: String @unique,
                  createdAt: DateTime @default(now()),
                }

                // 用户状态枚举
                enum UserStatus {
                  ACTIVE,
                  INACTIVE,
                  PENDING
                }
                """;

            System.out.println("=== FML解析演示 ===");
            System.out.println("原始FML:");
            System.out.println(fmlString);
            System.out.println();

            // 解析FML
            ModelParser parser = new ModelParser(new StringReader(fmlString));
            List<ModelParser.ASTNode> ast = parser.CompilationUnit();

          System.out.println("解析结果:");
            for (ModelParser.ASTNode node : ast) {
                System.out.println(node);
            }
            System.out.println();

            // 转换为SchemaObject
            System.out.println("转换为SchemaObject:");
            for (ModelParser.ASTNode node : ast) {
                SchemaObject schemaObject = ASTNodeConverter.toSchemaObject(node);
                if (schemaObject != null) {
                    System.out.println("类型: " + schemaObject.getClass().getSimpleName());
                    System.out.println("名称: " + schemaObject.getName());
                    System.out.println("FML: " + schemaObject.getFml());
                    System.out.println();
                }
            }

            System.out.println("=== FML解析成功 ===");

        } catch (Exception e) {
            System.err.println("FML解析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
