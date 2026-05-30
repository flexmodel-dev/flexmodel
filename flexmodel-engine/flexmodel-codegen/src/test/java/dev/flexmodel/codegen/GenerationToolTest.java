package dev.flexmodel.codegen;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author cjbi
 */
class GenerationToolTest extends AbstractIntegrationTest {

  @Test
  void test() throws Exception {
    Configuration configuration = new Configuration();
    SchemaConfig schemaConfig = new SchemaConfig();
    schemaConfig.setName("system");
    schemaConfig.setBaseDir("src/test/resources/");
    schemaConfig.setDirectory("src/test/java");
    schemaConfig.setPackageName("com.example");
    schemaConfig.setReplaceString("f_");
    configuration.addSchema(schemaConfig);

    GenerationTool.run(configuration);
  }

  @Test
  void testFML() {
    Configuration configuration = new Configuration();
    SchemaConfig schemaConfig = new SchemaConfig();
    schemaConfig.setName("system_fml");
    schemaConfig.setImportScript("import.fml");
    schemaConfig.setBaseDir("src/test/resources/");
    schemaConfig.setDirectory("src/test/java");
    schemaConfig.setPackageName("com.example_fml");
    schemaConfig.setReplaceString("f_");
    configuration.addSchema(schemaConfig);
    GenerationTool.run(configuration);
  }
  public static void listFiles(File dir) {
    File[] files = dir.listFiles();
    if (files == null) return;

    for (File file : files) {
      if (file.isDirectory()) {
        System.out.println("dir: " + file.getAbsolutePath());
        listFiles(file); // 递归遍历子目录
      } else {
        System.out.println(file.getAbsolutePath()); // 打印文件路径
      }
    }
  }

  @AfterAll
  static void afterAll() throws IOException {
    deleteRecursively(Paths.get("src/test/java/com"));
    deleteRecursively(Paths.get("src/test/resources/target/classes"));
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) return;
    Files.walkFileTree(path, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
