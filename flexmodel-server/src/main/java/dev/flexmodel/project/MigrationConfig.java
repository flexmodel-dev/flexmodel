package dev.flexmodel.project;

import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.SchemaObject;

import java.util.Map;

/**
 * 解析 FML 中 {@code @migration(enabled: false)} 等迁移行为注解。
 * <p>
 * 注解由 {@code ASTNodeConverter} 存入 {@code additionalProperties["migration"]}：
 * 无参数注解存为 {@code true}，带参数注解存为参数 Map（值为 String）。
 * <p>
 * 默认语义：未标 {@code @migration} 的模型全量迁移（向后兼容现有行为）。
 * {@code limit} 为预留字段，v1 不消费。
 *
 * @author cjbi
 */
public class MigrationConfig {

  private final boolean enabled;
  private final Integer limit;

  private MigrationConfig(boolean enabled, Integer limit) {
    this.enabled = enabled;
    this.limit = limit;
  }

  /**
   * 默认配置：全量迁移。
   */
  public static MigrationConfig defaults() {
    return new MigrationConfig(true, null);
  }

  /**
   * 从模型的扩展属性解析迁移配置。
   *
   * @param model 模型定义，可为 null
   * @return 迁移配置；模型为 null 或未标记 @migration 时返回默认（全量迁移）
   */
  public static MigrationConfig of(SchemaObject model) {
    if (model == null) {
      return defaults();
    }
    Map<String, Object> additionalProperties;
    if (model instanceof EntityDefinition e) {
      additionalProperties = e.getAdditionalProperties();
    } else if (model instanceof EnumDefinition en) {
      additionalProperties = en.getAdditionalProperties();
    } else {
      return defaults();
    }
    Object value = additionalProperties.get("migration");
    if (value == null) {
      return defaults();
    }
    // 无参数标记注解：@migration → true（启用迁移）
    if (value instanceof Boolean b) {
      return new MigrationConfig(b, null);
    }
    // 带参数注解：@migration(enabled: false, limit: 100) → Map
    if (value instanceof Map<?, ?> map) {
      boolean enabled = true;
      Integer limit = null;
      Object enabledValue = map.get("enabled");
      if (enabledValue != null) {
        enabled = Boolean.parseBoolean(enabledValue.toString());
      }
      Object limitValue = map.get("limit");
      if (limitValue != null) {
        try {
          limit = Integer.parseInt(limitValue.toString());
        } catch (NumberFormatException ignored) {
          // 非法 limit 值忽略，保持 null
        }
      }
      return new MigrationConfig(enabled, limit);
    }
    return defaults();
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 迁移记录数上限，null 表示不限。v1 预留，暂不消费。
   */
  public Integer getLimit() {
    return limit;
  }
}
