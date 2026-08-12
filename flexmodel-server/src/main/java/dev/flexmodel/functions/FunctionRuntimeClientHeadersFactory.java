package dev.flexmodel.functions;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import java.util.List;

/**
 * Propagates all incoming JAX-RS request headers to the outgoing
 * Deno Functions Runtime REST client call, so that Edge Functions
 * can access the original client headers via their Request object.
 *
 * <p>Also injects runtime-internal headers (x-flexmodel-auth-token,
 * x-flexmodel-invoke-id) that are set programmatically by
 * {@link FunctionInvoker} via @HeaderParam.
 *
 * <p>Hop-by-hop headers (host, content-length, transfer-encoding, connection)
 * are excluded as they are transport-level and should not be forwarded.
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
    "accept-encoding"
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

    return clientOutgoingHeaders;
  }
}
