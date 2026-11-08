package dev.flexmodel.sql;

import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.query.Query;
import dev.flexmodel.reflect.LazyLoadInterceptor;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.EventAwareDataService;
import dev.flexmodel.service.WriteInterceptor;
import dev.flexmodel.session.AbstractSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * SQL数据库的Session实现
 * 负责SQL数据库的事务管理和连接管理
 *
 * @author cjbi
 */
public class SqlSession extends AbstractSession {

  private final Connection connection;
  private final SqlContext sqlContext;
  private final List<Runnable> pendingCallbacks = new ArrayList<>();
  private final List<Runnable> committedCallbacks = new ArrayList<>();
  private int eventBoundary;
  private DataService interceptedData;

  public SqlSession(SqlContext sqlContext, DataService dataService) {
    super(sqlContext, dataService, new SqlSchemaService(sqlContext));
    this.connection = sqlContext.getConnection();
    this.sqlContext = sqlContext;
  }

  @Override
  public DataService data() {
    if (interceptedData == null) {
      interceptedData = applyWriteInterceptors();
    }
    return interceptedData;
  }

  /**
   * 工厂级写拦截器：拦截 insert、update、delete 前缀方法，其余直接委托事件感知数据服务。
   */
  private DataService applyWriteInterceptors() {
    List<WriteInterceptor> interceptors = sqlContext.getFactory().getWriteInterceptors();
    if (interceptors.isEmpty()) {
      return dataService;
    }
    return (DataService) Proxy.newProxyInstance(DataService.class.getClassLoader(), new Class<?>[]{DataService.class},
      (proxy, method, args) -> {
        String name = method.getName();
        Supplier<Object> proceed = () -> {
          try {
            return method.invoke(dataService, args);
          } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException(error.getCause());
          } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
          }
        };
        if (!name.startsWith("insert") && !name.startsWith("update") && !name.startsWith("delete")) {
          return proceed.get();
        }
        Supplier<Object> chain = proceed;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
          WriteInterceptor interceptor = interceptors.get(i);
          Supplier<Object> next = chain;
          chain = () -> interceptor.intercept(this, name, args, next);
        }
        return chain.get();
      });
  }

  @Override
  public boolean isTransactionActive() {
    try {
      return !connection.getAutoCommit();
    } catch (SQLException error) {
      throw new IllegalStateException(error);
    }
  }

  @Override
  public void afterCommit(Runnable callback) {
    (isTransactionActive() ? pendingCallbacks : committedCallbacks).add(callback);
  }

  @Override
  public int lockRow(String model, String column, Object value) {
    if (!isTransactionActive()) throw new IllegalStateException("行锁必须位于事务内");
    EntityDefinition definition = (EntityDefinition) sqlContext.getModelDefinition(model);
    if (definition == null || definition.getField(column) == null) throw new IllegalArgumentException("行锁字段不存在");
    String table = sqlContext.getSqlDialect().quoteIdentifier(model);
    String key = sqlContext.getSqlDialect().quoteIdentifier(column);
    return sqlContext.getJdbcOperations()
      .update("update " + table + " set " + key + "=" + key + " where " + key + "=:lockValue", Map.of("lockValue", value));
  }

  @Override
  public List<Map<String, Object>> lockRows(String model, Query query) {
    if (!isTransactionActive()) throw new IllegalStateException("行锁必须位于事务内");
    EntityDefinition definition = (EntityDefinition) sqlContext.getModelDefinition(model);
    String id = definition.findIdField().orElseThrow().getName();
    query.setForUpdate(true);
    List<Map<String, Object>> rows = data().findAll(model, query);
    if (!sqlContext.getSqlDialect().supportsForUpdate()) {
      rows.stream()
        .sorted(Comparator.comparing(row -> Objects.toString(row.get(id), "")))
        .forEach(row -> lockRow(model, id, row.get(id)));
    }
    return rows;
  }

  @Override
  public void startTransaction() {
    if (isTransactionActive()) return;
    eventBoundary = markEventBoundary();
    try {
      this.connection.setAutoCommit(false);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      log.debug("Start Transaction Session {}", sessionId);
    }
  }

  @Override
  public void commit() {
    try {
      if (!connection.getAutoCommit()) {
        connection.commit();
      }
      this.connection.setAutoCommit(true);
      committedCallbacks.addAll(pendingCallbacks);
      pendingCallbacks.clear();
      eventBoundary = markEventBoundary();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      log.debug("Commit Session {}", sessionId);
    }
  }

  @Override
  public void rollback() {
    try {
      connection.rollback();
      this.connection.setAutoCommit(true);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      discardTransaction();
      log.debug("Rollback Session {}", sessionId);
    }
  }

  /**
   * 丢弃当前事务产生的缓冲事件与未提交回调。
   */
  private void discardTransaction() {
    discardEventsSince(eventBoundary);
    pendingCallbacks.clear();
  }

  private int markEventBoundary() {
    return dataService instanceof EventAwareDataService aware ? aware.markEventBoundary() : 0;
  }

  private void discardEventsSince(int boundary) {
    if (dataService instanceof EventAwareDataService aware) {
      aware.discardEventsSince(boundary);
    }
  }

  @Override
  public void close() {
    if (isClosed()) return;
    if (isTransactionActive()) discardTransaction();
    try {
      if (!connection.getAutoCommit()) {
        connection.rollback();
        log.warn("Session {} closed with uncommitted transaction, rolled back", sessionId);
      }
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      LazyLoadInterceptor.clear();
      log.debug("Closed Session {}", sessionId);
      // 连接归还后先发布事件，再执行提交后回调
      flushEvents();
      var callbacks = List.copyOf(committedCallbacks);
      committedCallbacks.clear();
      for (var callback : callbacks) {
        try {
          callback.run();
        } catch (RuntimeException error) {
          log.warn("提交后通知执行失败", error);
        }
      }
    }
  }

  @Override
  public boolean isClosed() {
    try {
      return connection.isClosed();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
