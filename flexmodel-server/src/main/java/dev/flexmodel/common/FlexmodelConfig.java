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
   * 空表示 path 模式；配置了真实域名则走 subdomain 模式。
   */
  @WithName("project-base-domain")
  Optional<String> projectBaseDomain();

  /**
   * 路由模式由 {@link #projectBaseDomain()} 自动推断，无需显式配置：
   * <ul>
   *   <li>配置了域名（非空）→ subdomain 模式</li>
   *   <li>未配置（空）→ path 模式</li>
   * </ul>
   */
  default boolean isSubdomainRouting() {
    return projectBaseDomain().map(d -> !d.isBlank()).orElse(false);
  }

  @WithName("datasource")
  @WithUnnamedKey(DEFAULT_SCHEMA_NAME)
  Map<String, DatasourceConfig> datasources();

  @WithDefault("${quarkus.http.root-path}")
  String apiRootPath();

  /**
   * 构建版本号，由 CI 在构建时通过 Maven 资源过滤注入（-Dflexmodel.version=...）。
   * 本地开发未指定时为 "dev"。
   */
  @WithName("version")
  @WithDefault("dev")
  String version();

  /**
   * 演示模式：开启后启动时才向 dev_test schema 写入表结构和种子数据。
   */
  @WithName("demo-mode")
  @WithDefault("false")
  boolean demoMode();

  @WithName("jwt")
  JwtConfig jwt();

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
