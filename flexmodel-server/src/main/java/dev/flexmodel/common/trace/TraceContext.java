package dev.flexmodel.common.trace;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.util.HexFormat;

@ApplicationScoped
public class TraceContext {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Inject
  jakarta.inject.Provider<CurrentTraceContext> currentTraceContextProvider;

  public TraceScope start(String projectId) {
    TraceScope scope = new TraceScope(generateHex(32), generateHex(16));
    currentTraceContextProvider.get().set(scope);
    return scope;
  }

  public TraceScope startChild(String traceId, String parentSpanId) {
    TraceScope scope = new TraceScope(traceId, generateHex(16), parentSpanId);
    currentTraceContextProvider.get().set(scope);
    return scope;
  }

  public String currentTraceId() {
    TraceScope scope = currentTraceContextProvider.get().get();
    return scope == null ? null : scope.traceId();
  }

  public String currentSpanId() {
    TraceScope scope = currentTraceContextProvider.get().get();
    return scope == null ? null : scope.spanId();
  }

  private static String generateHex(int characters) {
    byte[] bytes = new byte[characters / 2];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public record TraceScope(String traceId, String spanId, String parentSpanId) implements AutoCloseable {
    public TraceScope(String traceId, String spanId) {
      this(traceId, spanId, null);
    }

    public String traceParent() {
      return "00-%s-%s-01".formatted(traceId, spanId);
    }

    @Override
    public void close() {
      CurrentTraceContext currentTraceContext = TraceContextHolder.current();
      if (currentTraceContext != null && currentTraceContext.get() == this) {
        currentTraceContext.clear();
      }
    }
  }
}
