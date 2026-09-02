package dev.flexmodel.common.trace;

import java.util.concurrent.Callable;

public final class TraceContextHolder {

  /**
   * 当前激活的 TraceScope 载体。基于 {@link ScopedValue}，不可变且作用域受限，
   * 适配虚拟线程：绑定值仅在 {@code ScopedValue.where(CURRENT, scope).run/call(...)}
   * 的动态范围内可见，退出即自动清理，无需手动 clear，避免 ThreadLocal 在虚拟线程下的
   * 跨调用残留与泄漏。
   */
  static final ScopedValue<TraceContext.TraceScope> CURRENT = ScopedValue.newInstance();

  private TraceContextHolder() {
  }

  /**
   * 返回当前作用域内激活的 TraceScope，未绑定时返回 {@code null}。
   * 注意：{@link ScopedValue#orElse(Object)} 要求默认值非 null，故用 isBound + get 手动判空。
   */
  public static TraceContext.TraceScope current() {
    return CURRENT.isBound() ? CURRENT.get() : null;
  }

  /**
   * 在指定 TraceScope 作用域内执行 body（无返回值）。
   */
  public static void with(TraceContext.TraceScope scope, Runnable body) {
    ScopedValue.where(CURRENT, scope).run(body);
  }

  /**
   * 在指定 TraceScope 作用域内执行 body 并返回结果。适配调用方需要返回值或抛出受检异常的场景：
   * 运行时异常与错误原样抛出，受检异常包装为运行时异常。
   */
  public static <T> T with(TraceContext.TraceScope scope, Callable<T> body) {
    try {
      // ScopedValue.call 接收 ScopedValue.CallableOp（非 java.util.concurrent.Callable），
      // 这里用 lambda 适配：body.call() 抛出的受检异常经 catch(Exception) 包装为运行时异常。
      return ScopedValue.where(CURRENT, scope).call(() -> body.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Error e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
