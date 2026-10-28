package dev.flexmodel.apps.approval;

import java.util.function.Supplier;

/**
 * A server-only capability limited to the owning root run.
 */
public final class AppApprovalRecordContext {
  private static final ThreadLocal<String> RUN = new ThreadLocal<>();

  private AppApprovalRecordContext() {
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
