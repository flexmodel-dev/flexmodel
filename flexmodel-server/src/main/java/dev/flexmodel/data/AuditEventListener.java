package dev.flexmodel.data;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.settings.ProjectLogSettings;
import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.event.EventListener;
import dev.flexmodel.common.trace.TraceContext;
import dev.flexmodel.project.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志监听器：监听引擎后置数据变更事件，对配置定义类表的增删改记录审计日志。
 * <p>
 * 与 {@link dev.flexmodel.realtime.RealtimeEventListener} 同构，仅订阅后置事件且仅在操作成功时记录。
 * 通过白名单过滤，只审计配置定义表（trigger/flow/function 等），不审计运行实例与日志表，避免量过大与重复。
 * 监听器内自行从 {@link SessionContext} 补充 userId、从 {@link TraceContext} 补充 traceId，
 * 引擎事件本身不携带审计关注点。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class AuditEventListener implements EventListener {

  @Inject
  AuditLogRepository auditLogRepository;

  @Inject
  ProjectRepository projectRepository;

  @Inject
  TraceContext traceContext;

  @Override
  public void onChanged(ChangedEvent event) {
    if (!event.isSuccess()) {
      return;
    }
    String modelName = event.getModelName();
    try {
      // schemaName 为项目数据库名，反查 projectId
      Project project = projectRepository.findProjectByDatabaseName(event.getSchemaName());
      if (project == null) {
        log.debug("Audit skip: project not found for schemaName={}", event.getSchemaName());
        return;
      }
      // 审计资源白名单按项目级 metadata.logSettings.auditResources 解析，未配置回退默认
      if (!ProjectLogSettings.auditResources(project).contains(modelName)) {
        return;
      }
      String projectId = project.getId();

      AuditLog auditLog = new AuditLog();
      auditLog.setAction(event.getEventType());                  // INSERTED / UPDATED / DELETED
      auditLog.setResourceType(modelName);
      auditLog.setResourceId(String.valueOf(event.getId()));
      auditLog.setSuccess(true);

      // 从新数据中提取资源名称（优先 name/title 字段）
      Map<String, Object> newData = event.getNewData();
      if (newData != null) {
        auditLog.setNewData(JsonUtils.toJsonString(newData));
        Object name = newData.get("name");
        if (name == null) {
          name = newData.get("title");
        }
        if (name != null) {
          auditLog.setResourceName(String.valueOf(name));
        }
      }
      Map<String, Object> oldData = event.getOldData();
      if (oldData != null && !oldData.isEmpty()) {
        auditLog.setOldData(JsonUtils.toJsonString(oldData));
      }

      // 补充操作人与链路（引擎事件不携带这些关注点，由监听器自行丰富）
      try {
        SessionContext ctx = CDI.current().select(SessionContext.class).get();
        auditLog.setUserId(ctx.getUserId());
      } catch (Exception ignored) {
        // 非请求上下文（如定时任务）时无法获取 userId，留空
      }
      try {
        auditLog.setTraceId(traceContext.currentTraceId());
      } catch (Exception ignored) {
      }

      auditLog.setCreatedAt(LocalDateTime.now());
      auditLogRepository.save(projectId, auditLog);
    } catch (Exception e) {
      // failsafe：审计失败不阻断业务，与 RealtimeEventListener 理念一致
      log.error("Failed to record audit log: model={}, eventType={}", modelName, event.getEventType(), e);
    }
  }

  @Override
  public boolean supports(String eventType) {
    return "INSERTED".equals(eventType)
      || "UPDATED".equals(eventType)
      || "DELETED".equals(eventType);
  }

  @Override
  public int getOrder() {
    return 2000; // 低优先级，最后执行，不影响业务逻辑
  }
}
