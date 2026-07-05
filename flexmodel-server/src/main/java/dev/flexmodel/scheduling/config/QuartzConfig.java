package dev.flexmodel.scheduling.config;

import dev.flexmodel.scheduling.job.ScheduledFlowExecutionJobListener;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class QuartzConfig {

  @Inject
  Scheduler scheduler;

  @Inject
  ScheduledFlowExecutionJobListener jobListener;

  @PostConstruct
  void init() {
    try {
      scheduler.getListenerManager().addJobListener(jobListener);
      log.info("已注册作业执行监听器: {}", jobListener.getName());
    } catch (SchedulerException e) {
      log.error("注册作业监听器失败", e);
    }
  }

}
