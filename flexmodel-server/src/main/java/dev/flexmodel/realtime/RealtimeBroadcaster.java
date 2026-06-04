package dev.flexmodel.realtime;

import dev.flexmodel.common.utils.JsonUtils;
import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * 订阅维度为项目（channel = projectId）+ 可选模型过滤（model = modelName 或 "*"），
 * 服务端解析 projectId 到 schemaName 进行事件匹配。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class RealtimeBroadcaster {

  @Inject
  ProjectService projectService;

  /**
   * 订阅过滤器，包含订阅 ID 和模型匹配模式
   */
  record SubscriptionFilter(String id, String modelPattern) {
    boolean matchesModel(String modelName) {
      return "*".equals(modelPattern) || modelPattern.equals(modelName);
    }
  }

  /**
   * 订阅元数据，用于取消订阅时反查
   */
  record SubscriptionMeta(String schemaName, String modelPattern) {
  }

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
   * 注册新的 WebSocket session
   */
  public void register(Session session) {
    sessionSubscriptions.put(session, new ConcurrentHashMap<>());
    log.info("Realtime session registered: {}", session.getId());
  }

  /**
   * 注销 WebSocket session，清理其所有订阅
   */
  public void unregister(Session session) {
    Map<String, SubscriptionMeta> subs = sessionSubscriptions.remove(session);
    if (subs != null) {
      for (SubscriptionMeta meta : subs.values()) {
        Map<Session, Set<SubscriptionFilter>> schemaMap = schemaSessions.get(meta.schemaName());
        if (schemaMap != null) {
          Set<SubscriptionFilter> filters = schemaMap.get(session);
          if (filters != null) {
            filters.removeIf(f -> subs.containsValue(meta) || true); // 清理该 session 的所有 filter
          }
          schemaMap.remove(session);
          if (schemaMap.isEmpty()) {
            schemaSessions.remove(meta.schemaName());
          }
        }
      }
      // 更精确的清理：逐个移除该 session 在各 schemaName 下的 filter
      cleanupSessionFromAllSchemas(session);
    }
    log.info("Realtime session unregistered: {}", session.getId());
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
   * 订阅指定项目的变更事件，可选指定模型过滤
   *
   * @param session        WebSocket session
   * @param subscriptionId 客户端分配的订阅 ID
   * @param projectId      项目 ID（channel）
   * @param model          模型名过滤（"*" 表示所有模型）
   * @return 解析后的 schemaName，如果项目不存在则返回 null
   */
  public String subscribe(Session session, String subscriptionId, String projectId, String model) {
    try {
      String schemaName = projectService.resolveDatabaseName(projectId);
      String modelPattern = (model == null || model.isBlank()) ? "*" : model;

      SubscriptionFilter filter = new SubscriptionFilter(subscriptionId, modelPattern);
      SubscriptionMeta meta = new SubscriptionMeta(schemaName, modelPattern);

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

      log.info("Realtime subscribe: session={}, subId={}, projectId={}, schemaName={}, model={}",
        session.getId(), subscriptionId, projectId, schemaName, modelPattern);
      return schemaName;
    } catch (Exception e) {
      log.error("Realtime subscribe failed: projectId={}", projectId, e);
      return null;
    }
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

          String json = JsonUtils.getInstance().stringify(payload);
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
