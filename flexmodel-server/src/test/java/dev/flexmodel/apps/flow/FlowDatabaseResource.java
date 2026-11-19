package dev.flexmodel.apps.flow;

import dev.flexmodel.SQLiteTestResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.*;

/**
 * Run the same workflow suite on SQLite or an isolated MySQL 8 container.
 */
public class FlowDatabaseResource extends SQLiteTestResource {
  private GenericContainer<?> mysql;

  @Override
  public Map<String, String> start() {
    if (!Boolean.getBoolean("flow.tests.mysql")) return super.start();
    mysql = new GenericContainer<>("mysql:8.0").withExposedPorts(3306)
      .withEnv("MYSQL_ROOT_PASSWORD", "workflow-test").withEnv("MYSQL_ROOT_HOST", "%")
      .withEnv("MYSQL_DATABASE", "workflow_system").waitingFor(Wait.forListeningPort())
      .withStartupTimeout(Duration.ofMinutes(2));
    mysql.start();
    String base = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/";
    String options = "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
    return Map.of("flexmodel.datasource.db-kind", "mysql", "flexmodel.datasource.url", base + "workflow_system" + options,
      "flexmodel.datasource.username", "root", "flexmodel.datasource.password", "workflow-test",
      "flexmodel.datasource.dev_test.db-kind", "mysql", "flexmodel.datasource.dev_test.url", base + "workflow_dev" + options,
      "flexmodel.datasource.dev_test.username", "root", "flexmodel.datasource.dev_test.password", "workflow-test",
      "flexmodel.project-url-template", base + "{{databaseName}}" + options);
  }

  @Override
  public void stop() {
    if (mysql != null) mysql.stop();
    else super.stop();
  }
}
