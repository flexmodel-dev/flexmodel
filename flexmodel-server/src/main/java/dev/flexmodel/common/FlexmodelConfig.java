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
  @WithDefault("localhost")
  String projectBaseDomain();

  /**
   * Routing mode: "path" or "subdomain".
   * In "path" mode, multi-tenant resources are accessed via path segments
   * (e.g., /pages/{projectId}, /functions/{projectId}/{name}).
   * In "subdomain" mode, they are accessed via subdomain
   * (e.g., {projectId}.{projectBaseDomain}, {projectId}.{projectBaseDomain}/functions/{name}).
   */
  @WithName("project-routing-mode")
  @WithDefault("path")
  String projectRoutingMode();

  /**
   * Derive the edge function URL based on routing mode and project base domain.
   * - path mode: /functions/{{projectId}}/{{name}}
   * - subdomain mode: https://{{projectId}}.{projectBaseDomain}/functions/{{name}}
   */
  default String edgeUrlTemplate() {
    if ("subdomain".equals(projectRoutingMode())) {
      return "https://{{projectId}}." + projectBaseDomain() + "/functions/{{name}}";
    }
    return "/functions/{{projectId}}/{{name}}";
  }

  /**
   * Derive the pages URL based on routing mode and project base domain.
   * - path mode: /pages/{{projectId}}
   * - subdomain mode: https://{{projectId}}.{projectBaseDomain}
   */
  default String pagesUrlTemplate() {
    if ("subdomain".equals(projectRoutingMode())) {
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
