package dev.flexmodel.common.trace;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CurrentTraceContext {

  private static final ThreadLocal<TraceContext.TraceScope> CURRENT = new ThreadLocal<>();

  public void set(TraceContext.TraceScope scope) {
    CURRENT.set(scope);
  }

  public TraceContext.TraceScope get() {
    return CURRENT.get();
  }

  public static CurrentTraceContext current() {
    return TraceContextHolder.current();
  }

  public void clear() {
    CURRENT.remove();
  }
}
