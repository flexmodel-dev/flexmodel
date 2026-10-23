package dev.flexmodel.common;

import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;

/**
 * Local transaction boundary. Borrowed repository sessions never close the owning session.
 */
@ApplicationScoped
public class ProjectUnitOfWork {
  private static final String PLATFORM_SCHEMA = "system";

  private record State(String projectId, String schema, Session session, List<Runnable> committed) {
  }

  private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
  @Inject
  SessionFactory sessionFactory;
  @Inject
  dev.flexmodel.common.persistence.ServerSessions sessions;

  /**
   * 开启项目业务库事务。
   */
  public <T> T execute(String projectId, Supplier<T> action) {
    return transaction(projectId, null, action);
  }

  /**
   * 在平台库中开启事务。
   * <p>
   * 平台级表统一存于 system schema，无法与业务库事务保持原子性，
   * 因此平台表写入使用独立事务。
   */
  public <T> T executePlatform(String projectId, Supplier<T> action) {
    return transaction(projectId, PLATFORM_SCHEMA, action);
  }

  /**
   * 事务模板。targetSchema 为 null 时使用项目业务库，否则使用指定 schema。
   * <p>
   * 同 schema 嵌套复用当前事务；跨 schema 嵌套开启独立事务，结束后恢复外层上下文。
   */
  private <T> T transaction(String projectId, String targetSchema, Supplier<T> action) {
    State existing = CURRENT.get();
    if (existing != null) {
      boolean sameSchema = targetSchema == null
        ? !PLATFORM_SCHEMA.equals(existing.schema())
        : targetSchema.equals(existing.schema());
      if (sameSchema) {
        if (!Objects.equals(projectId, existing.projectId())) throw new IllegalStateException("禁止嵌套跨项目事务");
        return action.get();
      }
    }
    String schema = targetSchema != null ? targetSchema : projectSchema(projectId);
    List<Runnable> committed = new ArrayList<>();
    try (var session = sessions.openOwned(schema)) {
      session.startTransaction();
      CURRENT.set(new State(projectId, schema, session, committed));
      try {
        T result = action.get();
        session.commit();
        return result;
      } catch (RuntimeException | Error error) {
        session.rollback();
        committed.clear();
        throw error;
      } finally {
        // 恢复外层事务上下文，支持业务事务与平台事务相互嵌套
        if (existing == null) CURRENT.remove();
        else CURRENT.set(existing);
      }
    } finally {
      for (Runnable callback : committed) callback.run();
    }
  }

  private String projectSchema(String projectId) {
    try (Session system = sessionFactory.createSession()) {
      Project project = system.dsl().selectFrom(Project.class)
        .where(dev.flexmodel.codegen.System.project.id.eq(projectId)).executeOne();
      if (project == null) throw new NotFoundException("应用不存在");
      return project.getDatabaseName();
    }
  }

  public static Session borrowPlatform() {
    State state = CURRENT.get();
    return state != null && PLATFORM_SCHEMA.equals(state.schema()) ? lease(state.session()) : null;
  }

  public static Session borrowSchema(String schema) {
    State state = CURRENT.get();
    return state != null && Objects.equals(schema, state.schema()) ? lease(state.session()) : null;
  }

  public static int lock(String projectId, String model, String column, Object value) {
    var state = CURRENT.get();
    if (state == null || !Objects.equals(projectId, state.projectId()))
      throw new IllegalStateException("行锁必须使用当前项目事务");
    return dev.flexmodel.common.persistence.ServerSessions.managed(state.session()).lockRow(model, column, value);
  }

  public static int lock(String projectId, Class<?> model, String column, Object value) {
    return lock(projectId, ReflectionUtils.getModelNameFromClass(model), column, value);
  }

  public <T> T adopt(String projectId, Session session, Supplier<T> action) {
    State existing = CURRENT.get();
    if (existing != null) {
      if (!Objects.equals(projectId, existing.projectId())) throw new IllegalStateException("禁止嵌套跨项目事务");
      // 仅同一会话才能复用；跨库嵌套（如平台事务内写业务表）必须为传入会话开启独立事务
      if (existing.session() == session) return action.get();
    }
    List<Runnable> committed = new ArrayList<>();
    var managed = dev.flexmodel.common.persistence.ServerSessions.managed(session);
    boolean ownsTransaction = !managed.isTransactionActive();
    if (ownsTransaction) session.startTransaction();
    CURRENT.set(new State(projectId, session.getName(), session, committed));
    try {
      T result = action.get();
      committed.forEach(managed::afterCommit);
      if (ownsTransaction) session.commit();
      return result;
    } catch (RuntimeException | Error error) {
      if (ownsTransaction) session.rollback();
      committed.clear();
      throw error;
    } finally {
      if (existing == null) CURRENT.remove();
      else CURRENT.set(existing);
    }
  }

  public static void afterCommit(Runnable action) {
    State state = CURRENT.get();
    if (state == null) action.run();
    else state.committed().add(action);
  }

  private static Session lease(Session session) {
    Class<?> contract = session instanceof dev.flexmodel.common.persistence.ManagedSession ? dev.flexmodel.common.persistence.ManagedSession.class : Session.class;
    return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
      if (Set.of("close", "commit", "rollback", "startTransaction").contains(method.getName())) return null;
      try {
        return method.invoke(session, args);
      } catch (InvocationTargetException exception) {
        throw exception.getCause();
      }
    });
  }
}
