package dev.flexmodel.common;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 请求级别的会话上下文
 * <p>
 * 由 Quarkus CDI 管理生命周期：每次 HTTP 请求或手动激活的请求上下文会创建一个实例，
 * 上下文结束时自动销毁，无需手动 clear()。
 * <p>
 * 虚拟线程友好：CDI @RequestScoped 由 Quarkus 内部管理上下文激活/停用，
 *
 * @author cjbi
 */
@RequestScoped
@Getter
@Setter
public class SessionContext {

  private String projectId;
  private String projectDatabaseName;
  private String userId;

  /**
   * 当前调用方被授予的权限串集合，由认证环节（如项目级 OIDC/Function Provider）
   * 在认证成功后填充。为 null 或空表示未配置权限范围（按"全部范围"处理），
   * 交由后续鉴权逻辑决定放行策略。
   */
  private Set<String> permissions;

}
