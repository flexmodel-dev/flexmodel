package dev.flexmodel.scheduling;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
@ActivateRequestContext
public class JobExecutionLogFmRepository extends AbstractRepository implements JobExecutionLogRepository {

  @Override
  public JobExecutionLog findById(String id) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(JobExecutionLog.class)
        .where(field(JobExecutionLog::getId).eq(id))
        .executeOne();
    }
  }

  @Override
  public JobExecutionLog save(String projectId, JobExecutionLog jobExecutionLog) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .mergeInto(JobExecutionLog.class)
        .values(jobExecutionLog)
        .execute();
    }
    return jobExecutionLog;
  }

  @Override
  public void delete(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .deleteFrom(JobExecutionLog.class)
        .where(field(JobExecutionLog::getProjectId).eq(projectId).and(filter))
        .execute();
    }
  }

  @Override
  public List<JobExecutionLog> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(JobExecutionLog.class)
        .where(field(JobExecutionLog::getProjectId).eq(projectId).and(filter))
        .page(page, size)
        .orderByDesc(JobExecutionLog::getStartTime)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(JobExecutionLog.class)
        .where(field(JobExecutionLog::getProjectId).eq(projectId).and(filter))
        .count();
    }
  }

  @Override
  public int purgeOldLogs(String projectId, int days) {
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(days);
    Predicate filter = field(JobExecutionLog::getCreatedAt).lte(purgeDate);

    long count = count(projectId, filter);

    delete(projectId, filter);

    return (int) count;
  }
}
