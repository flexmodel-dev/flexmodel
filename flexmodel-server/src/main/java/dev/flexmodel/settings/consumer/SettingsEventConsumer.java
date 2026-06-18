package dev.flexmodel.settings.consumer;

import dev.flexmodel.settings.SettingsChanged;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class SettingsEventConsumer {
  @ConsumeEvent("settings-changed") // 监听特定地址的事件
  public void consume(SettingsChanged event) {
    log.info("Received settings message: {}", event.getMessage());
    // 处理设置变更事件
  }

}
