package dev.flexmodel.scheduling.consumer;

import dev.flexmodel.project.ProjectDeletedEvent;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 监听项目删除事件，清理该项目在 Quartz 中残留的调度作业与触发器。
 * <p>
 * 业务触发器记录 {@code f_trigger} 随项目物理 Schema 一起被 DROP，但 Quartz 持久化数据
 * （{@code f_qrtz_*}）存放于系统 Schema，与项目 Schema 相互独立，删项目后不会自动清除，
 * 会形成继续触发的「孤儿调度作业」。本 consumer 在 {@code project.deleted} 事件后做兜底清理。
 * <p>
 * 匹配策略采用双重校验，避免前缀歧义误删：
 * <ol>
 *   <li>{@link GroupMatcher#jobGroupContains(String)} 以 {@code projectId + "_"} 粗筛候选作业
 *       （{@link dev.flexmodel.scheduling.TriggerService#getJobGroup} 生成的 group 形如
 *       {@code {projectId}_flow_{jobId}} / {@code {projectId}_fn_{jobId}} / {@code {projectId}_{modelName}}）；</li>
 *   <li>逐个校验 {@code JobDataMap.projectId} 与目标 projectId 严格相等，排除 {@code dev}/{@code dev_test}
 *       这类前缀包含（token {@code dev_}）导致的误删，以及无 projectId 的非业务作业。</li>
 * </ol>
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class ProjectDeletedSchedulingConsumer {

  @Inject
  Scheduler scheduler;

  @ConsumeEvent("project.deleted")
  public void consume(ProjectDeletedEvent event) {
    String projectId = event.getProjectId();
    List<JobKey> jobKeys = findProjectJobKeys(projectId);
    if (jobKeys.isEmpty()) {
      log.info("项目删除：无 Quartz 调度作业需清理，projectId={}", projectId);
      return;
    }
    cleanupJobs(projectId, jobKeys);
  }

  /**
   * 通过 GroupMatcher 粗筛 + JobDataMap.projectId 精确比对，定位属于指定项目的 Quartz 作业。
   */
  private List<JobKey> findProjectJobKeys(String projectId) {
    List<JobKey> matched = new ArrayList<>();
    try {
      Set<JobKey> candidates = scheduler.getJobKeys(GroupMatcher.jobGroupContains(projectId + "_"));
      for (JobKey jk : candidates) {
        try {
          if (scheduler.getJobDetail(jk) == null) {
            continue;
          }
          String jobProjectId = scheduler.getJobDetail(jk).getJobDataMap().getString("projectId");
          if (projectId.equals(jobProjectId)) {
            matched.add(jk);
          }
        } catch (Exception e) {
          // 单个作业解析失败不影响其他作业的清理
          log.warn("项目删除：解析作业 JobDataMap 失败，跳过: jobKey={}, projectId={}", jk, projectId, e);
        }
      }
    } catch (SchedulerException e) {
      log.error("项目删除：查询 Quartz 作业失败，projectId={}", projectId, e);
    }
    return matched;
  }

  /**
   * 先取消调度触发器，再删除作业（{@link Scheduler#deleteJobs} 会兜底移除作业关联的残留触发器）。
   * 失败仅记日志不抛出，与项目删除流程中「物理清理失败不阻断主流程」一致。
   */
  private void cleanupJobs(String projectId, List<JobKey> jobKeys) {
    List<org.quartz.TriggerKey> triggerKeys = new ArrayList<>();
    for (JobKey jk : jobKeys) {
      try {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jk);
        for (Trigger t : triggers) {
          triggerKeys.add(t.getKey());
        }
      } catch (SchedulerException e) {
        log.warn("项目删除：查询作业触发器失败: jobKey={}, projectId={}", jk, projectId, e);
      }
    }
    try {
      if (!triggerKeys.isEmpty()) {
        scheduler.unscheduleJobs(triggerKeys);
      }
    } catch (SchedulerException e) {
      log.warn("项目删除：取消触发器调度失败: projectId={}", projectId, e);
    }
    try {
      scheduler.deleteJobs(jobKeys);
    } catch (SchedulerException e) {
      log.error("项目删除：删除 Quartz 作业失败: projectId={}", projectId, e);
    }
    log.info("项目删除：已清理 {} 个 Quartz 调度作业，projectId={}", jobKeys.size(), projectId);
  }

}
