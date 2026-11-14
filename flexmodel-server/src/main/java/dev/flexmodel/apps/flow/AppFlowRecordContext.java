package dev.flexmodel.apps.flow;

import java.util.function.Supplier;

/**
 * A server-only capability limited to the owning root run.
 */
public final class AppFlowRecordContext {
  private static final ThreadLocal<String> RUN = new ThreadLocal<>();

  private AppFlowRecordContext() {
  }

  public static String runId() {
    return RUN.get();
  }

  public static <T> T as(String run, Supplier<T> action) {
    String previous = RUN.get();
    RUN.set(run);
    try {
      return action.get();
    } finally {
      if (previous == null) RUN.remove();
      else RUN.set(previous);
    }
  }
}
