package dev.flexmodel;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.session.SessionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 测试数据初始化器
 * 在测试启动时加载dev_test项目的schema和数据
 *
 * @author cjbi
 */
@Singleton
@Slf4j
public class TestDataInitializer {

    private boolean initialized = false;

    void initTestData(@Observes StartupEvent event, SessionFactory sessionFactory) {
        if (initialized) {
            return;
        }

        try {
            log.info("Initializing test data for dev_test project...");

            // 加载dev_test的schema和数据（FML格式）
            try (InputStream fmlStream = getClass().getClassLoader()
                    .getResourceAsStream("dev_test.fml")) {
                if (fmlStream == null) {
                    log.warn("dev_test.fml not found, skipping test data initialization");
                    return;
                }
                String fmlContent = new String(fmlStream.readAllBytes(), StandardCharsets.UTF_8);
                sessionFactory.loadFMLString("dev_test", fmlContent);
                log.info("Loaded dev_test schema and data from FML");
            }

            initialized = true;
            log.info("Test data initialization completed");
        } catch (IOException e) {
            log.error("Failed to initialize test data", e);
            throw new RuntimeException("Failed to initialize test data", e);
        }
    }
}
