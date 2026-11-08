package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.codegen.enumeration.TriggerType;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.messaging.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.quartz.*;

import java.time.*;
import java.util.*;

import static dev.flexmodel.codegen.System.trigger;
import static dev.flexmodel.common.Values.*;
import static dev.flexmodel.query.Expressions.*;

/**
 * Published trigger projection. All mutations are owned by the flow publication transaction.
 */
@ApplicationScoped
public class AppFlowBindings extends AbstractRepository implements ReliableMessageHandler {
  @Inject
  AppFlowDeploymentRepository deployments;
  @Inject
  ReliableMessages messages;
  @Inject
  Scheduler scheduler;

  public Trigger find(String app, String flow) {
    try (var s = openSession()) {
      return s.dsl().selectFrom(Trigger.class).where(trigger.projectId.eq(app).and(trigger.managedKey.eq(flow))).executeOne();
    }
  }

  public List<Trigger> active(String app) {
    try (var s = openSession()) {
      return s.dsl().selectFrom(Trigger.class).where(trigger.projectId.eq(app).and(trigger.jobType.eq("WORKFLOW")).and(trigger.state.eq(true))).execute();
    }
  }

  public void bind(String app, String flow, String deployment, boolean enabled) {
    var cfg = FlowTrigger.of(deployments.deployedFlow(app, deployment));
    Trigger previous = find(app, flow), value = previous == null ? new Trigger() : previous;
    value.setId(previous == null ? UUID.randomUUID().toString() : previous.getId());
    value.setProjectId(app);
    value.setManagedKey(flow);
    value.setDeploymentId(deployment);
    value.setGeneration(previous == null ? 1 : previous.getGeneration() + 1);
    value.setJobId(flow);
    value.setJobType("WORKFLOW");
    value.setJobGroup("workflow_" + app);
    value.setName(flow);
    value.setState(enabled);
    value.setConfig(cfg);
    value.setType(switch (text(cfg, "type")) {
      case "SCHEDULE" -> TriggerType.SCHEDULE;
      case "DATA_EVENT" -> TriggerType.EVENT;
      default -> TriggerType.MANUAL;
    });
    try (var s = openSession()) {
      s.dsl().mergeInto(Trigger.class).values(value).execute();
    }
    messages.enqueue(app, "schedule-sync:" + value.getId() + ":" + value.getGeneration(), type(), Map.of("flowId", flow));
  }

  public void remove(String app, String flow) {
    Trigger old = find(app, flow);
    if (old == null) return;
    try (var s = openSession()) {
      s.dsl().deleteFrom(Trigger.class).where(trigger.projectId.eq(app).and(trigger.id.eq(old.getId()))).execute();
    }
    messages.enqueue(app, "schedule-delete:" + old.getId() + ":" + UUID.randomUUID(), type(), Map.of("flowId", flow, "deletedTriggerId", old.getId()));
  }

  public String type() {
    return "flow.schedule.sync";
  }

  public void handle(String app, String message, Map<String, Object> payload) {
    try {
      Trigger value = find(app, text(payload, "flowId"));
      if (value == null) {
        scheduler.deleteJob(JobKey.jobKey("workflow-" + text(payload, "deletedTriggerId"), "workflow_" + app));
      } else sync(value);
    } catch (SchedulerException e) {
      throw new IllegalStateException("同步流程调度失败", e);
    }
  }

  public void sync(Trigger value) throws SchedulerException {
    String app = value.getProjectId();
    var key = JobKey.jobKey("workflow-" + value.getId(), "workflow_" + app);
    var existing = scheduler.getJobDetail(key);
    if (Boolean.TRUE.equals(value.getState()) && value.getType() == TriggerType.SCHEDULE && existing != null && existing.getJobDataMap().getInt("generation") == value.getGeneration())
      return;
    scheduler.deleteJob(key);
    if (!Boolean.TRUE.equals(value.getState()) || value.getType() != TriggerType.SCHEDULE) return;
    var cfg = object(value.getConfig());
    var job = JobBuilder.newJob(AppFlowScheduleJob.class).withIdentity(key)
      .usingJobData("projectId", app).usingJobData("jobId", value.getJobId()).usingJobData("jobType", "WORKFLOW")
      .usingJobData("jobGroup", value.getJobGroup()).usingJobData("triggerId", value.getId())
      .usingJobData("deploymentId", value.getDeploymentId()).usingJobData("generation", value.getGeneration()).build();
    var builder = TriggerBuilder.newTrigger().withIdentity("workflow-" + value.getId(), "workflow_" + app).forJob(job)
      .withSchedule(CronScheduleBuilder.cronSchedule(text(cfg, "cron"))
        .inTimeZone(TimeZone.getTimeZone(ZoneId.of(text(cfg, "timezone").isBlank() ? "Asia/Shanghai" : text(cfg, "timezone"))))
        .withMisfireHandlingInstructionFireAndProceed());
    if (!text(cfg, "startAt").isBlank()) builder.startAt(Date.from(Instant.parse(text(cfg, "startAt"))));
    else builder.startNow();
    if (!text(cfg, "endAt").isBlank()) builder.endAt(Date.from(Instant.parse(text(cfg, "endAt"))));
    scheduler.scheduleJob(job, builder.build());
  }

  public void log(String app, String flow, String source, String state, String instance, String reason) {
    String id = UUID.nameUUIDFromBytes((app + ":" + flow + ":" + source).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    try (var s = openSession()) {
      if (s.data().findById("f_app_flow_trigger_log", id).isEmpty())
        s.data().insert("f_app_flow_trigger_log", new LinkedHashMap<>(Map.of("id", id, "app_id", app, "flow_id", flow, "source_id", source,
          "state", state, "instance_id", instance, "reason", reason, "created_at", LocalDateTime.now())));
    }
  }

  public List<Map<String, Object>> logs(String app, String flow) {
    try (var s = openSession()) {
      return s.data().findAll("f_app_flow_trigger_log", field("app_id").eq(app).and(field("flow_id").eq(flow)));
    }
  }
}
