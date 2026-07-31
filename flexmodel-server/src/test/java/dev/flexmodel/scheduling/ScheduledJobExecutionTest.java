package dev.flexmodel.scheduling;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.scheduling.job.ScheduledFlowExecutionJob;
import dev.flexmodel.scheduling.job.ScheduledFunctionExecutionJob;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class ScheduledJobExecutionTest {

  @Inject
  Scheduler quartz;

  @Inject
  JobExecutionLogService jobExecutionLogService;

  @BeforeEach
  void clearScheduler() throws Exception {
    quartz.clear();
  }

  @AfterEach
  void cleanup() throws Exception {
    quartz.clear();
  }

  private JobDataMap fullJobDataMap() {
    JobDataMap map = new JobDataMap();
    map.put("triggerId", "test-trigger-id");
    map.put("jobId", "test-job-id");
    map.put("jobGroup", "test-job-group");
    map.put("jobType", "FLOW");
    map.put("projectId", "test-project");
    return map;
  }

  @Test
  void scheduledFlowExecutionJobListener_jobCompletesWithoutException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<JobExecutionContext> capturedCtx = new AtomicReference<>();
    AtomicReference<JobExecutionException> capturedEx = new AtomicReference<>();

    JobListener captureListener = new JobListener() {
      @Override
      public String getName() { return "CaptureListener-success"; }

      @Override
      public void jobToBeExecuted(JobExecutionContext context) {}

      @Override
      public void jobExecutionVetoed(JobExecutionContext context) {}

      @Override
      public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        capturedCtx.set(context);
        if (jobException != null) capturedEx.set(jobException);
        latch.countDown();
      }
    };

    quartz.getListenerManager().addJobListener(captureListener);
    try {
      JobDetail job = JobBuilder.newJob(NoopJob.class)
        .withIdentity("job-listener-success", "grp-listener")
        .usingJobData(fullJobDataMap())
        .storeDurably()
        .build();
      Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("trig-listener-success", "grp-listener")
        .forJob(job)
        .startNow()
        .build();

      quartz.scheduleJob(job, trigger);
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      JobExecutionContext ctx = capturedCtx.get();
      assertNotNull(ctx);
      assertNull(capturedEx.get(), "NoopJob should succeed without JobExecutionException");
    } finally {
      quartz.getListenerManager().removeJobListener(captureListener.getName());
    }
  }

  @Test
  void scheduledFlowExecutionJobListener_jobCompletesWithException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<JobExecutionContext> capturedCtx = new AtomicReference<>();
    AtomicReference<JobExecutionException> capturedEx = new AtomicReference<>();

    JobListener captureListener = new JobListener() {
      @Override
      public String getName() { return "CaptureListener-failure"; }

      @Override
      public void jobToBeExecuted(JobExecutionContext context) {}

      @Override
      public void jobExecutionVetoed(JobExecutionContext context) {}

      @Override
      public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        capturedCtx.set(context);
        if (jobException != null) capturedEx.set(jobException);
        latch.countDown();
      }
    };

    quartz.getListenerManager().addJobListener(captureListener);
    try {
      JobDetail job = JobBuilder.newJob(FailingJob.class)
        .withIdentity("job-listener-failure", "grp-listener")
        .usingJobData(fullJobDataMap())
        .storeDurably()
        .build();
      Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("trig-listener-failure", "grp-listener")
        .forJob(job)
        .startNow()
        .build();

      quartz.scheduleJob(job, trigger);
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      JobExecutionContext ctx = capturedCtx.get();
      assertNotNull(ctx);
      assertNotNull(capturedEx.get(), "FailingJob should throw JobExecutionException");
    } finally {
      quartz.getListenerManager().removeJobListener(captureListener.getName());
    }
  }

  @Test
  void scheduledFunctionExecutionJob_missingJobId_throwsJobExecutionException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<JobExecutionException> capturedEx = new AtomicReference<>();

    JobListener captureListener = new JobListener() {
      @Override
      public String getName() { return "CaptureListener-func-missing"; }

      @Override
      public void jobToBeExecuted(JobExecutionContext context) {}

      @Override
      public void jobExecutionVetoed(JobExecutionContext context) {}

      @Override
      public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        if (jobException != null) capturedEx.set(jobException);
        latch.countDown();
      }
    };

    quartz.getListenerManager().addJobListener(captureListener);
    try {
      JobDataMap map = new JobDataMap();
      map.put("triggerId", "test-trigger-id");
      map.put("jobGroup", "test-job-group");
      map.put("jobType", "FUNCTION");
      map.put("projectId", "test-project");

      JobDetail job = JobBuilder.newJob(ScheduledFunctionExecutionJob.class)
        .withIdentity("job-func-missing", "grp-func-missing")
        .usingJobData(map)
        .storeDurably()
        .build();
      Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("trig-func-missing", "grp-func-missing")
        .forJob(job)
        .startNow()
        .build();

      quartz.scheduleJob(job, trigger);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedEx.get(), "ScheduledFunctionExecutionJob without jobId should throw JobExecutionException");
    } finally {
      quartz.getListenerManager().removeJobListener(captureListener.getName());
    }
  }

  @Test
  void scheduledFlowExecutionJob_missingJobId_throwsJobExecutionException() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<JobExecutionException> capturedEx = new AtomicReference<>();

    JobListener captureListener = new JobListener() {
      @Override
      public String getName() { return "CaptureListener-flow-missing"; }

      @Override
      public void jobToBeExecuted(JobExecutionContext context) {}

      @Override
      public void jobExecutionVetoed(JobExecutionContext context) {}

      @Override
      public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        if (jobException != null) capturedEx.set(jobException);
        latch.countDown();
      }
    };

    quartz.getListenerManager().addJobListener(captureListener);
    try {
      JobDataMap map = new JobDataMap();
      map.put("triggerId", "test-trigger-id");
      map.put("jobGroup", "test-job-group");
      map.put("jobType", "FLOW");
      map.put("projectId", "test-project");

      JobDetail job = JobBuilder.newJob(ScheduledFlowExecutionJob.class)
        .withIdentity("job-flow-missing", "grp-flow-missing")
        .usingJobData(map)
        .storeDurably()
        .build();
      Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("trig-flow-missing", "grp-flow-missing")
        .forJob(job)
        .startNow()
        .build();

      quartz.scheduleJob(job, trigger);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNotNull(capturedEx.get(), "ScheduledFlowExecutionJob without jobId should throw JobExecutionException");
    } finally {
      quartz.getListenerManager().removeJobListener(captureListener.getName());
    }
  }

  @Test
  void fmJobStore_crud() throws Exception {
    JobDetail job = JobBuilder.newJob(NoopJob.class)
      .withIdentity("job-crud", "grp-crud")
      .usingJobData(fullJobDataMap())
      .storeDurably()
      .build();
    quartz.addJob(job, false);
    assertTrue(quartz.checkExists(job.getKey()));
    JobDetail retrieved = quartz.getJobDetail(job.getKey());
    assertNotNull(retrieved);
    assertEquals("test-job-id", retrieved.getJobDataMap().getString("jobId"));

    Trigger trigger = TriggerBuilder.newTrigger()
      .withIdentity("trig-crud", "grp-crud")
      .forJob(job)
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInHours(1)
        .repeatForever())
      .startAt(new Date(System.currentTimeMillis() + 3600_000))
      .build();
    quartz.scheduleJob(trigger);
    assertTrue(quartz.checkExists(trigger.getKey()));

    assertThrows(org.quartz.JobPersistenceException.class, () -> {
      JobDetail dup = JobBuilder.newJob(NoopJob.class)
        .withIdentity("job-crud", "grp-crud")
        .storeDurably()
        .build();
      quartz.addJob(dup, false);
    });

    JobDetail replaceJob = JobBuilder.newJob(NoopJob.class)
      .withIdentity("job-crud", "grp-crud")
      .usingJobData(fullJobDataMap())
      .storeDurably()
      .build();
    quartz.addJob(replaceJob, true);
    assertTrue(quartz.checkExists(job.getKey()));

    assertTrue(quartz.deleteJob(job.getKey()));
    assertFalse(quartz.checkExists(job.getKey()));
    assertFalse(quartz.checkExists(trigger.getKey()), "Deleting job should cascade-delete its triggers");
  }

  @Test
  void fmJobStore_triggerState() throws Exception {
    JobDetail job = JobBuilder.newJob(NoopJob.class)
      .withIdentity("job-state", "grp-state")
      .storeDurably()
      .build();
    Trigger trigger = TriggerBuilder.newTrigger()
      .withIdentity("trig-state", "grp-state")
      .forJob(job)
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInSeconds(1)
        .withRepeatCount(0))
      .startAt(new Date(System.currentTimeMillis() + 60_000))
      .build();
    quartz.scheduleJob(job, trigger);

    assertEquals(Trigger.TriggerState.NORMAL, quartz.getTriggerState(trigger.getKey()));

    quartz.pauseTrigger(trigger.getKey());
    assertEquals(Trigger.TriggerState.PAUSED, quartz.getTriggerState(trigger.getKey()));

    quartz.resumeTrigger(trigger.getKey());
    assertEquals(Trigger.TriggerState.NORMAL, quartz.getTriggerState(trigger.getKey()));
  }

  @Test
  void fmJobStore_calendar() throws Exception {
    org.quartz.impl.calendar.HolidayCalendar cal = new org.quartz.impl.calendar.HolidayCalendar();
    cal.addExcludedDate(new Date());
    quartz.addCalendar("testCal", cal, true, false);
    assertNotNull(quartz.getCalendar("testCal"));

    org.quartz.impl.calendar.HolidayCalendar cal2 = new org.quartz.impl.calendar.HolidayCalendar();
    quartz.addCalendar("testCal2", cal2, true, false);
    assertNotNull(quartz.getCalendar("testCal2"));

    assertTrue(quartz.deleteCalendar("testCal"));
    assertNull(quartz.getCalendar("testCal"));

    assertTrue(quartz.deleteCalendar("testCal2"));
    assertNull(quartz.getCalendar("testCal2"));
  }

  @Test
  void fmJobStore_triggerFires() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<JobExecutionContext> capturedCtx = new AtomicReference<>();

    JobListener captureListener = new JobListener() {
      @Override
      public String getName() { return "CaptureListener-fire"; }

      @Override
      public void jobToBeExecuted(JobExecutionContext context) {}

      @Override
      public void jobExecutionVetoed(JobExecutionContext context) {}

      @Override
      public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        capturedCtx.set(context);
        latch.countDown();
      }
    };

    quartz.getListenerManager().addJobListener(captureListener);
    try {
      JobDetail job = JobBuilder.newJob(NoopJob.class)
        .withIdentity("job-fire", "grp-fire")
        .usingJobData(fullJobDataMap())
        .build();
      Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("trig-fire", "grp-fire")
        .forJob(job)
        .startNow()
        .build();

      quartz.scheduleJob(job, trigger);
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      JobExecutionContext ctx = capturedCtx.get();
      assertNotNull(ctx);
      assertNotNull(ctx.getFireTime());
      assertEquals("job-fire", ctx.getJobDetail().getKey().getName());
    } finally {
      quartz.getListenerManager().removeJobListener(captureListener.getName());
    }
  }

  @Test
  void fmJobStore_disallowConcurrent() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    NonConcurrentTestJob.reset(latch);

    JobDetail job = JobBuilder.newJob(NonConcurrentTestJob.class)
      .withIdentity("job-nc", "grp-nc")
      .usingJobData(fullJobDataMap())
      .build();

    Trigger t1 = TriggerBuilder.newTrigger()
      .withIdentity("t1-nc", "grp-nc")
      .forJob(job)
      .startNow()
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInMilliseconds(50)
        .withRepeatCount(0))
      .build();
    Trigger t2 = TriggerBuilder.newTrigger()
      .withIdentity("t2-nc", "grp-nc")
      .forJob(job)
      .startAt(new Date(System.currentTimeMillis() + 10))
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInMilliseconds(50)
        .withRepeatCount(0))
      .build();

    Map<JobDetail, Set<? extends Trigger>> bundle = new HashMap<>();
    bundle.put(job, new LinkedHashSet<>(Arrays.asList(t1, t2)));
    quartz.scheduleJobs(bundle, true);

    assertTrue(latch.await(5, TimeUnit.SECONDS));
    assertFalse(NonConcurrentTestJob.detectedOverlap.get(), "No overlap should occur for @DisallowConcurrentExecution job");
  }

  @Test
  void fmJobStore_reschedule() throws Exception {
    JobDetail job = JobBuilder.newJob(NoopJob.class)
      .withIdentity("job-rs", "grp-rs")
      .usingJobData(fullJobDataMap())
      .storeDurably()
      .build();
    Date startAt = new Date(System.currentTimeMillis() + 5000);
    Trigger trig = TriggerBuilder.newTrigger()
      .withIdentity("trig-rs", "grp-rs")
      .forJob(job)
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInSeconds(10)
        .withRepeatCount(0))
      .startAt(startAt)
      .build();
    quartz.scheduleJob(job, trig);

    Date oldNext = quartz.getTrigger(trig.getKey()).getNextFireTime();
    assertNotNull(oldNext);

    Trigger newTrig = TriggerBuilder.newTrigger()
      .withIdentity(trig.getKey())
      .forJob(job)
      .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInSeconds(60)
        .withRepeatCount(0))
      .startAt(new Date(oldNext.getTime() + 30_000))
      .build();
    quartz.rescheduleJob(trig.getKey(), newTrig);

    Date newNext = quartz.getTrigger(trig.getKey()).getNextFireTime();
    assertNotNull(newNext);
    assertTrue(newNext.after(oldNext), "Rescheduled nextFireTime should be later than the original");
  }

  @Test
  void fmJobStore_groupQueries() throws Exception {
    JobDetail j1 = JobBuilder.newJob(NoopJob.class)
      .withIdentity("A", "G-alpha").usingJobData(fullJobDataMap()).storeDurably().build();
    JobDetail j2 = JobBuilder.newJob(NoopJob.class)
      .withIdentity("B", "G-alpha").usingJobData(fullJobDataMap()).storeDurably().build();
    JobDetail j3 = JobBuilder.newJob(NoopJob.class)
      .withIdentity("C", "G-beta").usingJobData(fullJobDataMap()).storeDurably().build();
    quartz.addJob(j1, true);
    quartz.addJob(j2, true);
    quartz.addJob(j3, true);

    Set<JobKey> alpha = quartz.getJobKeys(GroupMatcher.jobGroupEquals("G-alpha"));
    assertEquals(2, alpha.size());

    Set<JobKey> beta = quartz.getJobKeys(GroupMatcher.jobGroupEquals("G-beta"));
    assertEquals(1, beta.size());

    Set<JobKey> allG = quartz.getJobKeys(GroupMatcher.jobGroupStartsWith("G-"));
    assertEquals(3, allG.size());

    Trigger t1 = TriggerBuilder.newTrigger().withIdentity("T1", "TG-alpha").forJob(j1)
      .startAt(new Date(System.currentTimeMillis() + 60_000)).build();
    Trigger t2 = TriggerBuilder.newTrigger().withIdentity("T2", "TG-alpha").forJob(j2)
      .startAt(new Date(System.currentTimeMillis() + 60_000)).build();
    Trigger t3 = TriggerBuilder.newTrigger().withIdentity("T3", "TG-beta").forJob(j3)
      .startAt(new Date(System.currentTimeMillis() + 60_000)).build();
    quartz.scheduleJob(t1);
    quartz.scheduleJob(t2);
    quartz.scheduleJob(t3);

    Set<TriggerKey> tgAlpha = quartz.getTriggerKeys(GroupMatcher.triggerGroupEquals("TG-alpha"));
    assertEquals(2, tgAlpha.size());

    Set<TriggerKey> tgBeta = quartz.getTriggerKeys(GroupMatcher.triggerGroupEquals("TG-beta"));
    assertEquals(1, tgBeta.size());

    List<String> jobGroups = quartz.getJobGroupNames();
    assertTrue(jobGroups.contains("G-alpha"));
    assertTrue(jobGroups.contains("G-beta"));

    List<String> triggerGroups = quartz.getTriggerGroupNames();
    assertTrue(triggerGroups.contains("TG-alpha"));
    assertTrue(triggerGroups.contains("TG-beta"));
  }

  public static class NoopJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
    }
  }

  public static class FailingJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
      throw new JobExecutionException("intentional test failure");
    }
  }

  @DisallowConcurrentExecution
  public static class NonConcurrentTestJob implements Job {
    private static final AtomicBoolean inProgress = new AtomicBoolean(false);
    static final AtomicBoolean detectedOverlap = new AtomicBoolean(false);
    static CountDownLatch latch;

    static void reset(CountDownLatch l) {
      inProgress.set(false);
      detectedOverlap.set(false);
      latch = l;
    }

    @Override
    public void execute(JobExecutionContext context) {
      if (!inProgress.compareAndSet(false, true)) {
        detectedOverlap.set(true);
      }
      try {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        if (latch != null) {
          latch.countDown();
        }
      } finally {
        inProgress.set(false);
      }
    }
  }
}
