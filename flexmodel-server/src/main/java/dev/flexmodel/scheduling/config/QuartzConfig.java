package dev.flexmodel.scheduling.config;

import dev.flexmodel.scheduling.job.ScheduledFlowExecutionJobListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Properties;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class QuartzConfig {

  @Inject
  ScheduledFlowExecutionJobListener jobListener;

  @Produces
  public Scheduler scheduler() throws Exception {
    Properties props = new Properties();

    // 基础配置
    props.setProperty("org.quartz.scheduler.instanceName", "FlexmodelScheduler");
    props.setProperty("org.quartz.threadPool.threadCount", "5");

    // 使用自定义 JobStore
    props.setProperty("org.quartz.jobStore.class", FmJobStore.class.getName());
//    props.setProperty("org.quartz.jobStore.class", RAMJobStore.class.getName());

    SchedulerFactory schedulerFactory = new StdSchedulerFactory(props);
    Scheduler scheduler = schedulerFactory.getScheduler();

    // 注册作业监听器
    try {
      scheduler.getListenerManager().addJobListener(jobListener);
      log.info("已注册作业执行监听器: {}", jobListener.getName());
    } catch (SchedulerException e) {
      log.error("注册作业监听器失败", e);
    }

    scheduler.start();
    return scheduler;
  }

  /**
   * 容器销毁（含 dev 模式热重启）时关闭调度器，避免旧的 QuartzSchedulerThread
   * 残留并继续通过已关闭的 ArC 容器访问 CDI Bean。
   */
  public void stopScheduler(@Disposes Scheduler scheduler) {
    try {
      scheduler.shutdown(true);
      log.info("Quartz scheduler 已关闭");
    } catch (SchedulerException e) {
      log.error("关闭 Quartz scheduler 失败", e);
    }
  }

}
