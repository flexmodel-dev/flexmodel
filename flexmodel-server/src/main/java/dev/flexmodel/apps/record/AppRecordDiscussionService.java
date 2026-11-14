package dev.flexmodel.apps.record;

import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.navigation.AppNavigationService;

import dev.flexmodel.apps.record.dto.AppRecordDiscussionCreateRequest;
import dev.flexmodel.apps.record.dto.AppRecordDiscussionPage;
import dev.flexmodel.apps.record.dto.AppRecordDiscussionView;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.notification.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static dev.flexmodel.common.Values.objects;
import static dev.flexmodel.common.Values.text;

@ApplicationScoped
public class AppRecordDiscussionService {

  private static final int MAX_CONTENT_LENGTH = 2000;
  private static final int MAX_MENTIONS = 20;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;
  private static final String ROOT_PARENT = "";

  @Inject
  SessionContext sessionContext;

  @Inject
  AppRecordDiscussionRepository discussions;

  @Inject
  AppRecordService appRecordService;

  @Inject
  AppFormService appFormService;

  @Inject
  AppNavigationService appNavigationService;

  @Inject
  DirectoryService directoryService;

  @Inject
  NotificationService notificationService;

  @Inject
  ProjectUnitOfWork transactions;

  public AppRecordDiscussionPage page(String appId, String formName, String recordId, int page, int size) {
    String userId = requireAccess(appId, formName, recordId);
    int limit = Math.clamp(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1, MAX_PAGE_SIZE);
    List<Map<String, Object>> roots = discussions.findRoots(appId, formName, recordId, Math.max(page, 1), limit);
    Map<String, List<Map<String, Object>>> replies = discussions.findReplies(
        appId, roots.stream().map(row -> text(row, "id")).toList()).stream()
      .collect(Collectors.groupingBy(row -> text(row, "parent_id"), LinkedHashMap::new, Collectors.toList()));
    List<AppRecordDiscussionView> list = roots.stream()
      .map(root -> view(root, replies.getOrDefault(text(root, "id"), List.of()), userId))
      .toList();
    return new AppRecordDiscussionPage(list,
      discussions.count(appId, formName, recordId),
      discussions.countRoots(appId, formName, recordId));
  }

  public AppRecordDiscussionView create(String appId, String formName, String recordId,
                                        AppRecordDiscussionCreateRequest request) {
    String userId = requireAccess(appId, formName, recordId);
    String content = request == null ? "" : Objects.toString(request.content(), "").trim();
    if (content.isEmpty()) throw new ValidationException("讨论内容不能为空");
    if (content.length() > MAX_CONTENT_LENGTH) throw new ValidationException("讨论内容不能超过 2000 字");
    String parentId = resolveRoot(appId, formName, recordId, request.parentId());
    String targetId = request.replyToId() == null || request.replyToId().isBlank() ? request.parentId() : request.replyToId();
    Map<String, Object> replyTarget = resolveReplyTarget(appId, formName, recordId, parentId, targetId);
    String replyToName = replyTarget == null ? null : text(replyTarget, "author_name");
    Map<String, String> members = memberNames(directoryService.directory());
    List<String> mentions = normalizeMentions(request.mentionedUserIds(), members.keySet(), userId);
    String replyRecipient = replyTarget == null ? "" : text(replyTarget, "author_id");
    List<String> mentionRecipients = mentions.stream().filter(id -> !id.equals(replyRecipient)).toList();
    String authorName = members.getOrDefault(userId, userId);
    String discussionId = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", discussionId);
    row.put("form_name", formName);
    row.put("record_id", recordId);
    row.put("parent_id", parentId);
    if (replyToName != null) row.put("reply_to_name", replyToName);
    row.put("content", content);
    row.put("author_id", userId);
    row.put("author_name", authorName);
    row.put("created_at", now);
    if (!mentions.isEmpty()) row.put("mentioned_user_ids", mentions);
    String formLabel = appFormService.findForm(appId, formName).label();
    transactions.executePlatform(appId, () -> {
      discussions.insert(appId, row);
      if (!mentionRecipients.isEmpty()) {
        notificationService.discussionMention(appId, discussionId, authorName, formLabel, content, mentionRecipients);
      }
      if (!replyRecipient.equals(userId) && members.containsKey(replyRecipient)) {
        notificationService.discussionReply(appId, discussionId, authorName, formLabel, content, replyRecipient);
      }
      return null;
    });
    return new AppRecordDiscussionView(discussionId, parentId.isBlank() ? null : parentId, replyToName, content,
      userId, authorName, now.toString(), true, 0, List.of());
  }

  public void delete(String appId, String formName, String recordId, String discussionId) {
    String userId = requireAccess(appId, formName, recordId);
    Map<String, Object> discussion = discussions.find(appId, formName, recordId, discussionId);
    if (discussion == null) throw new NotFoundException("讨论不存在");
    if (!userId.equals(text(discussion, "author_id"))) {
      throw new ForbiddenException("只能删除自己的讨论");
    }
    transactions.executePlatform(appId, () -> {
      discussions.deleteThread(appId, discussionId);
      return null;
    });
  }

  private String requireAccess(String appId, String formName, String recordId) {
    if (!sessionContext.isAuthenticatedUser()) throw new ForbiddenException("请使用个人账号参与讨论");
    String userId = sessionContext.getUserId();
    if (userId == null || userId.isBlank()) throw new ForbiddenException("请使用个人账号参与讨论");
    if (!appNavigationService.canAccessForm(appId, formName)) throw new ForbiddenException("无权访问该表单");
    appRecordService.requireRecordExists(appId, formName, recordId);
    return userId;
  }

  private String resolveRoot(String appId, String formName, String recordId, String parentId) {
    if (parentId == null || parentId.isBlank()) return ROOT_PARENT;
    Map<String, Object> parent = discussions.find(appId, formName, recordId, parentId);
    if (parent == null) throw new ValidationException("回复的讨论不存在");
    String root = text(parent, "parent_id");
    return root.isBlank() ? text(parent, "id") : root;
  }

  private Map<String, Object> resolveReplyTarget(String appId, String formName, String recordId,
                                                 String parentId, String replyToId) {
    if (parentId.isBlank()) return null;
    String targetId = replyToId == null || replyToId.isBlank() ? parentId : replyToId;
    Map<String, Object> target = discussions.find(appId, formName, recordId, targetId);
    if (target == null) throw new ValidationException("回复目标不存在");
    boolean rootTarget = parentId.equals(text(target, "id")) && text(target, "parent_id").isBlank();
    boolean replyTarget = parentId.equals(text(target, "parent_id"));
    if (!rootTarget && !replyTarget) throw new ValidationException("回复目标不属于该讨论");
    return target;
  }

  private List<String> normalizeMentions(List<String> requested, Set<String> activeMembers, String authorId) {
    if (requested == null || requested.isEmpty()) return List.of();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : requested) {
      if (value == null) continue;
      String id = value.trim();
      if (!id.isEmpty()) unique.add(id);
    }
    unique.remove(authorId);
    if (unique.size() > MAX_MENTIONS) throw new ValidationException("最多提及 20 位成员");
    if (!activeMembers.containsAll(unique)) throw new ValidationException("提及成员不存在或已停用");
    return List.copyOf(unique);
  }

  private static Map<String, String> memberNames(Map<String, Object> directory) {
    Map<String, String> result = new HashMap<>();
    for (Map<String, Object> member : objects(directory.get("members"))) {
      result.put(text(member, "id"), text(member, "name"));
    }
    return result;
  }

  private AppRecordDiscussionView view(Map<String, Object> row, List<Map<String, Object>> replyRows,
                                       String userId) {
    List<AppRecordDiscussionView> replies = replyRows.stream()
      .map(reply -> row(reply, List.of(), userId))
      .toList();
    return row(row, replies, userId);
  }

  private AppRecordDiscussionView row(Map<String, Object> row, List<AppRecordDiscussionView> replies,
                                      String userId) {
    String parentId = text(row, "parent_id");
    Object createdAt = row.get("created_at");
    return new AppRecordDiscussionView(
      text(row, "id"),
      parentId.isBlank() ? null : parentId,
      text(row, "reply_to_name").isBlank() ? null : text(row, "reply_to_name"),
      text(row, "content"),
      text(row, "author_id"),
      text(row, "author_name"),
      createdAt == null ? null : createdAt.toString(),
      userId.equals(text(row, "author_id")),
      replies.size(),
      replies
    );
  }
}
