package dev.flexmodel.common.trace;

public final class TraceContextHolder {

  private static final ThreadLocal<CurrentTraceContext> CURRENT_CONTEXT = new ThreadLocal<>();

  private TraceContextHolder() {
  }

  public static CurrentTraceContext current() {
    CurrentTraceContext context = CURRENT_CONTEXT.get();
    if (context == null) {
      context = new CurrentTraceContext();
      CURRENT_CONTEXT.set(context);
    }
    return context;
  }
}
