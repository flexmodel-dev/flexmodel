package dev.flexmodel.api.consumer;

import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.api.ApiRequestLogService;

import java.util.Map;

/**
 * @author cjbi
 */
@ApplicationScoped
public class LogEventConsumer {

  @Inject
  ApiRequestLogService apiLogService;

  @SuppressWarnings("unchecked")
  @ConsumeEvent("request.logging") // 监听特定地址的事件
  public void consume(Map<String, Object> payload) {
    String projectId = (String) payload.get("projectId");
    ApiRequestLog apiLog = (ApiRequestLog) payload.get("log");
    apiLogService.create(projectId, apiLog);
  }

}
