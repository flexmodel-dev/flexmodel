package dev.flexmodel.common;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithUnnamedKey;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * @author cjbi
 */
@ConfigMapping(prefix = "flexmodel")
public interface FlexmodelConfig extends Serializable {

  String DEFAULT_SCHEMA_NAME = "system";

  String DEV_TEST_SCHEMA_NAME = "dev_test";

  @WithName("project-url-template")
  String projectUrlTemplate();

  @WithName("datasource")
  @WithUnnamedKey(DEFAULT_SCHEMA_NAME)
  Map<String, DatasourceConfig> datasources();

  @WithDefault("${quarkus.http.root-path}")
  String apiRootPath();

  interface DatasourceConfig {

    @WithName("db-kind")
    String dbKind();

    String url();

    Optional<String> username();

    Optional<String> password();
  }
}
