package dev.flexmodel.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * OTel 手动 span 创建工具。
 * <p>
 * Quartz Job 等非 HTTP 入口没有自动 span 上下文，需手动创建根 span 使 traceId 贯穿下游。
 * 设置 {@code flexmodel.project_id} 属性，供 {@link FmSpanExporter} 提取 projectId 落库。
 *
 * @author cjbi
 */
@ApplicationScoped
public class TracingHelper {

  public static final String PROJECT_ID_ATTR = "flexmodel.project_id";

  @Inject
  Tracer tracer;

  /**
   * 创建并激活一个根 span，返回包含 traceId 的 scope（try-with-resources）。
   */
  public SpanScope startSpan(String spanName, String projectId) {
    Span span = tracer.spanBuilder(spanName)
      .setAttribute(PROJECT_ID_ATTR, projectId)
      .startSpan();
    Scope scope = span.makeCurrent();
    return new SpanScope(span, scope);
  }

  /**
   * 获取当前 active span 的 traceId，无有效上下文时返回 null。
   * 用于手动触发（API/事件）场景关联已有 HTTP span。
   */
  public String currentTraceId() {
    SpanContext ctx = Span.current().getSpanContext();
    return ctx.isValid() ? ctx.getTraceId() : null;
  }

  /**
   * 获取当前 active span 的 spanId，无有效上下文时返回 null。
   * 与 {@link #currentTraceId()} 配合，用于手动触发场景向 EventBus 传播链路上下文。
   */
  public String currentSpanId() {
    SpanContext ctx = Span.current().getSpanContext();
    return ctx.isValid() ? ctx.getSpanId() : null;
  }

  /**
   * 从远程 traceId 恢复 span 上下文，创建并激活一个子 span。
   * <p>
   * 用于跨线程/EventBus 传播后，消费端恢复链路。
   */
  public SpanScope startChildSpan(String spanName, String traceId, String parentSpanId, String projectId) {
    SpanContext parentCtx = SpanContext.createFromRemoteParent(
      traceId, parentSpanId, TraceFlags.getDefault(), TraceState.getDefault());
    Span span = tracer.spanBuilder(spanName)
      .setParent(Context.root().with(Span.wrap(parentCtx)))
      .setAttribute(PROJECT_ID_ATTR, projectId)
      .startSpan();
    Scope scope = span.makeCurrent();
    return new SpanScope(span, scope);
  }

  /**
   * AutoCloseable span scope，关闭时 end span + 关闭 scope。
   */
  public static final class SpanScope implements AutoCloseable {
    private final Span span;
    private final Scope scope;

    SpanScope(Span span, Scope scope) {
      this.span = span;
      this.scope = scope;
    }

    public String traceId() {
      return span.getSpanContext().getTraceId();
    }

    public String spanId() {
      return span.getSpanContext().getSpanId();
    }

    public void recordException(Throwable t) {
      span.recordException(t);
      span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, t.getMessage());
    }

    @Override
    public void close() {
      scope.close();
      span.end();
    }
  }
}
