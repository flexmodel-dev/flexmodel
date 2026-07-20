package dev.flexmodel.common.authz;

import dev.flexmodel.common.SessionContext;
import jakarta.annotation.Priority;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Set;

/**
 * 权限校验过滤器。
 * <p>
 * 在所有认证过滤器之后运行（{@link Priorities#AUTHORIZATION}），
 * 读取资源方法 / 类上的 {@link RequiresPermissions} 注解，
 * 从 {@link SessionContext#getPermissions()} 获取当前调用方的权限集合，
 * 通过 {@link PermissionHelper#isPermitted(Set, String[], Logical)} 执行通配匹配。
 * <p>
 * 权限集合为 {@code null}（系统 JWT 或 API Key 认证且未配置项目级 Provider）
 * 时视为"未受限制"，放行；集合非空时严格校验。
 * 标注 {@link PermitAll} 的方法直接放行。
 *
 * @author cjbi
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class PermissionFilter implements ContainerRequestFilter {

  @Context
  ResourceInfo resourceInfo;

  @Inject
  SessionContext sessionContext;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    // @PermitAll → 放行
    if (resourceInfo.getResourceMethod().getAnnotation(PermitAll.class) != null) {
      return;
    }

    // 方法级注解优先，回退到类级
    RequiresPermissions rp = resourceInfo.getResourceMethod()
      .getAnnotation(RequiresPermissions.class);
    if (rp == null) {
      rp = resourceInfo.getResourceClass().getAnnotation(RequiresPermissions.class);
    }
    // 未标注 → 无需鉴权，放行
    if (rp == null) {
      return;
    }

    Set<String> permissions = sessionContext.getPermissions();
    // 未配置权限来源（系统 JWT / API Key 等非项目 Provider 认证）→ 放行
    if (permissions == null) {
      return;
    }

    if (!PermissionHelper.isPermitted(permissions, rp.value(), rp.logical())) {
      throw new ForbiddenException(
        "Permission denied, required: " + String.join(", ", rp.value()));
    }
  }
}
