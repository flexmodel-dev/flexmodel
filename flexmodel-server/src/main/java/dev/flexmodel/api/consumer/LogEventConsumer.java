package dev.flexmodel.api.consumer;

import dev.flexmodel.api.ApiRequestLogService;
import dev.flexmodel.codegen.entity.ApiRequestLog;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * @author cjbi
 */
@ApplicationScoped
public class LogEventConsumer {

  @Inject
  ApiRequestLogService apiLogService;

  @ConsumeEvent("request.logging") // 监听特定地址的事件
  public void consume(Map<String, Object> payload) {
    String projectId = (String) payload.get("projectId");
    ApiRequestLog apiLog = (ApiRequestLog) payload.get("log");
    try {
      apiLogService.create(projectId, apiLog);
    } catch (Exception _) {
      // fire and forget
    }
  }

}
