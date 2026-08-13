package dev.flexmodel.common;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithUnnamedKey;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * @author cjbi
 */
@ConfigMapping(prefix = "flexmodel")
public interface FlexmodelConfig extends Serializable {

  String DEFAULT_SCHEMA_NAME = "system";

  @WithName("project-url-template")
  String projectUrlTemplate();

  /**
   * Project base domain, used for CORS and URL construction in subdomain routing mode.
   */
  @WithName("project-base-domain")
  String projectBaseDomain();

  /**
   * 路由模式由 {@link #projectBaseDomain()} 自动推断，无需显式配置：
   * <ul>
   *   <li>配置了域名（非空）→ subdomain 模式</li>
   *   <li>未配置（空）→ path 模式</li>
   * </ul>
   */
  default boolean isSubdomainRouting() {
    String domain = projectBaseDomain();
    return domain != null && !domain.isBlank();
  }

  /**
   * 推导边缘函数调用 URL（统一经 Java 代理端点 /open/{projectId}/functions/{name}/invoke）。
   * - path 模式: /api/open/{{projectId}}/functions/{{name}}/invoke
   * - subdomain 模式: https://{{projectId}}.{projectBaseDomain}/api/open/{{projectId}}/functions/{{name}}/invoke
   */
  default String edgeUrlTemplate() {
    if (isSubdomainRouting()) {
      return "https://{{projectId}}." + projectBaseDomain()
        + "/api/open/{{projectId}}/functions/{{name}}/invoke";
    }
    return "/api/open/{{projectId}}/functions/{{name}}/invoke";
  }

  /**
   * 推导 Pages 站点 URL。
   * - path 模式: /pages/{{projectId}}
   * - subdomain 模式: https://{{projectId}}.{projectBaseDomain}（子域名根即站点）
   */
  default String pagesUrlTemplate() {
    if (isSubdomainRouting()) {
      return "https://{{projectId}}." + projectBaseDomain();
    }
    return "/pages/{{projectId}}";
  }

  @WithName("datasource")
  @WithUnnamedKey(DEFAULT_SCHEMA_NAME)
  Map<String, DatasourceConfig> datasources();

  @WithDefault("${quarkus.http.root-path}")
  String apiRootPath();

  @WithName("jwt")
  JwtConfig jwt();

  @WithName("pages")
  PagesConfig pages();

  interface PagesConfig {

    @WithName("root-path")
    @WithDefault("./pages")
    String rootPath();
  }

  interface DatasourceConfig {

    @WithName("db-kind")
    String dbKind();

    String url();

    Optional<String> username();

    Optional<String> password();
  }

  interface JwtConfig {

    @WithDefault("storewebkey")
    String secret();

    @WithDefault("7d")
    Duration accessTokenLifetime();

    @WithDefault("30d")
    Duration refreshTokenLifetime();
  }
}
