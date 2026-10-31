package dev.flexmodel.apps.dataauthorization;

import dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization;
import dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Control;
import dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.FieldControl;
import dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Selection;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.*;

import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class AppDataAuthorizationService {
  @Inject
  AppDataAuthorizationRepository appDataAuthorizationRepository;
  @Inject
  AppFormService appFormService;
  @Inject
  ProjectService projectService;
  @Inject
  SessionContext context;
  @Inject
  DirectoryService directoryService;

  public Project requireManager(String appId) {
    Project project = projectService.findProject(appId);
    if (project == null || project.getParentProjectId() != null || "default".equals(appId))
      throw new NotFoundException("应用不存在");
    if (context.getUserId() == null || !context.getUserId().equals(project.getOwnerId()))
      throw new ForbiddenException("只有应用所有者可以管理数据权限");
    return project;
  }

  public List<AppDataAuthorization> list(String appId, String form) {
    requireManager(appId);
    requireBusinessForm(appId, form);
    return appDataAuthorizationRepository.find(appId, form);
  }

  public AppDataAuthorization save(String appId, String form, String id, Map<String, Object> request) {
    requireManager(appId);
    if (id != null) requireRule(appId, form, id);
    AppPermissionConditions.keys(request, Set.of("name", "enabled", "subjects", "dataScope", "controls"));
    String name = text(request, "name").trim();
    if (name.isBlank() || name.length() > 255) fail("规则名称需要1至255个字符");
    var members = directoryService.directory();
    var subjects = selections(request.get("subjects"), false, members);
    var scope = selections(request.get("dataScope"), true, members);
    if (!(request.get("enabled") instanceof Boolean)) fail("规则启用状态必须为布尔值");
    if (!(request.get("controls") instanceof List<?>)) fail("控制明细必须为数组");
    AppFormResponse root = requireBusinessForm(appId, form);
    Set<String> targets = new HashSet<>();
    targets.add(form);
    if (root.layout() != null)
      root.layout().items().stream().filter(i -> "SUBFORM".equals(i.type())).forEach(i -> targets.add(i.formName()));
    Set<String> seen = new HashSet<>();
    List<Control> controls = new ArrayList<>();
    for (Object item : (List<?>) request.get("controls")) {
      if (!(item instanceof Map<?, ?>)) fail("控制明细必须为对象");
      Map<String, Object> row = object(item);
      AppPermissionConditions.keys(row, Set.of("formName", "readAll", "writeAll", "allowDelete", "fieldControls", "condition"));
      String target = text(row, "formName");
      if (!targets.contains(target) || !seen.add(target)) fail("明细表单不属于当前主子关系或重复");
      var targetForm = requireBusinessForm(appId, target);
      for (String key : List.of("readAll", "writeAll", "allowDelete"))
        if (!(row.get(key) instanceof Boolean)) fail("权限开关必须为布尔值");
      if (row.get("fieldControls") != null && !(row.get("fieldControls") instanceof Map<?, ?>))
        fail("字段权限必须为对象");
      Map<String, FieldControl> fieldControls = new LinkedHashMap<>();
      object(row.get("fieldControls")).forEach((key, value) -> {
        var field = targetForm.fields().stream().filter(f -> f.name().equals(key)).findFirst().orElseThrow(() -> new ValidationException("权限字段不存在: " + key));
        if (!(value instanceof Map<?, ?>)) fail("字段权限必须为对象");
        var flags = object(value);
        AppPermissionConditions.keys(flags, Set.of("read", "write"));
        if (flags.values().stream().anyMatch(v -> !(v instanceof Boolean))) fail("字段权限必须为布尔值");
        if (field.builtin() && Boolean.TRUE.equals(flags.get("write"))) fail("内置字段不能授权修改");
        fieldControls.put(key, new FieldControl(Boolean.TRUE.equals(flags.get("read")), Boolean.TRUE.equals(flags.get("write"))));
      });
      if (row.get("condition") != null && !(row.get("condition") instanceof Map<?, ?>)) fail("权限条件必须为对象");
      Map<String, Object> condition = row.get("condition") == null ? null : object(row.get("condition"));
      AppPermissionConditions.validate(condition, targetForm.fields());
      controls.add(new Control(target, Boolean.TRUE.equals(row.get("readAll")), Boolean.TRUE.equals(row.get("writeAll")),
        Boolean.TRUE.equals(row.get("allowDelete")), fieldControls, condition));
    }
    if (!seen.contains(form)) fail("必须包含主表权限明细");
    return appDataAuthorizationRepository.save(new AppDataAuthorization(id == null ? UUID.randomUUID().toString() : id, appId, form, name,
      Boolean.TRUE.equals(request.get("enabled")), subjects, scope, List.copyOf(controls)));
  }

  public void delete(String appId, String form, String id) {
    requireManager(appId);
    appDataAuthorizationRepository.delete(requireRule(appId, form, id));
  }

  public void deleteAppRules(String app) {
    appDataAuthorizationRepository.find(app, null).forEach(appDataAuthorizationRepository::delete);
  }

  private AppDataAuthorization requireRule(String app, String form, String id) {
    return appDataAuthorizationRepository.find(app, form).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(() -> new NotFoundException("授权规则不存在"));
  }

  public AppFormResponse requireBusinessForm(String app, String form) {
    var result = appFormService.findForm(app, form);
    if (result.builtin()) fail("不支持内置表单权限控制");
    return result;
  }

  private List<Selection> selections(Object input, boolean scope, Map<String, Object> directory) {
    if (!(input instanceof List<?> list) || list.size() > 100)
      throw new ValidationException("人员选择必须为不超过100项的数组");
    List<Selection> result = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?>)) fail("人员选择项必须为对象");
      var value = object(item);
      AppPermissionConditions.keys(value, Set.of("type", "ids", "includeChildren"));
      String type = text(value, "type");
      boolean entity = Set.of("USER", "ROLE", "ORGANIZATION").contains(type);
      if (!entity && (!scope || !Set.of("SELF", "DIRECT_REPORTS", "ALL_REPORTS", "ALL").contains(type)))
        fail("未知人员选择类型");
      if (value.containsKey("includeChildren") && (!(value.get("includeChildren") instanceof Boolean) || Boolean.TRUE.equals(value.get("includeChildren")) && !type.equals("ORGANIZATION")))
        fail("只有组织支持包含子组织");
      List<String> ids = List.of();
      if (entity) {
        if (!(value.get("ids") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 800 || raw.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank()))
          fail("成员ID必须为非空字符串数组");
        ids = new ArrayList<>(new LinkedHashSet<>(strings(value.get("ids"))));
        String key = switch (type) {
          case "USER" -> "members";
          case "ROLE" -> "roles";
          default -> "organizations";
        };
        Set<String> known = new HashSet<>(objects(directory.get(key)).stream().map(m -> text(m, "id")).toList());
        if (!known.containsAll(ids)) fail("成员、角色或组织不存在");
      } else if (value.containsKey("ids") && (!(value.get("ids") instanceof List<?> emptyIds) || !emptyIds.isEmpty()))
        fail("动态范围不接受指定成员ID");
      result.add(new Selection(type, List.copyOf(ids), Boolean.TRUE.equals(value.get("includeChildren"))));
    }
    if (result.stream().anyMatch(s -> s.type().equals("ALL")) && result.size() != 1)
      fail("全部数据范围不能与其他范围混选");
    return List.copyOf(result);
  }

  public void reconcile(String app, Set<String> liveForms, Map<String, Set<String>> children) {
    for (var rule : appDataAuthorizationRepository.find(app, null)) {
      if (!liveForms.contains(rule.formName())) {
        appDataAuthorizationRepository.delete(rule);
        continue;
      }
      var controls = rule.controls().stream().filter(c -> c.formName().equals(rule.formName()) || children.getOrDefault(rule.formName(), Set.of()).contains(c.formName())).toList();
      if (controls.size() != rule.controls().size())
        appDataAuthorizationRepository.save(new AppDataAuthorization(rule.id(), app, rule.formName(), rule.name(), rule.enabled(), rule.subjects(), rule.dataScope(), controls));
    }
  }

  private static void fail(String message) {
    throw new ValidationException(message);
  }
}
