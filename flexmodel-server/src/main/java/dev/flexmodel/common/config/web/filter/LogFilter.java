package dev.flexmodel.common.config.web.filter;

import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.settings.SettingsService;
import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.settings.Settings;
import dev.flexmodel.JsonUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author cjbi
 */
@Slf4j
@Provider
public class LogFilter implements ContainerRequestFilter, ContainerResponseFilter {

  @Inject
  SettingsService settingsService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    requestContext.setProperty("startTime", System.currentTimeMillis());
    try {
      byte[] bytes = requestContext.getEntityStream().readAllBytes();
      if (bytes.length > 0) {
        String body = new String(bytes);
        requestContext.setProperty("requestBody", body);
      }
      requestContext.setEntityStream(new ByteArrayInputStream(bytes));
      //This is your POST Body as String
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    Long beginTime = (Long) requestContext.getProperty("startTime");
    long execTime;
    if (beginTime != null) {
      execTime = System.currentTimeMillis() - beginTime;
    } else {
      execTime = -1L;
    }
    CompletableFuture.runAsync(() -> {
      Settings settings = settingsService.getSettings();
      boolean isLoggingEnabled = settings.getLog().isConsoleLoggingEnabled();
      boolean isLogRequest = requestContext.getUriInfo().getPath().startsWith("/logs");
      if (isLoggingEnabled && !isLogRequest) {
        saveLog(requestContext, responseContext, execTime);
      }
    });
  }

  private void saveLog(ContainerRequestContext requestContext, ContainerResponseContext responseContext, long execTime) {
    ApiRequestLog apiLog = new ApiRequestLog();
    apiLog.setHttpMethod(requestContext.getMethod());
    apiLog.setUrl(requestContext.getUriInfo().getRequestUri().toString());
    apiLog.setPath(apiLog.getHttpMethod() + " " + requestContext.getUriInfo().getPath());
    apiLog.setRequestHeaders(requestContext.getHeaders());
    apiLog.setRequestBody(requestContext.getProperty("requestBody"));
    apiLog.setIsSuccess(true);
    String projectId = Objects.toString(requestContext.getProperty("projectId"), null);
//      apiData.setRemoteIp(null);
    int statusCode = responseContext.getStatus();
    apiLog.setStatusCode(statusCode);
    String ipAddress = requestContext.getHeaderString("X-Forwarded-For");
    if (ipAddress == null) {
      ipAddress = requestContext.getUriInfo().getRequestUri().getHost(); // 或者使用其他方式获取 IP 地址
    }
    apiLog.setClientIp(ipAddress);
    if (statusCode >= 400) {
      apiLog.setIsSuccess(false);
      apiLog.setErrorMessage(JsonUtils.toJsonString(responseContext.getEntity()));
    }
    apiLog.setResponseTime((int) execTime);
    Map<String, Object> payload = new HashMap<>();
    payload.put("projectId", projectId);
    payload.put("log", apiLog);
    CDI.current().select(EventBus.class).get().send("request.logging", payload);
  }
}
