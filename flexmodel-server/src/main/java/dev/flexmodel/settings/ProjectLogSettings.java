package dev.flexmodel.settings;

import dev.flexmodel.codegen.entity.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目级日志设置解析器：从 {@link Project#getMetadata()} 的 {@code logSettings} 节点读取
 * 日志保留天数与审计资源白名单，未配置时回退到默认值。
 * <p>
 * metadata 结构示例：
 * <pre>
 * {
 *   "logSettings": {
 *     "logRetentionDays": 7,
 *     "auditResources": ["f_trigger", "f_function", ...]
 *   }
 * }
 * </pre>
 *
 * @author cjbi
 */
public final class ProjectLogSettings {

  /**
   * 默认日志保留天数（天）。
   */
  public static final int DEFAULT_LOG_RETENTION_DAYS = 7;

  /**
   * 默认审计资源白名单：仅这些配置定义表的增删改记审计日志。
   */
  public static final List<String> DEFAULT_AUDIT_RESOURCES = List.of(
    "f_trigger",
    "f_em_flow_definition",
    "f_em_flow_deployment",
    "f_function",
    "f_bucket",
    "f_auth_provider_config"
  );

  private ProjectLogSettings() {
  }

  /**
   * 解析项目级日志保留天数，未配置或非法时回退到 {@link #DEFAULT_LOG_RETENTION_DAYS}。
   */
  public static int logRetentionDays(Project project) {
    Map<String, Object> logSettings = logSettingsNode(project);
    Object value = logSettings.get("logRetentionDays");
    if (value instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    return DEFAULT_LOG_RETENTION_DAYS;
  }

  /**
   * 解析项目级审计资源白名单，未配置或为空时回退到 {@link #DEFAULT_AUDIT_RESOURCES}。
   */
  public static Set<String> auditResources(Project project) {
    Map<String, Object> logSettings = logSettingsNode(project);
    Object value = logSettings.get("auditResources");
    if (value instanceof List<?> list && !list.isEmpty()) {
      List<String> resources = new ArrayList<>();
      for (Object item : list) {
        if (item != null) {
          resources.add(String.valueOf(item));
        }
      }
      if (!resources.isEmpty()) {
        return Set.copyOf(resources);
      }
    }
    return Set.copyOf(DEFAULT_AUDIT_RESOURCES);
  }

  /**
   * 安全提取 metadata 中的 logSettings 节点，缺失时返回空 Map。
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> logSettingsNode(Project project) {
    if (project == null) {
      return Map.of();
    }
    Object metadata = project.getMetadata();
    if (metadata instanceof Map<?, ?> map) {
      Object logSettings = map.get("logSettings");
      if (logSettings instanceof Map<?, ?> settings) {
        return (Map<String, Object>) settings;
      }
    }
    return Map.of();
  }
}
