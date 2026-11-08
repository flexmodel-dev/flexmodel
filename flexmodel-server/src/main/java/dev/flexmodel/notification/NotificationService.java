package dev.flexmodel.notification;

import dev.flexmodel.apps.record.AppRecordDiscussionRepository;
import dev.flexmodel.common.messaging.ReliableMessageHandler;
import dev.flexmodel.common.messaging.ReliableMessages;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.project.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Producers only enqueue in their project transaction; delivery owns a separate system transaction.
 */
@ApplicationScoped
public class NotificationService implements ReliableMessageHandler {
  @Inject
  ReliableMessages messages;
  @Inject
  NotificationRepository inbox;
  @Inject
  DirectoryService directoryService;
  @Inject
  ProjectRepository projects;
  @Inject
  AppRecordDiscussionRepository appRecordDiscussions;

  public String type() {
    return "notification.deliver";
  }

  public boolean activeUser(String user) {
    return inbox.activeUser(user);
  }

  public Map<String, Object> list(String user, String status, String category, String app, int page, int size) {
    return inbox.list(user, status, category, app, page, size);
  }

  public Map<String, Object> summary(String user) {
    return inbox.summary(user);
  }

  public Map<String, Object> detail(String user, String id) {
    var result = new LinkedHashMap<>(inbox.detail(user, id));
    result.put("canNavigate", false);
    Object appId = result.get("appId"), instanceId = result.get("instanceId"), discussionId = result.get("discussionId");
    if (instanceId != null && !instanceId.toString().isBlank() && appId != null
      && projects.findProject(appId.toString()) != null) {
      result.put("canNavigate", true);
    } else if (appId != null && discussionId != null && !discussionId.toString().isBlank()) {
      Map<String, Object> discussion = appRecordDiscussions.findById(appId.toString(), discussionId.toString());
      if (discussion != null) {
        result.put("formName", discussion.get("form_name"));
        result.put("recordId", discussion.get("record_id"));
        result.put("canNavigate", true);
      }
    }
    return result;
  }

  public long read(String user, String id, String category, String app) {
    return inbox.read(user, id, category, app);
  }

  @SuppressWarnings("unchecked")
  public void handle(String app, String key, Map<String, Object> payload) {
    for (String user : (List<String>) payload.get("recipients")) inbox.deliver(app, key, user, payload);
  }

  public void enqueue(String app, String key, String run, String task, String category, String title, String content, List<String> recipients) {
    enqueue(app, key, run, task, category, title, content, recipients, Map.of());
  }

  public void enqueue(String app, String key, String run, String task, String category, String title, String content,
                      List<String> recipients, Map<String, Object> extra) {
    if (recipients.isEmpty()) return;
    var project = projects.findProject(app);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("recipients", recipients.stream().distinct().toList());
    payload.put("orderingKey", "notification:" + key);
    payload.put("instance_id", run);
    payload.put("task_id", task);
    payload.put("app_name", project == null ? app : project.getName());
    payload.put("category", category);
    payload.put("title", title);
    payload.put("content", content);
    payload.put("occurred_at", LocalDateTime.now().toString());
    payload.putAll(extra);
    messages.enqueue(app, "notification:" + key, type(), payload);
  }

  /**
   * 讨论中被提及时通知对应成员。
   */
  public void discussionMention(String app, String discussionId, String author, String formLabel,
                                String content, List<String> recipients) {
    discussionNotification(app, discussionId, author, formLabel, content, recipients, "mention", "提到了你");
  }

  /**
   * 回复通知发给具体回复目标的作者。
   */
  public void discussionReply(String app, String discussionId, String author, String formLabel,
                              String content, String recipient) {
    discussionNotification(app, discussionId, author, formLabel, content, List.of(recipient), "reply", "回复了你的讨论");
  }

  private void discussionNotification(String app, String discussionId, String author, String formLabel,
                                      String content, List<String> recipients, String kind, String action) {
    if (recipients.isEmpty()) return;
    String title = author + " 在「" + formLabel + "」中" + action;
    String excerpt = content.length() > 500 ? content.substring(0, 500) : content;
    enqueue(app, "discussion:" + discussionId + ":" + kind, "", "", "discussion",
      title.substring(0, Math.min(200, title.length())), excerpt, recipients,
      Map.of("discussion_id", discussionId));
  }

  public void pending(String app, String run, String task, String user, String title) {
    enqueue(app, "pending:" + task, run, task, "approval", "待你审批：" + title.substring(0, Math.min(190, title.length())), "你有一项新的审批任务，请查看审批详情并处理。", List.of(user));
  }

  public void result(String app, String run, String user, String title, String status) {
    String label = Map.of("SUCCEEDED", "已完成", "REJECTED", "已拒绝", "TERMINATED", "已终止").get(status);
    enqueue(app, "result:" + run + ":" + status, run, "", "approval", "流程" + label + "：" + title.substring(0, Math.min(185, title.length())), "你发起的流程" + label + "，请查看审批详情。", List.of(user));
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> node(String app, String run, String task, String initiator, Map<String, Object> cfg, Map<String, Object> variables) {
    List<String> recipients;
    String title, content;
    try {
      recipients = directoryService.resolve((List<Map<String, Object>>) cfg.get("rules"), initiator, variables);
      if (recipients.isEmpty()) return Map.of("status", "skipped", "reason", "未解析到有效接收人");
      title = NotificationTemplates.render(Objects.toString(cfg.get("titleTemplate"), ""), variables, 200);
      content = NotificationTemplates.render(Objects.toString(cfg.get("contentTemplate"), ""), variables, 5000);
    } catch (RuntimeException error) {
      return Map.of("status", "failed", "reason", Objects.toString(error.getMessage(), "通知配置执行失败"));
    }
    enqueue(app, "node:" + task, run, task, "workflow", title, content, recipients);
    return Map.of("status", "queued", "recipientCount", recipients.size(), "title", title, "content", content);
  }
}
