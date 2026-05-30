package dev.flexmodel;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/**
 * @author cjbi
 */
public class SQLiteTestResource implements QuarkusTestResourceLifecycleManager {

  private File testDbFile;
  private File testDevDbFile;

  @Override
  public Map<String, String> start() {
    // 为每次测试创建唯一的临时文件数据库，避免测试之间的状态污染
    String testId = UUID.randomUUID().toString().substring(0, 8);
    testDbFile = createTempDbFile("flexmodel-test-" + testId + ".db");
    testDevDbFile = createTempDbFile("flexmodel-dev-test-" + testId + ".db");

    return Map.of(
      "flexmodel.datasource.db-kind", "sqlite",
      "flexmodel.datasource.url", "jdbc:sqlite:file:" + testDbFile.getAbsolutePath(),
      "flexmodel.datasource.username", "",
      "flexmodel.datasource.password", "",
      "SQLITE_URL", "jdbc:sqlite:file:" + testDbFile.getAbsolutePath(),
      "SQLITE_USERNAME", "",
      "SQLITE_PASSWORD", "",
      "flexmodel.datasource.dev_test.db-kind", "sqlite",
      "flexmodel.datasource.dev_test.url", "jdbc:sqlite:file:" + testDevDbFile.getAbsolutePath());
  }

  private File createTempDbFile(String name) {
    File tempDir = new File(System.getProperty("java.io.tmpdir"), "flexmodel-test");
    if (!tempDir.exists()) {
      tempDir.mkdirs();
    }
    File dbFile = new File(tempDir, name);
    // 删除可能存在的旧文件
    if (dbFile.exists()) {
      dbFile.delete();
    }
    return dbFile;
  }

  @Override
  public void stop() {
    // 清理临时数据库文件
    if (testDbFile != null && testDbFile.exists()) {
      testDbFile.delete();
    }
    if (testDevDbFile != null && testDevDbFile.exists()) {
      testDevDbFile.delete();
    }
  }
}
