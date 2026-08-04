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

  // blocking = true: JDBC 写入必须在工作线程（virtual thread）上执行，否则会阻塞 Vert.x 事件循环
  @ConsumeEvent(value = "request.logging", blocking = true) // 监听特定地址的事件
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
