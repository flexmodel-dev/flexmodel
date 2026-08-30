package dev.flexmodel.observability;

import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.flow.repository.NodeInstanceLogRepository;
import dev.flexmodel.observability.api.ApiRequestLogService;
import dev.flexmodel.observability.audit.AuditLogService;
import dev.flexmodel.observability.function.FunctionLogService;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.settings.ProjectObservabilitySettings;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * 可观测性日志定时清理作业。
 * <p>
 * 按项目级可观测性设置（{@link ProjectObservabilitySettings#logRetentionDays}）解析每个项目的日志保留天数，
 * 逐项目清理以下六类可观测性日志：Span、接口日志、函数日志、任务执行日志、节点执行日志、审计日志。
 * Span 存储在平台级系统库（按 project_id 过滤），其余存储在项目库。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class ObservabilityLogPurgeJob {

  @Inject
  ApiRequestLogService apiLogService;
  @Inject
  FunctionLogService functionLogService;
  @Inject
  JobExecutionLogService jobExecutionLogService;
  @Inject
  SpanRepository spanRepository;
  @Inject
  AuditLogService auditLogService;
  @Inject
  NodeInstanceLogRepository nodeInstanceLogRepository;
  @Inject
  ProjectService projectService;

  @Scheduled(cron = "0 0 1 * * ?")
  void purgeOldLogs() {
    for (Project project : projectService.findProjects()) {
      String projectId = project.getId();
      int maxDays = ProjectObservabilitySettings.logRetentionDays(project);
      purgeProject(projectId, maxDays);
    }
  }

  /**
   * 清理单个项目的全部可观测性日志，任一类失败不中断其余清理。
   */
  private void purgeProject(String projectId, int maxDays) {
    log.info("Purging observability logs older than {} days for project {}", maxDays, projectId);
    purge("span", projectId, () -> spanRepository.purgeOldLogs(projectId, maxDays));
    purge("api log", projectId, () -> apiLogService.purgeOldLogs(projectId, maxDays));
    purge("function log", projectId, () -> functionLogService.purgeOldLogs(projectId, maxDays));
    purge("job execution log", projectId, () -> jobExecutionLogService.purgeOldLogs(projectId, maxDays));
    purge("node instance log", projectId, () -> nodeInstanceLogRepository.purgeOldLogs(projectId, maxDays));
    purge("audit log", projectId, () -> auditLogService.purgeOldLogs(projectId, maxDays));
  }

  /**
   * failsafe 包装：单类日志清理失败仅记录，不阻断后续清理，与审计/实时监听器理念一致。
   */
  private void purge(String name, String projectId, Runnable task) {
    try {
      task.run();
    } catch (Exception e) {
      log.error("Failed to purge {} for project {}", name, projectId, e);
    }
  }

}
