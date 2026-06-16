package dev.flexmodel.common.config.web.vertx;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

/**
 * Static resource handler for Quarkus webjar resources.
 * In production, the frontend is served independently by the flexmodel-ui container;
 * this handler remains for development convenience (quarkus:dev with -Pwith-ui).
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class StaticResourceHandler {

  public void handle(@Observes StartupEvent startupEvent, Router router) {
    // Static resources are now served by flexmodel-ui container in production.
    // The /flexmodel-ui/* reroute to webjars is no longer needed since the
    // frontend base path has been changed from /flexmodel-ui to /.
  }

}
