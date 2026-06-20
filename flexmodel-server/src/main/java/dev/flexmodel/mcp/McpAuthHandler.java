package dev.flexmodel.mcp;

import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.auth.service.ApiKeyService;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 端点认证处理器。
 * <p>
 * 在 Vert.x 路由层拦截 MCP 请求，通过 URL query 参数 {@code apikey} 进行 API Key 认证。
 * <p>
 * 客户端配置示例：
 * <pre>
 * {
 *   "mcpServers": {
 *     "flexmodel": {
 *       "url": "http://localhost:8080/api/mcp?apikey=fm_ak_xxxxx"
 *     }
 *   }
 * }
 * </pre>
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class McpAuthHandler {

  @Inject
  ApiKeyService apiKeyService;

  /**
   * 在 Router 启动时注册 MCP 认证拦截器，优先于 MCP Server 的路由执行。
   */
  public void init(@Observes StartupEvent startupEvent, Router router) {
    router.route("/mcp*").order(-1).handler(rc -> {
      String apikey = rc.request().getParam("api_key");
      if (apikey == null || apikey.isBlank()) {
        reject401(rc.response(), "Missing api_key parameter");
        return;
      }
      AuthApiKey apiKey = apiKeyService.validate(apikey);
      if (apiKey == null) {
        reject401(rc.response(), "Invalid API Key");
        return;
      }
      log.debug("MCP authenticated: key={}, path={}", apiKey.getName(), rc.request().path());
      rc.next();
    });
  }

  private void reject401(HttpServerResponse response, String message) {
    log.warn("MCP auth rejected: {}", message);
    response
      .setStatusCode(401)
      .putHeader("Content-Type", "application/json")
      .end("{\"error\":\"" + message + "\"}");
  }
}
