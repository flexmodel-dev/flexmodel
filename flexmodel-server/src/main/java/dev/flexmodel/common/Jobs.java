package dev.flexmodel.common;

import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import dev.flexmodel.settings.SettingsService;
import dev.flexmodel.api.ApiRequestLogService;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.settings.Settings;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.codegen.entity.Project;

/**
 * @author cjbi
 */
public class Jobs {

  @Inject
  SettingsService settingsService;
  @Inject
  ApiRequestLogService apiLogService;
  @Inject
  JobExecutionLogService jobExecutionLogService;
  @Inject
  ProjectService projectService;

  @Scheduled(cron = "0 0 1 * * ?")
  void purgeOldLogs() {
    Settings settings = settingsService.getSettings();
    int maxDays = settings.getLog().getMaxDays();
    for (Project project : projectService.findProjects()) {
      apiLogService.purgeOldLogs(project.getId(), maxDays);
    }
    jobExecutionLogService.purgeOldLogs(maxDays);
  }

}
