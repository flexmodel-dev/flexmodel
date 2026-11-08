package dev.flexmodel.session;

import dev.flexmodel.sql.JdbcSchemaProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 事务工作单元。
 * 同 schema 嵌套加入当前事务；跨 schema 嵌套挂起外层、开独立事务、结束后恢复外层上下文。
 * afterCommit 回调注册到宿主会话：提交确认后随会话关闭（连接归还、事件发布完成）按序执行，回滚丢弃。
 */
public class UnitOfWork {

  private record Frame(String schema, Session session) {
  }

  private static final ThreadLocal<Deque<Frame>> STACK = new ThreadLocal<>();
  private static final Set<String> LEASE_BLOCKED = Set.of("close", "commit", "rollback", "startTransaction");

  private final SessionFactory factory;

  public UnitOfWork(SessionFactory factory) {
    this.factory = factory;
  }

  /**
   * schema 级事务模板。自建会话采用 READ_COMMITTED 隔离级别与方言连接准备。
   */
  public <T> T execute(String schema, Supplier<T> action) {
    Frame current = current();
    if (current != null && current.schema().equals(schema)) {
      return action.get();
    }
    if (!(factory.getSchemaProvider(schema) instanceof JdbcSchemaProvider)) {
      throw new UnsupportedOperationException("事务工作单元需要 SQL 数据源");
    }
    try (Session session = factory.createSession(schema, SessionOptions.readCommitted())) {
      session.startTransaction();
      push(new Frame(schema, session));
      try {
        T result = action.get();
        session.commit();
        return result;
      } catch (RuntimeException | Error error) {
        session.rollback();
        throw error;
      } finally {
        pop();
      }
    }
  }

  /**
   * 接管既有会话：其无事务则为其开启并拥有提交权，有事务则仅加入。
   * 会话生命周期仍由调用方负责，本方法不关闭会话。
   */
  public <T> T adopt(Session session, Supplier<T> action) {
    Frame current = current();
    if (current != null && current.session() == session) {
      return action.get();
    }
    boolean ownsTransaction = !session.isTransactionActive();
    if (ownsTransaction) {
      session.startTransaction();
    }
    push(new Frame(session.getName(), session));
    try {
      T result = action.get();
      if (ownsTransaction) {
        session.commit();
      }
      return result;
    } catch (RuntimeException | Error error) {
      if (ownsTransaction) {
        session.rollback();
      }
      throw error;
    } finally {
      pop();
    }
  }

  public static boolean isActive() {
    return current() != null;
  }

  public static String currentSchema() {
    Frame frame = current();
    return frame != null ? frame.schema() : null;
  }

  /**
   * 注册提交后回调；无事务上下文时立即执行。
   */
  public static void afterCommit(Runnable action) {
    Frame frame = current();
    if (frame == null) {
      action.run();
    } else {
      frame.session().afterCommit(action);
    }
  }

  /**
   * 当前事务内行锁；无事务上下文抛出 IllegalStateException。
   */
  public static int lock(String model, String column, Object value) {
    Frame frame = current();
    if (frame == null) {
      throw new IllegalStateException("行锁必须位于事务内");
    }
    return frame.session().lockRow(model, column, value);
  }

  /**
   * 从当前上下文借出会话代理（禁 close/commit/rollback/startTransaction）；无匹配上下文返回 null。
   */
  public static Session borrow(String schema) {
    Frame frame = current();
    return frame != null && frame.schema().equals(schema) ? lease(frame.session()) : null;
  }

  private static Frame current() {
    Deque<Frame> stack = STACK.get();
    return stack == null || stack.isEmpty() ? null : stack.peek();
  }

  private static void push(Frame frame) {
    Deque<Frame> stack = STACK.get();
    if (stack == null) {
      stack = new ArrayDeque<>();
      STACK.set(stack);
    }
    stack.push(frame);
  }

  private static void pop() {
    Deque<Frame> stack = STACK.get();
    if (stack != null) {
      stack.pop();
      if (stack.isEmpty()) {
        STACK.remove();
      }
    }
  }

  private static Session lease(Session session) {
    return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
      (proxy, method, args) -> {
        if (LEASE_BLOCKED.contains(method.getName())) {
          return null;
        }
        try {
          return method.invoke(session, args);
        } catch (InvocationTargetException exception) {
          throw exception.getCause();
        }
      });
  }
}
