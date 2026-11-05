package dev.flexmodel.directory;

import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.codegen.entity.UserRole;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

import static dev.flexmodel.common.Values.*;

/**
 * Shared system directory and member-rule resolution, independent of approval flows.
 */
@ApplicationScoped
public class DirectoryService {
  public static final List<String> SOURCES = List.of("users", "roles", "organizations", "initiator", "node", "variable");
  public static final List<String> SCOPES = List.of("self", "directReports", "allReports", "manager");
  @Inject
  SessionFactory sessions;

  public Map<String, Object> directory() {
    try (Session session = sessions.createSession()) {
      List<Map<String, Object>> roles = session.data().findAll(ReflectionUtils.getModelNameFromClass(Role.class), new Query());
      List<Map<String, Object>> organizations = session.data().findAll(ReflectionUtils.getModelNameFromClass(Department.class), new Query());
      var userRoles = session.data().findAll(ReflectionUtils.getModelNameFromClass(UserRole.class), new Query());
      var userDepartments = session.data().findAll(ReflectionUtils.getModelNameFromClass(UserDepartment.class), new Query());
      List<Map<String, Object>> members = new ArrayList<>();
      for (var user : session.data().findAll(ReflectionUtils.getModelNameFromClass(User.class), new Query()))
        if ("ACTIVE".equals(text(user, "status"))) {
          String id = text(user, "id");
          List<String> roleIds = userRoles.stream().filter(item -> id.equals(text(item, "user_id"))).map(item -> text(item, "role_id")).sorted().toList();
          List<String> orgIds = userDepartments.stream().filter(item -> id.equals(text(item, "user_id"))).map(item -> text(item, "department_id")).sorted().toList();
          Map<String, Object> member = new LinkedHashMap<>(Map.of("id", id, "name", text(user, "name"), "roleIds", roleIds, "organizationIds", orgIds,
            "role", roles.stream().filter(item -> roleIds.contains(text(item, "id"))).map(item -> text(item, "name")).reduce((a, b) -> a + "、" + b).orElse(""),
            "organization", orgIds.isEmpty() ? "" : orgIds.getFirst()));
          member.put("avatar", text(user, "avatar"));
          member.put("managerId", text(user, "manager_user_id"));
          members.add(member);
        }
      members.sort(Comparator.comparing(member -> text(member, "id")));
      return Map.of("members", members, "roles", roles.stream().map(role -> Map.of("id", text(role, "id"), "name", text(role, "name"))).toList(),
        "organizations", organizations.stream().map(org -> Map.of("id", text(org, "id"), "name", text(org, "name"), "parentId", text(org, "parent_id"))).toList());
    }
  }

  public List<String> resolve(List<Map<String, Object>> rules, String initiator, Map<String, Object> variables) {
    return resolve(rules, initiator, variables, directory());
  }

  public List<String> resolve(List<Map<String, Object>> rules, String initiator, Map<String, Object> variables, Map<String, Object> directory) {
    List<Map<String, Object>> members = objects(directory.get("members"));
    List<Map<String, Object>> organizations = objects(directory.get("organizations"));
    Set<String> active = new HashSet<>(members.stream().map(member -> text(member, "id")).toList());
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (var rule : rules) {
      List<String> values = strings(rule.get("values"));
      LinkedHashSet<String> base = new LinkedHashSet<>();
      switch (text(rule, "source")) {
        case "users" -> base.addAll(values);
        case "roles" ->
          members.stream().filter(member -> strings(member.get("roleIds")).stream().anyMatch(values::contains)).forEach(member -> base.add(text(member, "id")));
        case "organizations" -> {
          Set<String> orgs = new HashSet<>(values);
          if (Boolean.TRUE.equals(rule.get("includeChildren"))) {
            boolean changed;
            do {
              changed = false;
              for (var org : organizations)
                if (orgs.contains(text(org, "parentId"))) changed |= orgs.add(text(org, "id"));
            } while (changed);
          }
          members.stream().filter(member -> strings(member.get("organizationIds")).stream().anyMatch(orgs::contains)).forEach(member -> base.add(text(member, "id")));
        }
        case "initiator" -> base.add(initiator);
        case "node" -> values.forEach(node -> base.addAll(strings(object(variables.get("nodeMembers")).get(node))));
        case "variable" -> values.forEach(variable -> base.addAll(strings(path(variables, variable))));
        default -> throw new ValidationException("执行人来源已不支持，请重新选择");
      }
      base.retainAll(active);
      LinkedHashSet<String> scoped = new LinkedHashSet<>();
      switch (text(rule, "scope")) {
        case "self" -> scoped.addAll(base);
        case "manager" ->
          base.forEach(id -> members.stream().filter(member -> text(member, "id").equals(id)).forEach(member -> scoped.add(text(member, "managerId"))));
        case "directReports" ->
          base.forEach(id -> members.stream().filter(member -> text(member, "managerId").equals(id)).forEach(member -> scoped.add(text(member, "id"))));
        case "allReports" -> {
          Set<String> visited = new HashSet<>(base);
          List<String> frontier = new ArrayList<>(base);
          while (!frontier.isEmpty()) {
            String manager = frontier.removeFirst();
            for (var member : members)
              if (manager.equals(text(member, "managerId")) && visited.add(text(member, "id"))) {
                scoped.add(text(member, "id"));
                frontier.add(text(member, "id"));
              }
          }
        }
        default -> throw new ValidationException("未知人员范围");
      }
      scoped.retainAll(active);
      result.addAll(scoped);
      if (result.size() > 1000) throw new ValidationException("单次最多解析 1000 名人员，请收紧人员范围");
    }
    return new ArrayList<>(result);
  }
}
