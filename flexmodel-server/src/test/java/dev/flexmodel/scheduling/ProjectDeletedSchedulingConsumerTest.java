package dev.flexmodel.scheduling;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.project.ProjectDeletedEvent;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.*;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证项目删除事件触发 Quartz 调度作业的精确清理。
 * <p>
 * 覆盖两条边界用例：
 * <ul>
 *   <li>目标项目（jobGroup 与 JobDataMap.projectId 均匹配）的作业/触发器应被清理；</li>
 *   <li>前缀碰撞项目（group 含目标项目 id 前缀，如 {@code e2e_proj_test} vs {@code e2e_proj}，
 *       模拟 dev/dev_test 歧义）但 JobDataMap.projectId 不符的作业应保留，证明双重匹配不误删。</li>
 * </ul>
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class ProjectDeletedSchedulingConsumerTest {

  @Inject
  Scheduler quartz;

  @Inject
  EventBus eventBus;

  /**
   * 被清理的目标作业：group 以 e2e_proj 为前缀，JobDataMap.projectId=e2e_proj。
   */
  private static final String TARGET_PROJECT_ID = "e2e_proj";
  private static final JobKey TARGET_JOB_KEY = JobKey.jobKey("job-e2e-target", "e2e_proj_flow_x");
  private static final TriggerKey TARGET_TRIGGER_KEY = TriggerKey.triggerKey("trigger-e2e-target", "e2e_proj_flow_x");

  /**
   * 前缀碰撞作业：group 以 e2e_proj_test 为前缀（含 token e2e_proj_），projectId=e2e_proj_test，
   * 用于验证 GroupMatcher 粗筛后必须依赖 JobDataMap.projectId 精确比对，否则会误删。
   */
  private static final String COLLISION_PROJECT_ID = "e2e_proj_test";
  private static final JobKey COLLISION_JOB_KEY = JobKey.jobKey("job-e2e-collision", "e2e_proj_test_flow_y");
  private static final TriggerKey COLLISION_TRIGGER_KEY = TriggerKey.triggerKey("trigger-e2e-collision", "e2e_proj_test_flow_y");

  @BeforeEach
  void seedJobs() throws SchedulerException {
    scheduleNoopJob(TARGET_JOB_KEY, TARGET_TRIGGER_KEY, TARGET_PROJECT_ID);
    scheduleNoopJob(COLLISION_JOB_KEY, COLLISION_TRIGGER_KEY, COLLISION_PROJECT_ID);
    assertTrue(quartz.checkExists(TARGET_JOB_KEY), "前置：目标作业应存在");
    assertTrue(quartz.checkExists(COLLISION_JOB_KEY), "前置：前缀碰撞作业应存在");
  }

  @AfterEach
  void cleanupJobs() {
    // 定向清理，避免影响共享 Scheduler 上下文中由 seed 数据注册的其它作业（如 dev_test seed）
    deleteJobQuietly(COLLISION_JOB_KEY);
    deleteJobQuietly(TARGET_JOB_KEY);
  }

  @Test
  void projectDeletedEventCleansMatchingQuartzJobsOnly() throws SchedulerException {
    // 发布删除 e2e_proj 的事件：consumer 应清理 target，保留 collision
    eventBus.publish("project.deleted", new ProjectDeletedEvent(TARGET_PROJECT_ID));

    // 有界轮询等待异步 consumer 完成（≤5s，每 100ms），不引入 Awaitility 依赖
    long deadline = System.currentTimeMillis() + 5_000L;
    while (quartz.checkExists(TARGET_JOB_KEY) && System.currentTimeMillis() < deadline) {
      sleep(100L);
    }

    // 目标作业及其触发器应已被清理
    assertFalse(quartz.checkExists(TARGET_JOB_KEY), "目标 Quartz 作业应被清理: " + TARGET_JOB_KEY);
    assertFalse(quartz.checkExists(TARGET_TRIGGER_KEY), "目标 Quartz 触发器应被取消: " + TARGET_TRIGGER_KEY);

    // 前缀碰撞作业应保留（JobDataMap.projectId 不符，不可误删）
    assertTrue(quartz.checkExists(COLLISION_JOB_KEY),
      "前缀碰撞项目（projectId 不符）作业不应被误删: " + COLLISION_JOB_KEY);
  }

  /**
   * 注册一个空实现的 Quartz 作业，触发器起始时间设为 1 小时后，避免测试期间真实触发执行。
   */
  private void scheduleNoopJob(JobKey jobKey, TriggerKey triggerKey, String projectId) throws SchedulerException {
    if (quartz.checkExists(jobKey)) {
      quartz.deleteJob(jobKey);
    }
    JobDetail jobDetail = JobBuilder.newJob(NoopJob.class)
      .withIdentity(jobKey)
      .usingJobData("projectId", projectId)
      .storeDurably()
      .build();
    Trigger trigger = TriggerBuilder.newTrigger()
      .withIdentity(triggerKey)
      .forJob(jobDetail)
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInHours(1)
        .repeatForever())
      .startAt(Date.from(Instant.now().plusSeconds(3600)))
      .build();
    quartz.scheduleJob(jobDetail, trigger);
  }

  private void deleteJobQuietly(JobKey jobKey) {
    try {
      if (quartz.checkExists(jobKey)) {
        quartz.deleteJob(jobKey);
      }
    } catch (SchedulerException ignored) {
      // 清理过程不阻断测试
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 空实现作业，仅用于占位调度断言，不执行任何业务逻辑。
   */
  public static class NoopJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
      // no-op
    }
  }
}
