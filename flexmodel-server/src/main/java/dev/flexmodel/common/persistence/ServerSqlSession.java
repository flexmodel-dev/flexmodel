package dev.flexmodel.common.persistence;

import dev.flexmodel.approval.ApprovalRecordGuard;
import dev.flexmodel.common.SqlErrors;
import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.EventAwareDataService;
import dev.flexmodel.sql.SqlContext;
import dev.flexmodel.sql.SqlDataService;
import dev.flexmodel.sql.SqlSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;

/**
 * Server policy and transaction lifecycle built on the engine's existing public SQL APIs.
 */
final class ServerSqlSession extends SqlSession implements ManagedSession {
  private final SqlContext context;
  private final Connection connection;
  private final CommitEvents events;
  private final DataService guarded;
  private final List<Runnable> pendingCallbacks = new ArrayList<>();
  private final List<Runnable> committedCallbacks = new ArrayList<>();
  private int eventBoundary;

  ServerSqlSession(SqlContext context, ApprovalRecordGuard guard) {
    this(context, guard, new CommitEvents(context));
  }

  private ServerSqlSession(SqlContext context, ApprovalRecordGuard guard, CommitEvents events) {
    super(context, events);
    this.context = context;
    this.connection = context.getConnection();
    this.events = events;
    guarded = (DataService) Proxy.newProxyInstance(DataService.class.getClassLoader(), new Class<?>[]{DataService.class}, (proxy, method, args) -> {
      Supplier<Object> invoke = () -> {
        try {
          return method.invoke(events, args);
        } catch (InvocationTargetException error) {
          if (error.getCause() instanceof RuntimeException runtime) throw databaseFailure(runtime);
          if (error.getCause() instanceof Error fatal) throw fatal;
          throw new IllegalStateException(error.getCause());
        } catch (IllegalAccessException error) {
          throw new IllegalStateException(error);
        }
      };
      if (method.getName().equals("getDataService")) return proxy;
      if (method.getName().startsWith("insert") || method.getName().startsWith("update") || method.getName().startsWith("delete"))
        return guard.intercept(this, method.getName(), args, invoke);
      return invoke.get();
    });
  }

  @Override
  public DataService data() {
    return guarded;
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
  public int lockRow(String model, String column, Object value) {
    if (!isTransactionActive()) throw new IllegalStateException("行锁必须位于事务内");
    var definition = (dev.flexmodel.model.EntityDefinition) context.getModelDefinition(model);
    if (definition == null || definition.getField(column) == null) throw new IllegalArgumentException("行锁字段不存在");
    String table = context.getSqlDialect().quoteIdentifier(model);
    String key = context.getSqlDialect().quoteIdentifier(column);
    try {
      return context.getJdbcOperations().update("update " + table + " set " + key + "=" + key + " where " + key + "=:lockValue", Map.of("lockValue", value));
    } catch (RuntimeException error) {
      throw databaseFailure(error);
    }
  }

  @Override
  public List<Map<String, Object>> lockRows(String model, dev.flexmodel.query.Query query) {
    if (!isTransactionActive()) throw new IllegalStateException("行锁必须位于事务内");
    var definition = (dev.flexmodel.model.EntityDefinition) context.getModelDefinition(model);
    String id = definition.findIdField().orElseThrow().getName();
    query.setForUpdate(true);
    try {
      var rows = events.findAll(model, query);
      if (!context.getSqlDialect().supportsForUpdate()) rows.stream()
        .sorted(Comparator.comparing(row -> Objects.toString(row.get(id), ""))).forEach(row -> lockRow(model, id, row.get(id)));
      return rows;
    } catch (RuntimeException error) {
      throw databaseFailure(error);
    }
  }

  private RuntimeException databaseFailure(RuntimeException error) {
    if (context.getSqlDialect() instanceof dev.flexmodel.sql.dialect.SQLiteSqlDialect && SqlErrors.sqliteBusy(error))
      return new jakarta.ws.rs.ServiceUnavailableException("SQLite 开发数据库正忙，请稍后重试");
    return error;
  }

  @Override
  public void startTransaction() {
    if (isTransactionActive()) return;
    eventBoundary = events.pending.size();
    super.startTransaction();
  }

  @Override
  public void commit() {
    super.commit();
    committedCallbacks.addAll(pendingCallbacks);
    pendingCallbacks.clear();
    eventBoundary = events.pending.size();
  }

  @Override
  public void rollback() {
    try {
      super.rollback();
    } finally {
      discardTransaction();
    }
  }

  private void discardTransaction() {
    events.pending.subList(Math.min(eventBoundary, events.pending.size()), events.pending.size()).clear();
    pendingCallbacks.clear();
  }

  @Override
  public void afterCommit(Runnable callback) {
    (isTransactionActive() ? pendingCallbacks : committedCallbacks).add(callback);
  }

  @Override
  public void close() {
    if (isClosed()) return;
    if (isTransactionActive()) discardTransaction();
    try {
      super.close();
    } finally {
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

  /**
   * Keep committed events, discard only the events belonging to a rolled-back transaction.
   */
  private static final class CommitEvents extends EventAwareDataService {
    private final List<ChangedEvent> pending = new ArrayList<>();

    CommitEvents(SqlContext context) {
      super(new SqlDataService(context), context.getFactory().getEventPublisher(), context.getSchemaName(), UUID.randomUUID().toString(), context.getFactory());
    }

    @Override
    public void addPendingChangedEvent(ChangedEvent event) {
      pending.add(event);
    }

    @Override
    public void flushPendingEvents() {
      var ready = List.copyOf(pending);
      pending.clear();
      ready.forEach(super::addPendingChangedEvent);
      super.flushPendingEvents();
    }
  }
}
