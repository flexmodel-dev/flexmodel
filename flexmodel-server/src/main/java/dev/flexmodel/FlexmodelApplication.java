package dev.flexmodel;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import lombok.extern.slf4j.Slf4j;

/**
 * Flexmodel Quarkus 启动类。
 *
 * @author cjbi
 */
@Slf4j
@QuarkusMain
public class FlexmodelApplication implements QuarkusApplication {

  public static void main(String[] args) {
    Quarkus.run(FlexmodelApplication.class, args);
  }

  @Override
  public int run(String... args) throws Exception {
    log.info("========================================");
    log.info("  Flexmodel Server Starting...");
    log.info("========================================");
    Quarkus.waitForExit();
    return 0;
  }
}
