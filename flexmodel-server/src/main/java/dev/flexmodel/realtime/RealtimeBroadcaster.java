package dev.flexmodel.realtime;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.event.ChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时事件广播器，管理 WebSocket 订阅关系并将引擎事件路由到匹配的订阅者。
 * <p>
 * 订阅维度为 schemaName（由 WebSocket onOpen 时通过 projectId 解析）+ channel（表名集合或 "*"），
 * schemaName 在连接建立时由 WebSocket 层解析并传入，本类不依赖 ProjectService。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class RealtimeBroadcaster {

  /**
   * 订阅过滤器，包含订阅 ID 和模型匹配集合
   */
  record SubscriptionFilter(String id, Set<String> modelPatterns) {
    boolean matchesModel(String modelName) {
      return modelPatterns.contains("*") || modelPatterns.contains(modelName);
    }
  }

  /**
   * 订阅元数据，用于取消订阅时反查
   */
  record SubscriptionMeta(String schemaName, Set<String> modelPatterns) {
  }

  /**
   * session -> schemaName（onOpen 时绑定）
   */
  private final Map<Session, String> sessionSchemaMap = new ConcurrentHashMap<>();

  /**
   * schemaName -> (session -> set of SubscriptionFilters)
   */
  private final Map<String, Map<Session, Set<SubscriptionFilter>>> schemaSessions = new ConcurrentHashMap<>();

  /**
   * session -> (subscriptionId -> SubscriptionMeta) -- 用于取消订阅时反查
   */
  private final Map<Session, Map<String, SubscriptionMeta>> sessionSubscriptions = new ConcurrentHashMap<>();

  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  /**
   * 注册新的 WebSocket session，绑定 schemaName
   *
   * @param session    WebSocket session
   * @param schemaName 由 projectId 解析得到的 schemaName
   */
  public void register(Session session, String schemaName) {
    sessionSchemaMap.put(session, schemaName);
    sessionSubscriptions.put(session, new ConcurrentHashMap<>());
    log.info("Realtime session registered: session={}, schemaName={}", session.getId(), schemaName);
  }

  /**
   * 注销 WebSocket session，清理其所有订阅
   */
  public void unregister(Session session) {
    String schemaName = sessionSchemaMap.remove(session);
    Map<String, SubscriptionMeta> subs = sessionSubscriptions.remove(session);

    // 清理该 session 在各 schemaName 下的订阅
    if (schemaName != null) {
      Map<Session, Set<SubscriptionFilter>> schemaMap = schemaSessions.get(schemaName);
      if (schemaMap != null) {
        schemaMap.remove(session);
        if (schemaMap.isEmpty()) {
          schemaSessions.remove(schemaName);
        }
      }
    }
    // 兜底清理（防止跨 schema 残留）
    cleanupSessionFromAllSchemas(session);

    log.info("Realtime session unregistered: session={}, schemaName={}", session.getId(), schemaName);
  }

  private void cleanupSessionFromAllSchemas(Session session) {
    for (Map.Entry<String, Map<Session, Set<SubscriptionFilter>>> schemaEntry : schemaSessions.entrySet()) {
      Map<Session, Set<SubscriptionFilter>> schemaMap = schemaEntry.getValue();
      schemaMap.remove(session);
      if (schemaMap.isEmpty()) {
        schemaSessions.remove(schemaEntry.getKey());
      }
    }
  }

  /**
   * 订阅变更事件，channel 为模型名集合（"*" 表示所有模型）
   *
   * @param session        WebSocket session
   * @param subscriptionId 客户端分配的订阅 ID
   * @param models         模型名集合，包含 "*" 时匹配所有模型
   * @return 是否订阅成功
   */
  public boolean subscribe(Session session, String subscriptionId, Set<String> models) {
    String schemaName = sessionSchemaMap.get(session);
    if (schemaName == null) {
      log.warn("Realtime subscribe failed: session={} has no bound schemaName", session.getId());
      return false;
    }

    Set<String> modelPatterns = (models == null || models.isEmpty()) ? Set.of("*") : models;

    SubscriptionFilter filter = new SubscriptionFilter(subscriptionId, modelPatterns);
    SubscriptionMeta meta = new SubscriptionMeta(schemaName, modelPatterns);

    // 更新 schemaSessions
    schemaSessions
      .computeIfAbsent(schemaName, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet())
      .add(filter);

    // 更新 sessionSubscriptions
    Map<String, SubscriptionMeta> subs = sessionSubscriptions.get(session);
    if (subs != null) {
      subs.put(subscriptionId, meta);
    }

    log.info("Realtime subscribe: session={}, subId={}, schemaName={}, models={}",
      session.getId(), subscriptionId, schemaName, modelPatterns);
    return true;
  }

  /**
   * 取消订阅
   *
   * @param session        WebSocket session
   * @param subscriptionId 订阅 ID
   */
  public void unsubscribe(Session session, String subscriptionId) {
    Map<String, SubscriptionMeta> subs = sessionSubscriptions.get(session);
    if (subs == null) return;

    SubscriptionMeta meta = subs.remove(subscriptionId);
    if (meta != null) {
      Map<Session, Set<SubscriptionFilter>> schemaMap = schemaSessions.get(meta.schemaName());
      if (schemaMap != null) {
        Set<SubscriptionFilter> filters = schemaMap.get(session);
        if (filters != null) {
          filters.removeIf(f -> f.id().equals(subscriptionId));
          if (filters.isEmpty()) {
            schemaMap.remove(session);
          }
        }
        if (schemaMap.isEmpty()) {
          schemaSessions.remove(meta.schemaName());
        }
      }
    }
    log.info("Realtime unsubscribe: session={}, subId={}", session.getId(), subscriptionId);
  }

  /**
   * 广播变更事件到所有匹配的订阅者
   *
   * @param event 引擎后置事件
   */
  public void broadcast(ChangedEvent event) {
    String schemaName = event.getSchemaName();
    Map<Session, Set<SubscriptionFilter>> matched = schemaSessions.get(schemaName);
    if (matched == null || matched.isEmpty()) {
      log.debug("Realtime broadcast skipped: eventSchema={}, knownSchemas={}, schemaSessionsSize={}",
        schemaName, schemaSessions.keySet(), schemaSessions.size());
      return;
    }

    // 映射引擎事件类型到协议事件类型
    String eventType = mapEventType(event.getEventType());
    String commitTimestamp = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));

    Map<String, Object> newData = event.getNewData() != null ? event.getNewData() : Map.of();
    Map<String, Object> oldData = event.getOldData() != null ? event.getOldData() : Map.of();

    for (Map.Entry<Session, Set<SubscriptionFilter>> entry : matched.entrySet()) {
      Session session = entry.getKey();
      Set<SubscriptionFilter> filters = entry.getValue();

      if (!session.isOpen()) {
        continue;
      }

      for (SubscriptionFilter filter : filters) {
        if (!filter.matchesModel(event.getModelName())) {
          continue;
        }

        try {
          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("type", "realtime");
          payload.put("id", filter.id());
          payload.put("event", eventType);
          payload.put("schema", schemaName);
          payload.put("model", event.getModelName());
          payload.put("commit_timestamp", commitTimestamp);
          payload.put("new", newData);
          payload.put("old", oldData);

          String json = JsonUtils.toJsonString(payload);
          session.getBasicRemote().sendText(json);
        } catch (IOException e) {
          log.warn("Failed to send realtime event to session={}, subId={}: {}",
            session.getId(), filter.id(), e.getMessage());
        }
      }
    }
  }

  /**
   * 将引擎事件类型映射到协议事件类型
   * INSERTED -> INSERT, UPDATED -> UPDATE, DELETED -> DELETE
   */
  private String mapEventType(String engineEventType) {
    return switch (engineEventType) {
      case "INSERTED" -> "INSERT";
      case "UPDATED" -> "UPDATE";
      case "DELETED" -> "DELETE";
      default -> engineEventType;
    };
  }
}
