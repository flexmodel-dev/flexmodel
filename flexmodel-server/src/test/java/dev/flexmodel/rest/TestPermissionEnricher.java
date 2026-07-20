package dev.flexmodel.rest;

import dev.flexmodel.common.SessionContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试用权限注入过滤器。
 * <p>
 * 在认证（AUTHENTICATION=1000）之后、鉴权（AUTHORIZATION=2000）之前运行。
 * 读取请求头 {@code X-Test-Permissions}（逗号分隔的权限串），
 * 写入 {@link SessionContext#setPermissions(Set)}。
 * 不设置该头时不注入，保持系统 JWT 的 null 语义（全部放行）。
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 500) // 1500
public class TestPermissionEnricher implements ContainerRequestFilter {

  @Inject
  SessionContext sessionContext;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String header = requestContext.getHeaderString("X-Test-Permissions");
    if (header == null) {
      return;
    }
    if (header.isEmpty()) {
      sessionContext.setPermissions(Collections.emptySet());
      return;
    }
    Set<String> perms = Arrays.stream(header.split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toSet());
    sessionContext.setPermissions(perms.isEmpty() ? Collections.emptySet() : perms);
  }
}
