package dev.flexmodel;

import dev.flexmodel.common.FlexmodelConfig;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;


/**
 * Flexmodel Quarkus 启动类。
 *
 * @author cjbi
 */
@Slf4j
@QuarkusMain
public class FlexmodelApplication implements QuarkusApplication {

  @Inject
  FlexmodelConfig flexmodelConfig;

  public static void main(String[] args) {
    Quarkus.run(FlexmodelApplication.class, args);
  }

  @Override
  public int run(String... args) throws Exception {
    log.info("========================================");
    log.info("  Flexmodel Server started!");
    log.info("========================================");

    printFlexmodelConfig();

    Quarkus.waitForExit();
    return 0;
  }

  private void printFlexmodelConfig() {
    log.info("---------- Flexmodel Configuration ----------");
    log.info("  project-url-template: {}", flexmodelConfig.projectUrlTemplate());
    log.info("  api-root-path: {}", flexmodelConfig.apiRootPath());
    if (flexmodelConfig.datasources() != null && !flexmodelConfig.datasources().isEmpty()) {
      flexmodelConfig.datasources().forEach((name, ds) -> {
        log.info("  datasource[{}]:", name);
        log.info("    db-kind: {}", ds.dbKind());
        log.info("    url: {}", ds.url());
        log.info("    username: {}", ds.username().orElse("<not set>"));
        log.info("    password: {}", ds.password().map(_ -> "***").orElse("<not set>"));
      });
    } else {
      log.info("  (no datasources configured)");
    }
  }

}
