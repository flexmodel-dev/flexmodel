package dev.flexmodel.apps;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorkbenchLegacyMigrationTestResource implements QuarkusTestResourceLifecycleManager {

  private File systemDbFile;
  private File projectDbFile;

  @Override
  public Map<String, String> start() {
    String testId = UUID.randomUUID().toString().substring(0, 8);
    systemDbFile = createTempDbFile("workbench-legacy-system-" + testId + ".db");
    projectDbFile = createTempDbFile("workbench-legacy-project-" + testId + ".db");
    seedLegacyWorkbench(systemDbFile);

    Map<String, String> config = new HashMap<>();
    config.put("flexmodel.datasource.db-kind", "sqlite");
    config.put("flexmodel.datasource.url", "jdbc:sqlite:file:" + systemDbFile.getAbsolutePath());
    config.put("flexmodel.datasource.username", "");
    config.put("flexmodel.datasource.password", "");
    config.put("flexmodel.datasource.dev_test.db-kind", "sqlite");
    config.put("flexmodel.datasource.dev_test.url", "jdbc:sqlite:file:" + projectDbFile.getAbsolutePath());
    return config;
  }

  @Override
  public void stop() {
    delete(systemDbFile);
    delete(projectDbFile);
  }

  private static File createTempDbFile(String name) {
    try {
      File directory = new File(System.getProperty("java.io.tmpdir"), "flexmodel-workbench-test");
      if (!directory.exists() && !directory.mkdirs()) {
        throw new IOException("Cannot create temp directory: " + directory);
      }
      File file = new File(directory, name);
      Files.deleteIfExists(file.toPath());
      if (!file.createNewFile()) {
        throw new IOException("Cannot create temp database: " + file);
      }
      return file;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot prepare SQLite test database", exception);
    }
  }

  private static void seedLegacyWorkbench(File dbFile) {
    String layout = """
      {"components":[{"id":"component_shortcuts","title":"快捷入口","type":"shortcuts","config":{"shortcuts":[]}}],"items":[{"componentId":"component_shortcuts","width":"HALF","height":"SHORT"}]}
      """.strip();
    String sql = """
      create table f_workbench (
        id varchar(255) primary key,
        project_id varchar(255),
        name varchar(255),
        description varchar(255),
        layout longvarchar,
        created_by varchar(255),
        updated_by varchar(255),
        created_at datetime,
        updated_at datetime
      )
      """;
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
         Statement statement = connection.createStatement()) {
      statement.execute(sql);
      try (PreparedStatement insert = connection.prepareStatement(
        "insert into f_workbench (id, project_id, name, layout, created_by, updated_by, created_at, updated_at) values (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))")) {
        insert.setString(1, "workbench-legacy");
        insert.setString(2, "dev_test");
        insert.setString(3, "Legacy startup workbench");
        insert.setString(4, layout);
        insert.setString(5, "test");
        insert.setString(6, "test");
        insert.executeUpdate();
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot seed legacy workbench", exception);
    }
  }

  private static void delete(File file) {
    try {
      if (file != null) {
        Files.deleteIfExists(file.toPath());
      }
    } catch (IOException ignored) {
    }
  }
}
