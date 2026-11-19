package dev.flexmodel.flow.service;

import java.util.function.Supplier;

/**
 * Trusted in-process handler identity; deliberately absent from REST DTOs.
 */
public final class ExternalTaskAccess {
  private static final ThreadLocal<String> OWNER = new ThreadLocal<>();

  private ExternalTaskAccess() {
  }

  public static String owner() {
    return OWNER.get();
  }

  public static <T> T as(String handler, Supplier<T> action) {
    String previous = OWNER.get();
    OWNER.set(handler);
    try {
      return action.get();
    } finally {
      if (previous == null) OWNER.remove();
      else OWNER.set(previous);
    }
  }
}
