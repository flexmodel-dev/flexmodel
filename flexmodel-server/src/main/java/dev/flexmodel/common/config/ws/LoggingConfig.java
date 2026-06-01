package dev.flexmodel.common.config.ws;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import dev.flexmodel.common.Constants;

import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author cjbi
 */
@ApplicationScoped
public class LoggingConfig {
  void onStart(@Observes StartupEvent ev) {
    WebSocketLogHandler handler = new WebSocketLogHandler();
    handler.setFormatter(new SimpleFormatter());
    Logger appLog = Logger.getLogger(Constants.APP_LOG_CATEGORY_NAME);
    appLog.addHandler(handler);
    Logger defaultLogger = Logger.getLogger("");
    defaultLogger.addHandler(handler);
  }
}
