package dev.flexmodel.apps.record;

import dev.flexmodel.codegen.entity.AppRecordHistory;
import dev.flexmodel.codegen.entity.AppRecordHistoryTarget;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.codegen.System.appRecordHistory;
import static dev.flexmodel.codegen.System.appRecordHistoryTarget;
import static dev.flexmodel.common.Values.text;

/**
 * 平台历史事件与展示目标索引持久化。
 */
@ApplicationScoped
public class AppRecordHistoryRepository extends AbstractRepository {

  private static final int DELETE_CHUNK = 500;

  public void insertEvent(String appId, Map<String, Object> event) {
    try (Session session = openSession()) {
      Map<String, Object> row = new LinkedHashMap<>(event);
      row.put("app_id", appId);
      session.dsl().insertInto(AppRecordHistory.class).values(row).execute();
    }
  }

  /**
   * 批量写事件：导入一次写入上千条历史时，逐条开关会话会成为主要耗时。
   */
  public void insertEvents(String appId, List<Map<String, Object>> events) {
    if (events.isEmpty()) return;
    List<Map<String, Object>> rows = new ArrayList<>(events.size());
    for (Map<String, Object> event : events) {
      Map<String, Object> row = new LinkedHashMap<>(event);
      row.put("app_id", appId);
      rows.add(row);
    }
    try (Session session = openSession()) {
      rows.forEach(row -> session.dsl().insertInto(AppRecordHistory.class).values(row).execute());
    }
  }

  public void insertTargets(String appId, List<Map<String, Object>> targets) {
    if (targets.isEmpty()) return;
    List<Map<String, Object>> rows = new ArrayList<>(targets.size());
    for (Map<String, Object> target : targets) {
      Map<String, Object> row = new LinkedHashMap<>(target);
      row.put("app_id", appId);
      rows.add(row);
    }
    try (Session session = openSession()) {
      rows.forEach(row -> session.dsl().insertInto(AppRecordHistoryTarget.class).values(row).execute());
    }
  }

  /**
   * 按时间线分页读取目标索引；游标位置为空时从头读取。
   */
  public List<Map<String, Object>> findTargets(
    String appId,
    String formName,
    String recordId,
    LocalDateTime cursorOccurredAt,
    String cursorEventId,
    int limit
  ) {
    try (Session session = openSession()) {
      Predicate filter = targetFilter(appId, formName, recordId);
      if (cursorOccurredAt != null && cursorEventId != null) {
        filter = filter.and(appRecordHistoryTarget.occurredAt.lt(cursorOccurredAt)
          .or(appRecordHistoryTarget.occurredAt.eq(cursorOccurredAt)
            .and(appRecordHistoryTarget.eventId.lt(cursorEventId))));
      }
      return session.dsl().selectFrom(AppRecordHistoryTarget.class)
        .where(filter)
        .orderBy(new Query.OrderBy().desc("occurred_at").desc("event_id"))
        .limit(limit)
        .executeMaps();
    }
  }

  public List<Map<String, Object>> findEvents(String appId, Collection<String> eventIds) {
    if (eventIds.isEmpty()) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    try (Session session = openSession()) {
      for (List<String> chunk : partition(eventIds)) {
        result.addAll(session.dsl().selectFrom(AppRecordHistory.class)
          .where(appRecordHistory.appId.eq(appId).and(appRecordHistory.id.in(chunk)))
          .executeMaps());
      }
    }
    return result;
  }

  public Map<String, Object> findEvent(String appId, String eventId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(AppRecordHistory.class).whereId(eventId).executeOneMap()
        .filter(row -> appId.equals(row.get("app_id")))
        .orElse(null);
    }
  }

  public Map<String, Object> findTarget(String appId, String eventId, String formName, String recordId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(AppRecordHistoryTarget.class)
        .where(appRecordHistoryTarget.appId.eq(appId)
          .and(appRecordHistoryTarget.eventId.eq(eventId))
          .and(appRecordHistoryTarget.formName.eq(formName))
          .and(appRecordHistoryTarget.recordId.eq(recordId)))
        .limit(1)
        .executeMaps()
        .stream().findFirst().orElse(null);
    }
  }

  /**
   * 删除表单历史：清理以该表单为来源的事件及其全部目标，同时清理以该表单为时间线的目标索引。
   */
  public void deleteByForm(String appId, String formName) {
    List<String> eventIds = new ArrayList<>();
    try (Session session = openSession()) {
      int page = 1;
      while (true) {
        List<Map<String, Object>> rows = session.dsl()
          .selectFrom(AppRecordHistory.class)
          .where(appRecordHistory.appId.eq(appId).and(appRecordHistory.formName.eq(formName)))
          .orderBy(new Query.OrderBy().asc("id"))
          .page(page, DELETE_CHUNK)
          .executeMaps();
        rows.forEach(row -> eventIds.add(text(row, "id")));
        if (rows.size() < DELETE_CHUNK) break;
        page++;
      }
      session.dsl().deleteFrom(AppRecordHistoryTarget.class)
        .where(appRecordHistoryTarget.appId.eq(appId)
          .and(appRecordHistoryTarget.formName.eq(formName)))
        .execute();
      for (List<String> chunk : partition(eventIds)) {
        session.dsl().deleteFrom(AppRecordHistoryTarget.class)
          .where(appRecordHistoryTarget.appId.eq(appId)
            .and(appRecordHistoryTarget.eventId.in(chunk)))
          .execute();
      }
      session.dsl().deleteFrom(AppRecordHistory.class)
        .where(appRecordHistory.appId.eq(appId).and(appRecordHistory.formName.eq(formName)))
        .execute();
    }
  }

  private static List<List<String>> partition(Collection<String> ids) {
    List<List<String>> result = new ArrayList<>();
    List<String> current = new ArrayList<>();
    for (String id : ids) {
      current.add(id);
      if (current.size() >= DELETE_CHUNK) {
        result.add(current);
        current = new ArrayList<>();
      }
    }
    if (!current.isEmpty()) result.add(current);
    return result;
  }

  private static Predicate targetFilter(String appId, String formName, String recordId) {
    return appRecordHistoryTarget.appId.eq(appId)
      .and(appRecordHistoryTarget.formName.eq(formName))
      .and(appRecordHistoryTarget.recordId.eq(recordId));
  }
}
