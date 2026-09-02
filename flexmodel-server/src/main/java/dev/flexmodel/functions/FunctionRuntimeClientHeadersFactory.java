package dev.flexmodel.functions;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import dev.flexmodel.common.trace.CurrentTraceContext;
import dev.flexmodel.common.trace.TraceContext;
import dev.flexmodel.common.trace.TraceContextHolder;

import java.util.List;

/**
 * Propagates all incoming JAX-RS request headers to the outgoing
 * Deno Functions Runtime REST client call, so that Edge Functions
 * can access the original client headers via their Request object.
 *
 * <p>Also injects runtime-internal headers (x-flexmodel-auth-token,
 * x-flexmodel-auth-token) that are set programmatically by
 * {@link FunctionInvoker} via @HeaderParam.
 *
 * <p>Hop-by-hop headers (host, content-length, transfer-encoding, connection)
 * are excluded as they are transport-level and should not be forwarded.
 *
 * <p>显式注入 W3C {@code traceparent} 头：从当前 OTel Span 上下文构造，
 * 确保 traceId 贯穿 Java→Deno 链路。自定义 {@code @RegisterClientHeaders}
 * 可能干扰 OTel 对 REST Client 的自动注入，此处作为兜底。
 *
 * @author cjbi
 */
@ApplicationScoped
public class FunctionRuntimeClientHeadersFactory implements ClientHeadersFactory {

  private static final List<String> EXCLUDED_HEADERS = List.of(
    "host", "content-length", "transfer-encoding", "connection",
    // accept-encoding 是传输协商头，透传会导致 Deno serve 自动 gzip/br 压缩响应，
    // 进而触发 Quarkus REST 客户端 @Consumes 严格匹配失败（content-type mismatch）。
    // Java↔Deno 是内部链路，不需要压缩；浏览器侧的压缩由上层服务处理。
    "accept-encoding",
    // traceparent 由本工厂从当前 Span 显式构造（见 injectTraceParent），
    // 不透传 incoming 的 traceparent（其 spanId 是上游调用方的，非当前服务端 span）。
    "traceparent"
  );

  @Override
  public MultivaluedMap<String, String> update(
    MultivaluedMap<String, String> incomingHeaders,
    MultivaluedMap<String, String> clientOutgoingHeaders) {

    // Propagate all incoming headers (from the original client request)
    for (String name : incomingHeaders.keySet()) {
      if (!EXCLUDED_HEADERS.contains(name.toLowerCase())) {
        clientOutgoingHeaders.put(name, incomingHeaders.get(name));
      }
    }

    // 显式注入 traceparent，确保 Deno 侧能提取 traceId 关联函数日志
    injectTraceParent(clientOutgoingHeaders);

    return clientOutgoingHeaders;
  }

  /**
   * 从当前 OTel Span 上下文构造 W3C traceparent 头并注入到 outgoing headers。
   * <p>格式: {@code 00-<traceId>-<spanId>-<traceFlags>}
   * <p>若 OTel 自动注入已设置 traceparent 则不覆盖。
   */
  private static void injectTraceParent(MultivaluedMap<String, String> outgoing) {
    if (outgoing.containsKey("traceparent")) {
      return; // OTel 自动注入已生效，不覆盖
    }
    try {
      CurrentTraceContext currentTraceContext = TraceContextHolder.current();
      TraceContext.TraceScope scope = currentTraceContext.get();
      if (scope != null) {
        outgoing.putSingle("traceparent", scope.traceParent());
      }
    } catch (Throwable ignored) {
      // 上下文不可用时静默跳过，不影响函数调用
    }
  }
}
