package dev.flexmodel.storage.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储提供者启动初始化器
 * <p>
 * 在 Quarkus 启动时读取配置并初始化 StorageBackend。
 * 未配置时默认使用本地存储（./storage）。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class StorageProviderInitializer {

  @Inject
  StorageProvider storageProvider;

  public void onStart(@Observes StartupEvent ev, StorageProviderConfig config) {
    log.info("Initializing storage provider: type={}, readOnly={}", config.type(), config.readOnly());
    try {
      storageProvider.initialize(config);
      log.info("Storage provider initialized successfully: {}", storageProvider.getBackend().getType());
    } catch (Exception e) {
      log.error("Failed to initialize storage provider", e);
      throw e;
    }
  }
}
