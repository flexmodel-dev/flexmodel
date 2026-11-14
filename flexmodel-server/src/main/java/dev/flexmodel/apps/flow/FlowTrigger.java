package dev.flexmodel.apps.flow;

import dev.flexmodel.common.ValidationException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import org.quartz.CronExpression;

import static dev.flexmodel.common.Values.*;

/**
 * Trigger access and validation shared by publishing and runtime.
 */
public final class FlowTrigger {
  private FlowTrigger() {
  }

  public static Map<String, Object> of(Map<String, Object> flow) {
    return objects(object(flow.get("definition")).get("nodes")).stream()
      .filter(n -> "start".equals(text(n, "type"))).findFirst()
      .map(n -> object(AppFlowJson.config(n).get("trigger"))).orElseGet(LinkedHashMap::new);
  }

  public static void validate(Map<String, Object> cfg) {
    String type = text(cfg, "type");
    if (!Set.of("MANUAL", "DATA_EVENT", "SCHEDULE").contains(type))
      throw new ValidationException("请选择手动、数据事件或定时触发方式");
    if (!type.equals("SCHEDULE")) {
      if (text(cfg, "formName").isBlank()) throw new ValidationException("手动和数据事件触发必须绑定表单");
      if (type.equals("DATA_EVENT") && (strings(cfg.get("operations")).isEmpty()
        || !Set.of("CREATE", "UPDATE", "DELETE").containsAll(strings(cfg.get("operations")))))
        throw new ValidationException("请选择有效的数据事件类型");
      return;
    }
    if (cfg.containsKey("formName") || cfg.containsKey("record") || cfg.containsKey("input") || cfg.containsKey("conditions"))
      throw new ValidationException("定时触发不包含表单、记录、筛选条件或业务输入");
    if (!CronExpression.isValidExpression(text(cfg, "cron"))) throw new ValidationException("Cron 表达式无效");
    try {
      ZoneId.of(text(cfg, "timezone").isBlank() ? "Asia/Shanghai" : text(cfg, "timezone"));
      Instant start = text(cfg, "startAt").isBlank() ? null : Instant.parse(text(cfg, "startAt"));
      Instant end = text(cfg, "endAt").isBlank() ? null : Instant.parse(text(cfg, "endAt"));
      if (start != null && end != null && !start.isBefore(end)) throw new IllegalArgumentException();
    } catch (RuntimeException error) {
      throw new ValidationException("时区或执行起止时间无效");
    }
  }
}
