package dev.flexmodel.common;

import dev.flexmodel.codegen.entity.Project;
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
  private record State(String projectId, String schema, Session session, List<Runnable> committed) {
  }

  private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
  @Inject
  SessionFactory sessionFactory;
  @Inject
  dev.flexmodel.common.persistence.ServerSessions sessions;

  public <T> T execute(String projectId, Supplier<T> action) {
    State existing = CURRENT.get();
    if (existing != null) {
      if (!Objects.equals(projectId, existing.projectId())) throw new IllegalStateException("禁止嵌套跨项目事务");
      return action.get();
    }
    String schema;
    try (Session system = sessionFactory.createSession()) {
      Project project = system.dsl().selectFrom(Project.class)
        .where(dev.flexmodel.codegen.System.project.id.eq(projectId)).executeOne();
      if (project == null) throw new NotFoundException("应用不存在");
      schema = project.getDatabaseName();
    }
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
        CURRENT.remove();
      }
    } finally {
      for (Runnable callback : committed) callback.run();
    }
  }

  public static Session borrow(String projectId) {
    State state = CURRENT.get();
    return state != null && Objects.equals(projectId, state.projectId()) ? lease(state.session()) : null;
  }

  public static Session borrowSchema(String schema) {
    State state = CURRENT.get();
    return state != null && Objects.equals(schema, state.schema()) ? lease(state.session()) : null;
  }

  public static boolean active() {
    return CURRENT.get() != null;
  }

  public static int lock(String projectId, String model, String column, Object value) {
    var state = CURRENT.get();
    if (state == null || !Objects.equals(projectId, state.projectId()))
      throw new IllegalStateException("行锁必须使用当前项目事务");
    return dev.flexmodel.common.persistence.ServerSessions.managed(state.session()).lockRow(model, column, value);
  }

  public <T> T adopt(String projectId, Session session, Supplier<T> action) {
    if (CURRENT.get() != null) {
      if (!Objects.equals(projectId, CURRENT.get().projectId())) throw new IllegalStateException("禁止嵌套跨项目事务");
      return action.get();
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
      CURRENT.remove();
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
