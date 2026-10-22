package dev.flexmodel.directory;

import dev.flexmodel.common.ValidationException;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

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
      List<Map<String, Object>> roles = session.data().findAll("f_role", new Query());
      List<Map<String, Object>> organizations = session.data().findAll("f_department", new Query());
      var userRoles = session.data().findAll("f_user_role", new Query());
      var userDepartments = session.data().findAll("f_user_department", new Query());
      List<Map<String, Object>> members = new ArrayList<>();
      for (var user : session.data().findAll("f_user", new Query()))
        if ("ACTIVE".equals(text(user, "status"))) {
          String id = text(user, "id");
          List<String> roleIds = userRoles.stream().filter(item -> id.equals(text(item, "user_id"))).map(item -> text(item, "role_id")).sorted().toList();
          List<String> orgIds = userDepartments.stream().filter(item -> id.equals(text(item, "user_id"))).map(item -> text(item, "department_id")).sorted().toList();
          Map<String, Object> member = new LinkedHashMap<>(Map.of("id", id, "name", text(user, "name"), "roleIds", roleIds, "organizationIds", orgIds,
            "role", roles.stream().filter(item -> roleIds.contains(text(item, "id"))).map(item -> text(item, "name")).reduce((a, b) -> a + "、" + b).orElse(""),
            "organization", orgIds.isEmpty() ? "" : orgIds.getFirst()));
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private static List<Map<String, Object>> objects(Object value) {
    return value instanceof List<?> list ? list.stream().map(DirectoryService::object).toList() : List.of();
  }

  private static List<String> strings(Object value) {
    return value instanceof Collection<?> list ? list.stream().map(String::valueOf).toList()
      : value instanceof String string && !string.isBlank() ? List.of(string) : List.of();
  }

  private static String text(Map<String, Object> value, String key) {
    return Objects.toString(value.get(key), "");
  }

  private static Object path(Map<String, Object> data, String path) {
    Object current = data;
    for (String part : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) current = map.get(part);
      else if (current instanceof List<?> list && part.matches("\\d+") && Integer.parseInt(part) < list.size())
        current = list.get(Integer.parseInt(part));
      else return null;
    }
    return current;
  }
}
