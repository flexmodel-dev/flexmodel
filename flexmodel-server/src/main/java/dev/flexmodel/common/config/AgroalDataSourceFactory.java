package dev.flexmodel.common.config;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import org.sqlite.SQLiteDataSource;

import java.sql.SQLException;
import java.time.Duration;

/**
 * Agroal 连接池工厂，统一管理 DataSource 的创建与池化参数配置。
 * <p>
 * 在 native image 中，通过 {@link AgroalConnectionFactoryConfigurationSupplier#connectionProviderClass(Class)}
 * 直接指定厂商 DataSource 类，由 Agroal 内部实例化并调用其 getConnection()，
 * 完全绕过 DriverManager SPI，避免 GraalVM 反射缺失问题。
 *
 * @author cjbi
 */
public final class AgroalDataSourceFactory {

  private AgroalDataSourceFactory() {
  }

  /**
   * 创建标准配置的 AgroalDataSource，用于应用正常运行时数据库访问。
   */
  public static AgroalDataSource createDataSource(String url, String username, String password) {
    EngineConfig.ensureSqliteParentDir(url);
    return buildDataSource(url, username, password,
      Duration.ofMinutes(10),   // maxLifetime
      Duration.ofMinutes(5)     // reapTimeout (idle)
    );
  }

  /**
   * 创建系统管理用的短存活 AgroalDataSource，用于 Schema DDL 操作。
   */
  public static AgroalDataSource createSystemDataSource(String url, String username, String password) {
    EngineConfig.ensureSqliteParentDir(url);
    return buildDataSource(url, username, password,
      Duration.ofSeconds(30),   // maxLifetime (短存活)
      Duration.ofMinutes(5)     // reapTimeout
    );
  }

  private static AgroalDataSource buildDataSource(String url, String username, String password,
                                                  Duration maxLifetime, Duration reapTimeout) {
    String normalizedUrl = normalizeSqliteUrl(url);
    Class<?> providerClass = resolveProviderClass(normalizedUrl);

    AgroalDataSourceConfigurationSupplier config = new AgroalDataSourceConfigurationSupplier();
    AgroalConnectionPoolConfigurationSupplier poolConfig = config.connectionPoolConfiguration();
    AgroalConnectionFactoryConfigurationSupplier factoryConfig = poolConfig.connectionFactoryConfiguration();

    factoryConfig.jdbcUrl(normalizedUrl);
    if (providerClass != null) {
      factoryConfig.connectionProviderClass(providerClass);
    }
    if (username != null && !username.isEmpty()) {
      factoryConfig.principal(new NamePrincipal(username));
    }
    if (password != null && !password.isEmpty()) {
      factoryConfig.credential(new SimplePassword(password));
    }

    // 连接池参数
    poolConfig.maxSize(100);
    poolConfig.minSize(0);
    poolConfig.initialSize(0);
    poolConfig.acquisitionTimeout(Duration.ofSeconds(10));   // connectionTimeout
    poolConfig.maxLifetime(maxLifetime);
    poolConfig.reapTimeout(reapTimeout);                     // idleTimeout
    poolConfig.leakTimeout(Duration.ofSeconds(60));          // leakDetectionThreshold
    poolConfig.validationTimeout(Duration.ofSeconds(3));     // validationTimeout

    try {
      return AgroalDataSource.from(config);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create AgroalDataSource: " + e.getMessage(), e);
    }
  }

  /**
   * 根据 JDBC URL 返回对应的厂商 DataSource 类。
   * <p>
   * 在 native image 中，通过 connectionProviderClass 直接提供 DataSource 类，
   * Agroal 会使用 DataSource 模式（而非 Driver 模式），由 PropertyInjector
   * 反射注入 url/user/password 等属性，完全绕过 DriverManager SPI。
   *
   * @return DataSource 类，或 null（回退到 Agroal 默认 DriverManager 方式）
   */
  static Class<?> resolveProviderClass(String url) {
    if (url == null) {
      return null;
    }
    if (url.startsWith("jdbc:sqlite:")) {
      return SQLiteDataSource.class;
    }
    if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
      return MysqlDataSource.class;
    }
    return null;
  }

  /**
   * 规范化 SQLite JDBC URL，注入并发写所需的 PRAGMA 参数。
   * <p>
   * SQLite 默认使用 rollback journal，同一时刻只允许一个写连接，
   * 连接池（maxSize=10）下并发写会立即抛出 {@code SQLITE_BUSY}。
   * 通过 URL 参数：
   * <ul>
   *   <li>{@code busy_timeout} —— 写锁被占用时让驱动等待重试，而非立即失败</li>
   *   <li>{@code journal_mode=WAL} —— 允许读连接与写连接并发，显著降低锁冲突</li>
   * </ul>
   * 若用户已在 URL 中显式指定同名参数，则保持用户配置不覆盖。
   */
  static String normalizeSqliteUrl(String url) {
    if (url == null || !url.startsWith("jdbc:sqlite:")) {
      return url;
    }
    url = appendParamIfAbsent(url, "busy_timeout", "5000");
    url = appendParamIfAbsent(url, "journal_mode", "WAL");
    return url;
  }

  private static String appendParamIfAbsent(String url, String key, String value) {
    String param = key + "=" + value;
    int queryIdx = url.indexOf('?');
    if (queryIdx < 0) {
      return url + "?" + param;
    }
    for (String p : url.substring(queryIdx + 1).split("&")) {
      String existingKey = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
      if (existingKey.equalsIgnoreCase(key)) {
        return url;
      }
    }
    return url + "&" + param;
  }
}
