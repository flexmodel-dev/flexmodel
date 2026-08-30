package dev.flexmodel.common;

import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import dev.flexmodel.observability.api.ApiRequestLogService;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.settings.ProjectObservabilitySettings;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.codegen.entity.Project;

/**
 * @author cjbi
 */
public class Jobs {

  @Inject
  ApiRequestLogService apiLogService;
  @Inject
  JobExecutionLogService jobExecutionLogService;
  @Inject
  ProjectService projectService;

  @Scheduled(cron = "0 0 1 * * ?")
  void purgeOldLogs() {
    for (Project project : projectService.findProjects()) {
      int maxDays = ProjectObservabilitySettings.logRetentionDays(project);
      apiLogService.purgeOldLogs(project.getId(), maxDays);
    }
    // 作业执行日志为平台级全局表，按默认保留天数清理
    jobExecutionLogService.purgeOldLogs(ProjectObservabilitySettings.DEFAULT_LOG_RETENTION_DAYS);
  }

}
