package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.*;

import static dev.flexmodel.common.Values.*;

/**
 * 目录可见范围存储在项目 metadata 中，父目录限制始终生效。
 */
@RequestScoped
public class FolderPermissions {
  public record Member(String type, String id, Boolean includeChildren) {
  }

  @Inject
  ProjectService projects;
  @Inject
  SessionContext context;
  @Inject
  DirectoryService directory;
  private Map<String, Object> directorySnapshot;

  private Map<String, Object> directorySnapshot() {
    if (directorySnapshot == null) directorySnapshot = directory.directory();
    return directorySnapshot;
  }

  private Project project(Bucket bucket) {
    return "PROJECT".equals(bucket.getOwnerType()) ? projects.findProject(bucket.getOwnerId()) : null;
  }

  public boolean canManage(Bucket bucket) {
    Project project = project(bucket);
    if (context.getUserId() == null) return false;
    if (project != null && context.getUserId().equals(project.getOwnerId())
      || context.getPermissions() != null && context.getPermissions().contains("*")) return true;
    return objects(directorySnapshot().get("members")).stream().anyMatch(member ->
      context.getUserId().equals(text(member, "id")) && strings(member.get("roleIds")).contains("admin"));
  }

  private Map<String, Object> policies(Bucket bucket) {
    Project project = project(bucket);
    return project == null ? Map.of() : object(object(object(project.getMetadata()).get("storageFolders")).get(bucket.getName()));
  }

  public boolean isRestricted(Bucket bucket, String value) {
    return !objects(policies(bucket).get(path(value))).isEmpty();
  }

  public static String path(String value) {
    String normalized = value == null ? "" : value.replaceAll("^/+|/+$", "");
    if (normalized.contains("\\") || normalized.chars().anyMatch(c -> c < 32 || c == 127)
      || Arrays.stream(normalized.split("/", -1)).anyMatch(p -> p.equals(".") || p.equals("..") || p.isEmpty() && !normalized.isEmpty()))
      throw new ValidationException("目录路径不正确");
    return normalized;
  }

  public boolean visible(Bucket bucket, String value) {
    String target = path(value);
    var policies = policies(bucket);
    if (policies.isEmpty() || canManage(bucket)) return true;
    Map<String, Object> snapshot = null;
    for (var entry : policies.entrySet()) {
      if (!target.equals(entry.getKey()) && !target.startsWith(entry.getKey() + "/")) continue;
      var members = objects(entry.getValue());
      if (members.isEmpty()) continue;
      if (context.getUserId() == null) return false;
      if (snapshot == null) snapshot = directorySnapshot();
      var rules = members.stream().map(member -> Map.<String, Object>of(
        "source", switch (text(member, "type")) {
          case "USER" -> "users";
          case "ROLE" -> "roles";
          case "ORGANIZATION" -> "organizations";
          default -> throw new ValidationException("目录成员类型不正确");
        }, "values", List.of(text(member, "id")), "scope", "self",
        "includeChildren", Boolean.TRUE.equals(member.get("includeChildren")))).toList();
      if (!directory.resolve(rules, context.getUserId(), Map.of(), snapshot).contains(context.getUserId()))
        return false;
    }
    return true;
  }

  public void requireAccess(Bucket bucket, String path) {
    if (!visible(bucket, path)) throw new ForbiddenException("无权访问此目录");
  }

  public void requireTreeAccess(Bucket bucket, String value) {
    String target = path(value);
    requireAccess(bucket, target);
    for (String key : policies(bucket).keySet())
      if (key.startsWith(target + "/")) requireAccess(bucket, key);
  }

  public List<Map<String, Object>> members(Bucket bucket, String path) {
    requireManager(bucket);
    return objects(policies(bucket).get(path(path)));
  }

  public void save(Bucket bucket, String value, List<Member> members) {
    edit(bucket, value, value, members, () -> {
    }, () -> {
    });
  }

  public void edit(Bucket bucket, String source, String value, List<Member> members, Runnable move, Runnable rollback) {
    requireManager(bucket);
    String target = path(value);
    // 根目录与表单附件区不提供目录设置。
    if (target.isEmpty() || target.equals(bucket.getOwnerId()) || target.startsWith(bucket.getOwnerId() + "/"))
      throw new ValidationException("系统目录不能修改");
    if (members == null || members.stream().anyMatch(m -> m == null || m.type() == null || m.id() == null || m.id().isBlank()
      || !Set.of("USER", "ROLE", "ORGANIZATION").contains(m.type())))
      throw new ValidationException("目录授权成员格式不正确");
    var next = new LinkedHashMap<>(policies(bucket));
    if (!source.equals(target)) {
      for (String key : new ArrayList<>(next.keySet())) {
        if (key.equals(source) || key.startsWith(source + "/"))
          next.put(target + key.substring(source.length()), next.remove(key));
      }
    }
    if (members.isEmpty()) next.remove(target);
    else next.put(target, members.stream().map(m -> Map.<String, Object>of("type", m.type(), "id", m.id(),
      "includeChildren", Boolean.TRUE.equals(m.includeChildren()))).toList());
    move.run();
    try {
      persist(bucket, next);
    } catch (RuntimeException failure) {
      try {
        rollback.run();
      } catch (RuntimeException rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    }
  }

  public void relocate(Bucket bucket, String source, String target) {
    var next = new LinkedHashMap<>(policies(bucket));
    boolean changed = false;
    for (String key : new ArrayList<>(next.keySet())) {
      if (key.equals(source) || key.startsWith(source + "/")) {
        Object members = next.remove(key);
        if (target != null) next.put(target + key.substring(source.length()), members);
        changed = true;
      }
    }
    if (changed) persist(bucket, next);
  }

  private void requireManager(Bucket bucket) {
    if (!canManage(bucket)) throw new ForbiddenException("只有所有者或管理员可以设置目录权限");
  }

  private void persist(Bucket bucket, Map<String, Object> policies) {
    Project project = project(bucket);
    Object previous = project.getMetadata();
    var metadata = new LinkedHashMap<>(object(project.getMetadata()));
    var folders = new LinkedHashMap<>(object(metadata.get("storageFolders")));
    folders.put(bucket.getName(), policies);
    metadata.put("storageFolders", folders);
    project.setMetadata(metadata);
    try {
      projects.updateProject(project);
    } catch (RuntimeException failure) {
      project.setMetadata(previous);
      throw failure;
    }
  }
}
