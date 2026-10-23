package dev.flexmodel.scheduling;

import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.codegen.System.jobExecutionLog;

@ApplicationScoped
@ActivateRequestContext
public class JobExecutionLogFmRepository extends AbstractRepository implements JobExecutionLogRepository {

  @Override
  public JobExecutionLog findById(String projectId, String id) {
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(JobExecutionLog.class)
        .where(jobExecutionLog.projectId.eq(projectId).and(jobExecutionLog.id.eq(id)))
        .executeOne();
    }
  }

  @Override
  public JobExecutionLog save(String projectId, JobExecutionLog jobExecutionLog) {
    jobExecutionLog.setProjectId(projectId);
    try (Session session = openSession()) {
      session.dsl()
        .mergeInto(JobExecutionLog.class)
        .values(jobExecutionLog)
        .execute();
    }
    return jobExecutionLog;
  }

  @Override
  public void delete(String projectId, Predicate filter) {
    try (Session session = openSession()) {
      session.dsl()
        .deleteFrom(JobExecutionLog.class)
        .where(jobExecutionLog.projectId.eq(projectId).and(filter))
        .execute();
    }
  }

  @Override
  public List<JobExecutionLog> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(JobExecutionLog.class)
        .where(jobExecutionLog.projectId.eq(projectId).and(filter))
        .page(page, size)
        .orderByDesc(jobExecutionLog.startTime)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(JobExecutionLog.class)
        .where(jobExecutionLog.projectId.eq(projectId).and(filter))
        .count();
    }
  }

  @Override
  public int purgeOldLogs(String projectId, int days) {
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(days);
    Predicate filter = jobExecutionLog.createdAt.lte(purgeDate);

    long count = count(projectId, filter);

    delete(projectId, filter);

    return (int) count;
  }
}
