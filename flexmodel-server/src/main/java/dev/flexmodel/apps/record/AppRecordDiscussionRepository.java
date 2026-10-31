package dev.flexmodel.apps.record;

import dev.flexmodel.codegen.entity.AppRecordDiscussion;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Query;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.appRecordDiscussion;

@ApplicationScoped
public class AppRecordDiscussionRepository extends AbstractRepository {

  public void insert(String appId, Map<String, Object> discussion) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(discussion);
      row.put("app_id", appId);
      session.data().insert(ReflectionUtils.getModelNameFromClass(AppRecordDiscussion.class), row);
    }
  }

  public Map<String, Object> find(String appId, String formName, String recordId, String discussionId) {
    try (Session session = openSession()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(AppRecordDiscussion.class), discussionId)
        .filter(row -> appId.equals(row.get("app_id"))
          && formName.equals(row.get("form_name"))
          && recordId.equals(row.get("record_id")))
        .orElse(null);
    }
  }

  public Map<String, Object> findById(String appId, String discussionId) {
    try (Session session = openSession()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(AppRecordDiscussion.class), discussionId)
        .filter(row -> appId.equals(row.get("app_id")))
        .orElse(null);
    }
  }

  public List<Map<String, Object>> findRoots(String appId, String formName, String recordId, int page, int size) {
    int limit = Math.clamp(size, 1, 50);
    try (Session session = openSession()) {
      return session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(AppRecordDiscussion.class))
        .where(recordFilter(appId, formName, recordId).and(appRecordDiscussion.parentId.eq("")))
        .orderBy(new Query.OrderBy().desc("created_at").desc("id"))
        .page(Math.clamp(page, 1, Integer.MAX_VALUE / limit), limit)
        .execute();
    }
  }

  public List<Map<String, Object>> findReplies(String appId, Collection<String> rootIds) {
    if (rootIds.isEmpty()) return List.of();
    try (Session session = openSession()) {
      return session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(AppRecordDiscussion.class))
        .where(appRecordDiscussion.appId.eq(appId).and(appRecordDiscussion.parentId.in(rootIds)))
        .orderBy(new Query.OrderBy().asc("created_at").asc("id"))
        .execute();
    }
  }

  public long count(String appId, String formName, String recordId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(AppRecordDiscussion.class)
        .where(recordFilter(appId, formName, recordId)).count();
    }
  }

  public long countRoots(String appId, String formName, String recordId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(AppRecordDiscussion.class)
        .where(recordFilter(appId, formName, recordId).and(appRecordDiscussion.parentId.eq(""))).count();
    }
  }

  public void deleteThread(String appId, String discussionId) {
    try (Session session = openSession()) {
      session.dsl().deleteFrom(AppRecordDiscussion.class)
        .where(appRecordDiscussion.appId.eq(appId)
          .and(appRecordDiscussion.id.eq(discussionId).or(appRecordDiscussion.parentId.eq(discussionId))))
        .execute();
    }
  }

  public void deleteByRecord(String appId, String formName, String recordId) {
    try (Session session = openSession()) {
      session.dsl().deleteFrom(AppRecordDiscussion.class)
        .where(recordFilter(appId, formName, recordId)).execute();
    }
  }

  public void deleteByForm(String appId, String formName) {
    try (Session session = openSession()) {
      session.dsl().deleteFrom(AppRecordDiscussion.class)
        .where(appRecordDiscussion.appId.eq(appId).and(appRecordDiscussion.formName.eq(formName))).execute();
    }
  }

  private static dev.flexmodel.query.Predicate recordFilter(String appId, String formName, String recordId) {
    return appRecordDiscussion.appId.eq(appId)
      .and(appRecordDiscussion.formName.eq(formName))
      .and(appRecordDiscussion.recordId.eq(recordId));
  }
}
