package dev.flexmodel.realtime;

import dev.flexmodel.JsonUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时订阅 WebSocket 端点。
 * <p>
 * 独立于 /api/json-rpc-ws（控制台专用），提供纯项目维度的数据变更实时订阅。
 * <p>
 * 协议参考 Supabase Realtime，客户端通过 channel（projectId）订阅，
 * 服务端推送该项目下所有模型的增删改事件。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
@ServerEndpoint("/api/realtime")
public class RealtimeWebSocket {

  @Inject
  RealtimeBroadcaster broadcaster;

  @OnOpen
  public void onOpen(Session session) {
    broadcaster.register(session);
    log.info("Realtime WebSocket connected: {}", session.getId());
  }

  @OnClose
  public void onClose(Session session) {
    broadcaster.unregister(session);
    log.info("Realtime WebSocket closed: {}", session.getId());
  }

  @OnMessage
  public void onMessage(String message, Session session) {
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
      try {
        sendSystemMessage(session, "error", null, "Parse error: " + e.getMessage());
      } catch (IOException ex) {
        log.error("Failed to send error response", ex);
      }
    }
  }

  private void handleSubscribe(Map<String, Object> msg, Session session) throws IOException {
    String id = (String) msg.get("id");
    String channel = (String) msg.get("channel");

    if (id == null || channel == null) {
      sendSystemMessage(session, "error", id, "Missing 'id' or 'channel' field");
      return;
    }

    String model = msg.get("model") != null ? (String) msg.get("model") : "*";
    String schemaName = broadcaster.subscribe(session, id, channel, model);
    if (schemaName != null) {
      sendSystemMessage(session, "subscribe_ok", id, null, "channel", channel);
    } else {
      sendSystemMessage(session, "error", id, "Failed to subscribe to channel: " + channel);
    }
  }

  private void handleUnsubscribe(Map<String, Object> msg, Session session) throws IOException {
    String id = (String) msg.get("id");
    if (id == null) {
      sendSystemMessage(session, "error", null, "Missing 'id' field");
      return;
    }

    broadcaster.unsubscribe(session, id);
    sendSystemMessage(session, "unsubscribe_ok", id, null);
  }

  private void sendSystemMessage(Session session, String event, String id, String errorMessage,
                                 String... extraKeyValues) throws IOException {
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
      payload.put(extraKeyValues[i], extraKeyValues[i + 1]);
    }

    String json = JsonUtils.toJsonString(payload);
    session.getBasicRemote().sendText(json);
  }
}
