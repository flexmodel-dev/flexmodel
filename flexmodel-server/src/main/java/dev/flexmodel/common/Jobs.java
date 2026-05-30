package dev.flexmodel.common;

import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import dev.flexmodel.settings.SettingsService;
import dev.flexmodel.api.ApiRequestLogService;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.settings.Settings;

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

  @Scheduled(cron = "0 0 1 * * ?")
  void purgeOldLogs() {
    Settings settings = settingsService.getSettings();
    apiLogService.purgeOldLogs(settings.getLog().getMaxDays());
    jobExecutionLogService.purgeOldLogs(settings.getLog().getMaxDays());
  }

}
