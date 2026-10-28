package dev.flexmodel.common.persistence;

import com.mysql.cj.jdbc.MysqlDataSource;
import dev.flexmodel.apps.approval.AppApprovalRecordGuard;
import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sqlite.SQLiteDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ServerSessionsTest {
  @TempDir
  Path temporary;
  private static GenericContainer<?> mysql;

  @BeforeAll
  static void startMysqlWhenRequested() {
    if (Boolean.getBoolean("server.sessions.mysql")) {
      mysql = new GenericContainer<>("mysql:8.0").withExposedPorts(3306)
        .withEnv("MYSQL_ROOT_PASSWORD", "session-test")
        .withEnv("MYSQL_ROOT_HOST", "%").withEnv("MYSQL_DATABASE", "session_policy")
        .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(2));
      mysql.start();
    }
  }

  @AfterAll
  static void stopMysql() {
    if (mysql != null) mysql.stop();
  }

  private ServerSessions sessions(String database) throws Exception {
    DataSource source;
    if (database.equals("sqlite")) {
      SQLiteDataSource sqlite = new SQLiteDataSource();
      sqlite.setUrl("jdbc:sqlite:" + temporary.resolve(UUID.randomUUID() + ".db"));
      source = sqlite;
    } else {
      Assumptions.assumeTrue(mysql != null, "启用 MySQL 集成验证：-Dserver.sessions.mysql=true");
      MysqlDataSource mysqlSource = new MysqlDataSource();
      mysqlSource.setURL("jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/session_policy?useSSL=false&allowPublicKeyRetrieval=true");
      mysqlSource.setUser("root");
      mysqlSource.setPassword("session-test");
      source = mysqlSource;
    }
    SessionFactory factory = SessionFactory.builder().setDefaultSchemaProvider(new JdbcSchemaProvider("policy", source)).setFailsafe(true).build();
    try (Session raw = factory.createSession()) {
      raw.applyFML("model session_items { id: String @id, name: String }");
      for (String id : List.of("lock-a", "lock-b"))
        if (raw.data().findById("session_items", id).isEmpty())
          raw.data().insert("session_items", new HashMap<>(Map.of("id", id, "name", id)));
      raw.data().delete("session_items", dev.flexmodel.query.Expressions.field("id").in("committed", "rolled-back", "uncommitted"));
    }
    ServerSessions result = new ServerSessions();
    result.factory = factory;
    result.guard = new AppApprovalRecordGuard() {
      @Override
      public Object intercept(Session session, String operation, Object[] arguments, Supplier<Object> proceed) {
        return proceed.get();
      }
    };
    return result;
  }

  @ParameterizedTest
  @ValueSource(strings = {"sqlite", "mysql"})
  void rollbackDiscardsOnlyCurrentEventsAndNotifications(String database) throws Exception {
    var sessions = sessions(database);
    var events = new ArrayList<ChangedEvent>();
    var callbacks = new ArrayList<String>();
    sessions.factory.getEventPublisher().addListener(new dev.flexmodel.event.EventListener() {
      @Override
      public void onChanged(ChangedEvent event) {
        events.add(event);
      }
    });
    try (var session = sessions.openOwned("policy")) {
      session.startTransaction();
      session.data().insert("session_items", new HashMap<>(Map.of("id", "committed", "name", "提交")));
      session.afterCommit(() -> callbacks.add("committed"));
      session.commit();
      session.startTransaction();
      session.data().insert("session_items", new HashMap<>(Map.of("id", "rolled-back", "name", "回滚")));
      session.afterCommit(() -> callbacks.add("rolled-back"));
      session.rollback();
      assertTrue(events.isEmpty());
      assertTrue(callbacks.isEmpty());
    }
    assertEquals(List.of("committed"), callbacks);
    assertEquals(1, events.size());
    assertEquals("committed", events.getFirst().getId());
    try (var session = sessions.openOwned("policy")) {
      assertTrue(session.data().findById("session_items", "committed").isPresent());
      assertTrue(session.data().findById("session_items", "rolled-back").isEmpty());
      session.startTransaction();
      session.data().insert("session_items", new HashMap<>(Map.of("id", "uncommitted", "name", "未提交")));
    }
    assertEquals(1, events.size(), "关闭未提交会话不能发送变更事件");
  }

  @ParameterizedTest
  @ValueSource(strings = {"sqlite", "mysql"})
  void sameRowSerializesIndependentConnections(String database) throws Exception {
    var sessions = sessions(database);
    try (var executor = Executors.newSingleThreadExecutor(); var first = sessions.openOwned("policy")) {
      first.startTransaction();
      assertEquals(1, first.lockRow("session_items", "id", "lock-a"));
      CountDownLatch started = new CountDownLatch(1);
      var second = executor.submit(() -> {
        try (var session = sessions.openOwned("policy")) {
          session.startTransaction();
          started.countDown();
          int affected = session.lockRow("session_items", "id", "lock-a");
          session.commit();
          return affected;
        }
      });
      assertTrue(started.await(3, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS));
      first.commit();
      assertEquals(1, second.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void sqliteContentionReturnsRetryableFailureWithoutRunningBusinessWork() throws Exception {
    var sessions = sessions("sqlite");
    try (var first = sessions.openOwned("policy"); var second = sessions.openOwned("policy")) {
      first.startTransaction();
      first.lockRow("session_items", "id", "lock-a");
      second.startTransaction();
      assertTimeout(Duration.ofSeconds(4), () -> {
        var error = assertThrows(ServiceUnavailableException.class, () -> second.lockRow("session_items", "id", "lock-a"));
        assertEquals(503, error.getResponse().getStatus());
      });
      second.rollback();
      first.rollback();
    }
  }

  @Test
  void mysqlDifferentRowsDoNotWaitForEachOther() throws Exception {
    var sessions = sessions("mysql");
    try (var first = sessions.openOwned("policy"); var executor = Executors.newSingleThreadExecutor()) {
      first.startTransaction();
      first.lockRow("session_items", "id", "lock-a");
      var independent = executor.submit(() -> {
        try (var second = sessions.openOwned("policy")) {
          second.startTransaction();
          int locked = second.lockRow("session_items", "id", "lock-b");
          second.commit();
          return locked;
        }
      });
      assertEquals(1, independent.get(2, TimeUnit.SECONDS), "无关记录必须能在另一事务释放锁之前继续执行");
      first.rollback();
    }
  }
}
