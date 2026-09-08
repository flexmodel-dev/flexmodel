package dev.flexmodel.common.trace;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.HexFormat;

@ApplicationScoped
public class TraceContext {

  /**
   * 启动根 span 并在作用域内执行 body。生成新的 traceId/spanId，
   * 仅在 body 执行期间通过 {@link ScopedValue} 激活，退出自动清理。
   *
   * @param projectId 归属项目（用于后续按项目维度检索链路，当前未写入 span）
   * @param body      需在链路上下文内执行的逻辑
   */
  public void start(String projectId, Runnable body) {
    ScopedValue.where(TraceContextHolder.CURRENT, createScope()).run(body);
  }

  /**
   * 以已有 traceId 恢复链路上下文（如 EventBus 跨线程消费端），
   * 生成新的 spanId 作为子 span，在作用域内执行 body。
   */
  public void startChild(String traceId, String parentSpanId, Runnable body) {
    ScopedValue.where(TraceContextHolder.CURRENT,
      new TraceScope(traceId, generateHex(16), parentSpanId)).run(body);
  }

  /**
   * 生成根 TraceScope 但不绑定。供无法用回调包裹的入口（如 Quartz 在 listener 与 execute 之间
   * 分阶段操作）使用：调用方生成 scope 后，在实际执行点通过 {@link TraceContextHolder#with} 绑定。
   */
  public TraceScope createScope() {
    return new TraceScope(generateHex(32), generateHex(16));
  }

  public String currentTraceId() {
    TraceScope scope = TraceContextHolder.current();
    return scope == null ? null : scope.traceId();
  }

  public String currentSpanId() {
    TraceScope scope = TraceContextHolder.current();
    return scope == null ? null : scope.spanId();
  }

  private static String generateHex(int characters) {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[characters / 2];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public record TraceScope(String traceId, String spanId, String parentSpanId) {
    public TraceScope(String traceId, String spanId) {
      this(traceId, spanId, null);
    }

    public String traceParent() {
      return "00-%s-%s-01".formatted(traceId, spanId);
    }
  }
}
