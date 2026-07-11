package dev.flexmodel.realtime;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.project.ProjectService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

/**
 * 实时订阅 WebSocket 端点。
 * <p>
 * 项目维度的数据变更实时订阅，projectId 通过 URL 路径传入。
 * 客户端通过 channel（表名）订阅，支持单表、多表（JSON 数组）或通配符 "*"。
 * <p>
 * 协议参考 Supabase Realtime，服务端推送项目下匹配模型的增删改事件。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
@ServerEndpoint("/projects/{projectId}/realtime")
public class RealtimeWebSocket {

  @Inject
  RealtimeBroadcaster broadcaster;

  @Inject
  ProjectService projectService;

  @OnOpen
  public void onOpen(Session session, @PathParam("projectId") String projectId) {
    // resolveDatabaseName → findProject 触发 @CacheResult 拦截器，
    // CacheResultInterceptor 内部使用 Uni.await().indefinitely() 会阻塞当前线程，
    // 但 WebSocket @OnOpen 默认在 Vert.x event loop 上执行，禁止阻塞 → 必须 offload 到 worker 线程池。
    Uni.createFrom().item(() -> projectService.resolveDatabaseName(projectId))
      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
      .subscribe().with(
        schemaName -> {
          broadcaster.register(session, schemaName);
          log.info("Realtime WebSocket connected: session={}, projectId={}, schemaName={}",
            session.getId(), projectId, schemaName);
        },
        error -> {
          log.error("Realtime WebSocket onOpen failed: projectId={}", projectId, error);
          try {
            session.close();
          } catch (IOException e) {
            log.error("Failed to close session", e);
          }
        }
      );
  }

  @OnClose
  public void onClose(Session session, @PathParam("projectId") String projectId) {
    broadcaster.unregister(session);
    log.info("Realtime WebSocket closed: session={}, projectId={}", session.getId(), projectId);
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    log.debug("Realtime onMessage received: session={}, payload={}", session.getId(), message);
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> msg = JsonUtils.parseToObject(message, Map.class);
      String type = (String) msg.get("type");

      if (type == null) {
        sendSystemMessage(session, "error", null, "Missing 'type' field");
        return;
      }

      switch (type) {
        case "subscribe" -> handleSubscribe(msg, session);
        case "unsubscribe" -> handleUnsubscribe(msg, session);
        case "heartbeat" -> sendSystemMessage(session, "heartbeat_ok", null, null);
        default -> sendSystemMessage(session, "error", null, "Unknown message type: " + type);
      }
    } catch (Exception e) {
      log.error("Error processing realtime message from session={}", session.getId(), e);
      sendSystemMessage(session, "error", null, "Parse error: " + e.getMessage());
    }
  }

  private void handleSubscribe(Map<String, Object> msg, Session session) {
    String id = (String) msg.get("id");
    Object channelObj = msg.get("channel");

    if (id == null || channelObj == null) {
      sendSystemMessage(session, "error", id, "Missing 'id' or 'channel' field");
      return;
    }

    // channel 支持 string / string[] / "*"
    Set<String> models = parseChannel(channelObj);
    if (models == null) {
      sendSystemMessage(session, "error", id, "Invalid 'channel' format: expected string or string array");
      return;
    }

    boolean ok = broadcaster.subscribe(session, id, models);
    if (ok) {
      sendSystemMessage(session, "subscribe_ok", id, null, "channel", channelObj);
    } else {
      sendSystemMessage(session, "error", id, "Failed to subscribe");
    }
  }

  private void handleUnsubscribe(Map<String, Object> msg, Session session) {
    String id = (String) msg.get("id");
    if (id == null) {
      sendSystemMessage(session, "error", null, "Missing 'id' field");
      return;
    }

    broadcaster.unsubscribe(session, id);
    sendSystemMessage(session, "unsubscribe_ok", id, null);
  }

  /**
   * 解析 channel 字段，支持：
   * <ul>
   *   <li>字符串 "*" — 匹配所有模型</li>
   *   <li>字符串 "users" — 匹配单个模型</li>
   *   <li>JSON 数组 ["users", "orders"] — 匹配多个模型</li>
   * </ul>
   *
   * @return 模型名集合，解析失败返回 null
   */
  @SuppressWarnings("unchecked")
  private Set<String> parseChannel(Object channelObj) {
    if (channelObj instanceof String s) {
      Set<String> set = new LinkedHashSet<>();
      set.add(s);
      return set;
    }
    if (channelObj instanceof List<?> list) {
      Set<String> set = new LinkedHashSet<>();
      for (Object item : list) {
        if (!(item instanceof String s)) {
          return null;
        }
        set.add(s);
      }
      return set;
    }
    return null;
  }

  private void sendSystemMessage(Session session, String event, String id, String errorMessage,
                                 Object... extraKeyValues) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "system");
    payload.put("event", event);
    if (id != null) {
      payload.put("id", id);
    }
    if (errorMessage != null) {
      payload.put("message", errorMessage);
    }
    // 额外的键值对
    for (int i = 0; i + 1 < extraKeyValues.length; i += 2) {
      payload.put((String) extraKeyValues[i], extraKeyValues[i + 1]);
    }

    String json = JsonUtils.toJsonString(payload);
    session.getAsyncRemote().sendText(json);
  }
}
