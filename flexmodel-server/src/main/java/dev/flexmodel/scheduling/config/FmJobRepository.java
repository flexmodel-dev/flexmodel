package dev.flexmodel.scheduling.config;

import dev.flexmodel.codegen.entity.*;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.List;

import static dev.flexmodel.codegen.System.*;

@ApplicationScoped
public class FmJobRepository {

  private static final String DEFAULT_SCHEMA_NAME = "system";

  SessionFactory sessionFactory;

  public FmJobRepository() {
    this.sessionFactory = CDI.current().select(SessionFactory.class).get();
  }

  public QrtzJobDetail findJobDetail(String schedName, String jobName, String jobGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzJobDetail.class)
        .where(qrtzJobDetail.schedName.eq(schedName)
          .and(qrtzJobDetail.jobName.eq(jobName))
          .and(qrtzJobDetail.jobGroup.eq(jobGroup)))
        .executeOne();
    }
  }

  public void upsertJobDetail(QrtzJobDetail jobDetail) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().mergeInto(QrtzJobDetail.class).values(jobDetail).execute();
    }
  }

  public void deleteJob(String schedName, String jobName, String jobGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .deleteFrom(QrtzJobDetail.class)
        .where(qrtzJobDetail.schedName.eq(schedName)
          .and(qrtzJobDetail.jobName.eq(jobName))
          .and(qrtzJobDetail.jobGroup.eq(jobGroup)))
        .execute();
    }
  }

  public List<QrtzTrigger> findTriggersByJob(String schedName, String jobName, String jobGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzTrigger.class)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.jobName.eq(jobName))
          .and(qrtzTrigger.jobGroup.eq(jobGroup)))
        .execute();
    }
  }

  public QrtzTrigger findTrigger(String schedName, String triggerName, String triggerGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzTrigger.class)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.triggerName.eq(triggerName))
          .and(qrtzTrigger.triggerGroup.eq(triggerGroup)))
        .executeOne();
    }
  }

  public void upsertTrigger(QrtzTrigger trigger) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().mergeInto(QrtzTrigger.class).values(trigger).execute();
    }
  }

  public void upsertSimpleTrigger(QrtzSimpleTrigger simple) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().mergeInto(QrtzSimpleTrigger.class).values(simple).execute();
    }
  }

  public void upsertCronTrigger(QrtzCronTrigger cron) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().mergeInto(QrtzCronTrigger.class).values(cron).execute();
    }
  }

  public void upsertSimpropTrigger(QrtzSimpropTrigger simprop) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().mergeInto(QrtzSimpropTrigger.class).values(simprop).execute();
    }
  }

  public void deleteTrigger(String schedName, String triggerName, String triggerGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .deleteFrom(QrtzTrigger.class)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.triggerName.eq(triggerName))
          .and(qrtzTrigger.triggerGroup.eq(triggerGroup)))
        .execute();
      session.dsl()
        .deleteFrom(QrtzSimpleTrigger.class)
        .where(qrtzSimpleTrigger.schedName.eq(schedName)
          .and(qrtzSimpleTrigger.triggerName.eq(triggerName))
          .and(qrtzSimpleTrigger.triggerGroup.eq(triggerGroup)))
        .execute();
      session.dsl()
        .deleteFrom(QrtzCronTrigger.class)
        .where(qrtzCronTrigger.schedName.eq(schedName)
          .and(qrtzCronTrigger.triggerName.eq(triggerName))
          .and(qrtzCronTrigger.triggerGroup.eq(triggerGroup)))
        .execute();
      session.dsl()
        .deleteFrom(QrtzSimpropTrigger.class)
        .where(qrtzSimpropTrigger.schedName.eq(schedName)
          .and(qrtzSimpropTrigger.triggerName.eq(triggerName))
          .and(qrtzSimpropTrigger.triggerGroup.eq(triggerGroup)))
        .execute();
    }
  }

  public List<QrtzTrigger> findTriggers(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzTrigger.class)
        .where(qrtzTrigger.schedName.eq(schedName))
        .execute();
    }
  }

  public List<QrtzJobDetail> findJobs(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzJobDetail.class)
        .where(qrtzJobDetail.schedName.eq(schedName))
        .execute();
    }
  }

  public List<QrtzCalendar> findCalendars(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzCalendar.class)
        .where(qrtzCalendar.schedName.eq(schedName))
        .execute();
    }
  }

  public void clearAll(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().deleteFrom(QrtzSimpleTrigger.class).execute();
      session.dsl().deleteFrom(QrtzCronTrigger.class).execute();
      session.dsl().deleteFrom(QrtzSimpropTrigger.class).execute();
      session.dsl().deleteFrom(QrtzTrigger.class).execute();
      session.dsl().deleteFrom(QrtzJobDetail.class).execute();
      session.dsl().deleteFrom(QrtzCalendar.class).execute();
    }
  }

  public QrtzCalendar findCalendar(String schedName, String calName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzCalendar.class)
        .where(qrtzCalendar.schedName.eq(schedName)
          .and(qrtzCalendar.calendarName.eq(calName)))
        .executeOne();
    }
  }

  public void upsertCalendar(QrtzCalendar calendar) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().mergeInto(QrtzCalendar.class).values(calendar).execute();
    }
  }

  public void deleteCalendar(String schedName, String calName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl().
        deleteFrom(QrtzCalendar.class)
        .where(qrtzCalendar.schedName.eq(schedName)
          .and(qrtzCalendar.calendarName.eq(calName)))
        .execute();
    }
  }

  public void updateTriggersStateByCalendarName(String schedName, String calName, String state) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzTrigger.class)
        .set(qrtzTrigger.triggerState, state)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.calendarName.eq(calName)))
        .execute();
    }
  }

  public long countJobs(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzJobDetail.class)
        .where(qrtzJobDetail.schedName.eq(schedName))
        .count();
    }
  }

  public long countTriggers(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzTrigger.class)
        .where(qrtzTrigger.schedName.eq(schedName))
        .count();
    }
  }

  public long countCalendars(String schedName) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl().selectFrom(QrtzCalendar.class)
        .where(qrtzCalendar.schedName.eq(schedName))
        .count();
    }
  }

  public List<QrtzTrigger> findDueTriggers(String schedName, long noLaterThan, long timeWindow, int maxCount) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzTrigger.class)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.triggerState.eq(Trigger.TriggerState.NORMAL.name()))
          .and(qrtzTrigger.nextFireTime.lte(noLaterThan + timeWindow)))
        .orderBy(qrtzTrigger.nextFireTime)
        .orderByDesc(qrtzTrigger.priority)
        .page(1, maxCount)
        .forUpdate()
        .execute();
    }
  }

  public void updateTriggerState(String schedName, String triggerName, String triggerGroup, String state) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzTrigger.class)
        .set(qrtzTrigger.triggerState, state)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.triggerName.eq(triggerName))
          .and(qrtzTrigger.triggerGroup.eq(triggerGroup)))
        .execute();
    }
  }

  public void updateAllTriggersState(String schedName, String state) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzTrigger.class)
        .set(qrtzTrigger.triggerState, state)
        .where(qrtzTrigger.schedName.eq(schedName))
        .execute();
    }
  }

  public void blockOtherTriggersOfJob(String schedName, JobKey jobKey, TriggerKey current) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzTrigger.class)
        .set(qrtzTrigger.triggerState, Trigger.TriggerState.BLOCKED.name())
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.jobName.eq(jobKey.getName()))
          .and(qrtzTrigger.jobGroup.eq(jobKey.getGroup()))
          .and(qrtzTrigger.triggerState.eq(Trigger.TriggerState.NORMAL.name()))
          .and(qrtzTrigger.triggerName.ne(current.getName())
            .or(qrtzTrigger.triggerGroup.ne(current.getGroup()))))
        .execute();
    }
  }

  public void unblockBlockedTriggersOfJob(String schedName, JobKey jobKey) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzTrigger.class)
        .set(qrtzTrigger.triggerState, Trigger.TriggerState.NORMAL.name())
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.jobName.eq(jobKey.getName()))
          .and(qrtzTrigger.jobGroup.eq(jobKey.getGroup()))
          .and(qrtzTrigger.triggerState.eq(Trigger.TriggerState.BLOCKED.name())))
        .execute();
    }
  }

  public QrtzCronTrigger findCronMeta(String schedName, String triggerName, String triggerGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzCronTrigger.class)
        .where(qrtzCronTrigger.schedName.eq(schedName)
          .and(qrtzCronTrigger.triggerName.eq(triggerName))
          .and(qrtzCronTrigger.triggerGroup.eq(triggerGroup)))
        .executeOne();
    }
  }

  public QrtzSimpleTrigger findSimpleMeta(String schedName, String triggerName, String triggerGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzSimpleTrigger.class)
        .where(qrtzSimpleTrigger.schedName.eq(schedName)
          .and(qrtzSimpleTrigger.triggerName.eq(triggerName))
          .and(qrtzSimpleTrigger.triggerGroup.eq(triggerGroup)))
        .executeOne();
    }
  }

  public QrtzSimpropTrigger findSimpropMeta(String schedName, String triggerName, String triggerGroup) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      return session.dsl()
        .selectFrom(QrtzSimpropTrigger.class)
        .where(qrtzSimpropTrigger.schedName.eq(schedName)
          .and(qrtzSimpropTrigger.triggerName.eq(triggerName))
          .and(qrtzSimpropTrigger.triggerGroup.eq(triggerGroup)))
        .executeOne();
    }
  }

  public void updateTriggerFireTimes(String schedName, String triggerName, String triggerGroup, Long prev, Long next) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzTrigger.class)
        .set(qrtzTrigger.prevFireTime, prev)
        .set(qrtzTrigger.nextFireTime, next)
        .where(qrtzTrigger.schedName.eq(schedName)
          .and(qrtzTrigger.triggerName.eq(triggerName))
          .and(qrtzTrigger.triggerGroup.eq(triggerGroup)))
        .execute();
    }
  }

  public void updateJobData(String schedName, JobKey jobKey, JobDataMap jobDataMap) {
    try (Session session = sessionFactory.createSession(DEFAULT_SCHEMA_NAME)) {
      session.dsl()
        .update(QrtzJobDetail.class)
        .set(qrtzJobDetail.jobData, jobDataMap)
        .where(qrtzJobDetail.schedName.eq(schedName)
          .and(qrtzJobDetail.jobName.eq(jobKey.getName()))
          .and(qrtzJobDetail.jobGroup.eq(jobKey.getGroup())))
        .execute();
    }
  }
}


