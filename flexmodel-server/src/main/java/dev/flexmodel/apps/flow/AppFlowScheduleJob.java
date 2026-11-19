package dev.flexmodel.apps.flow;

import org.quartz.*;

import java.util.Map;

/**
 * The JobStore has already persisted the firing; execution only acknowledges the handoff.
 */
public class AppFlowScheduleJob implements Job {
  public void execute(JobExecutionContext context) {
    context.setResult(Map.of("status", "ACCEPTED", "scheduledAt", context.getScheduledFireTime().toInstant().toString()));
  }
}
