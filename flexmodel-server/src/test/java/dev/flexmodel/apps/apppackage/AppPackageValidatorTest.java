package dev.flexmodel.apps.apppackage;

import dev.flexmodel.apps.apppackage.AppPackageValidator.Result;
import dev.flexmodel.apps.apppackage.AppPackageValidator.SystemRecordLookup;
import dev.flexmodel.apps.apppackage.AppPackageZip.Content;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.apps.navigation.dto.NavDestinationRecord;
import dev.flexmodel.apps.navigation.dto.NavVisibilityRule;
import dev.flexmodel.apps.workbench.dto.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.flexmodel.apps.apppackage.dto.AppPackage.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPackageValidatorTest {
  AppPackageValidator validator = new AppPackageValidator();
  SystemRecordLookup system = (form, id) -> "u1".equals(id);

  Map<String, Object> directory() {
    return Map.of(
      "members", List.of(Map.of("id", "user_1")),
      "roles", List.of(Map.of("id", "role_1")),
      "organizations", List.of(Map.of("id", "org_1")));
  }

  Result validate(Content content) {
    return validator.validate(content, directory(), system);
  }

  Set<String> codes(Result result) {
    return result.issues().stream().map(ValidationIssue::code).collect(Collectors.toSet());
  }

  AppFormFieldRecord field(String name, String type) {
    return new AppFormFieldRecord(name, name, type, false, false, null, null, null);
  }

  AppFormFieldRecord required(String name, String type) {
    return new AppFormFieldRecord(name, name, type, true, false, null, null, null);
  }

  AppFormFieldRecord reference(String name, String target) {
    return new AppFormFieldRecord(name, name, "REFERENCE", false, false, null, target, null);
  }

  List<AppFormItemRecord> layoutOf(AppFormRecord record) {
    return record.fields().stream().filter(item -> !item.builtin())
      .map(item -> new AppFormItemRecord("FIELD", item.name(), item.label(), null, "FULL", null, null, null, null))
      .toList();
  }

  AppFormRecord form(String name, List<AppFormFieldRecord> fields) {
    List<AppFormItemRecord> items = fields.stream().filter(item -> !item.builtin())
      .map(item -> new AppFormItemRecord("FIELD", item.name(), item.label(), null, "FULL", null, null, null, null))
      .toList();
    return new AppFormRecord(name, name, "", "name", "name",
      LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0),
      fields, new AppFormLayoutRecord(items), List.of());
  }

  AppFormRecord orderForm() {
    return form("f_order", List.of(required("name", "TEXT"), field("amount", "NUMBER")));
  }

  AppFormRecord customerForm() {
    return form("f_customer", List.of(required("name", "TEXT")));
  }

  Manifest manifest(List<AppFormRecord> forms, Map<String, List<Map<String, Object>>> data) {
    return new Manifest(FORMAT, VERSION, "2026-10-30T00:00:00Z", new ManifestSource("app_a", "test"),
      forms.stream().map(record -> new ManifestForm(record.name(),
        record.fields().stream().filter(item -> !item.builtin()).toList().size(),
        data.containsKey(record.name()),
        data.containsKey(record.name()) ? (long) data.get(record.name()).size() : null)).toList(),
      List.of());
  }

  Content content(List<AppFormRecord> forms, List<AppNavRecord> navigation,
                  List<WorkbenchExport> workbenches, Map<String, List<Map<String, Object>>> data) {
    return new Content(manifest(forms, data), new AppInfo("测试应用", "", "Boxes", "blue"),
      forms, navigation, workbenches, List.of(), List.of(), data, Map.of());
  }

  Content content(List<AppFormRecord> forms, Map<String, List<Map<String, Object>>> data) {
    return content(forms, List.of(), List.of(), data);
  }

  AppNavRecord navEntry(String id, String name, String destinationType, String destinationId,
                        String parentId, List<NavVisibilityRule> visibility) {
    return new AppNavRecord(id, "ENTRY", name, null, parentId,
      destinationType == null ? null : new NavDestinationRecord(destinationType, destinationId),
      null, visibility);
  }

  @org.junit.jupiter.api.Test
  void acceptsMinimalPackage() {
    var result = validate(content(List.of(orderForm()), Map.of()));
    assertTrue(result.valid(), result.issues().toString());
  }

  @org.junit.jupiter.api.Test
  void acceptsPackageWithDataAndReferences() {
    var order = form("f_order", List.of(required("name", "TEXT"), reference("customer", "f_customer")));
    var result = validate(content(List.of(order, customerForm()), Map.of(
      "f_order", List.of(Map.of("id", "o1", "name", "订单", "customer", "c1")),
      "f_customer", List.of(Map.of("id", "c1", "name", "客户")))));
    assertTrue(result.valid(), result.issues().toString());
  }

  @org.junit.jupiter.api.Test
  void acceptsEmptyPackage() {
    assertTrue(validate(content(List.of(), Map.of())).valid());
  }

  @org.junit.jupiter.api.Test
  void rejectsReservedSystemFormName() {
    var result = validate(content(List.of(form("f_user", List.of(required("name", "TEXT")))), Map.of()));
    assertTrue(codes(result).contains("RESERVED_FORM_NAME"));
  }

  @org.junit.jupiter.api.Test
  void rejectsDuplicateForms() {
    var result = validate(content(List.of(orderForm(), orderForm()), Map.of()));
    assertTrue(codes(result).contains("FORM_DUPLICATED"));
  }

  @org.junit.jupiter.api.Test
  void rejectsSearchFieldMismatchAndMissingDisplayField() {
    var record = new AppFormRecord("f_order", "订单", "", "name", "amount",
      LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0),
      List.of(required("name", "TEXT"), field("amount", "NUMBER")),
      new AppFormLayoutRecord(List.of()), List.of());
    var result = validate(content(List.of(record), Map.of()));
    assertTrue(codes(result).contains("SEARCH_FIELD_INVARIANT"));
    var missing = new AppFormRecord("f_order", "订单", "", "ghost", "ghost",
      LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0),
      List.of(required("name", "TEXT")), new AppFormLayoutRecord(List.of()), List.of());
    assertTrue(codes(validate(content(List.of(missing), Map.of()))).contains("FORM_INVALID"));
  }

  @org.junit.jupiter.api.Test
  void rejectsInvalidFieldTypesOptionsAndConfigs() {
    var result = validate(content(List.of(form("f_order", List.of(
        required("name", "TEXT"), field("bad", "WEIRD"),
        new AppFormFieldRecord("with_options", "x", "TEXT", false, false,
          List.of(new dev.flexmodel.apps.form.dto.AppFormFieldOptionResponse("1", "一", null, true)), null, null),
        new AppFormFieldRecord("wrong_ref", "x", "TEXT", false, false, null, "f_other", null)))),
      Map.of()));
    var found = codes(result);
    assertTrue(found.contains("FIELD_INVALID"));
    assertTrue(found.contains("FIELD_OPTIONS_INVALID"));
    assertTrue(found.contains("FIELD_REFERENCE_INVALID"));
  }

  @org.junit.jupiter.api.Test
  void rejectsReferenceOutsidePackageAndBlankTarget() {
    var result = validate(content(List.of(form("f_order", List.of(
      required("name", "TEXT"), reference("customer", "f_missing")))), Map.of()));
    assertTrue(codes(result).contains("FIELD_REFERENCE_INVALID"), result.issues().toString());

    var blank = validate(content(List.of(form("f_order", List.of(
      required("name", "TEXT"), reference("customer", "")))), Map.of()));
    assertTrue(codes(blank).contains("FIELD_REFERENCE_INVALID"));
  }

  @org.junit.jupiter.api.Test
  void rejectsNavigationProblems() {
    var forms = List.of(orderForm());
    var parentMismatch = List.of(new AppNavRecord("nav_1", "GROUP", "分组", null, null,
      null, List.of(navEntry("nav_2", "订单", "FORM", "f_order", "nav_ghost", null)), null));
    var result = validate(content(forms, parentMismatch, List.of(), Map.of()));
    assertTrue(codes(result).contains("NAV_PARENT_MISMATCH"), result.issues().toString());

    var destinationMissing = List.of(navEntry("nav_1", "订单", "FORM", "f_ghost", null, null));
    assertTrue(codes(validate(content(forms, destinationMissing, List.of(), Map.of())))
      .contains("NAV_INVALID"));

    var duplicated = List.of(navEntry("nav_1", "订单", "FORM", "f_order", null, null),
      navEntry("nav_1", "重复", "FORM", "f_order", null, null));
    assertTrue(codes(validate(content(forms, duplicated, List.of(), Map.of())))
      .contains("NAV_INVALID"));

    var groupWithDestination = List.of(new AppNavRecord("nav_1", "GROUP", "分组", null, null,
      new NavDestinationRecord("BUILTIN", "workbench"), List.of(), null));
    assertTrue(codes(validate(content(forms, groupWithDestination, List.of(), Map.of())))
      .contains("NAV_INVALID"));
  }

  @org.junit.jupiter.api.Test
  void rejectsVisibilityOfUnknownDirectoryMembers() {
    var navs = List.of(navEntry("nav_1", "订单", "FORM", "f_order", null, List.of(
      new NavVisibilityRule("USER", "user_1", null),
      new NavVisibilityRule("ROLE", "role_ghost", null))));
    var result = validate(content(List.of(orderForm()), navs, List.of(), Map.of()));
    assertTrue(codes(result).contains("DIRECTORY_ROLE_MISSING"));
    assertFalse(codes(result).contains("DIRECTORY_MEMBER_MISSING"), result.issues().toString());
  }

  @org.junit.jupiter.api.Test
  void rejectsWorkbenchShortcutWithoutFormNavEntry() {
    var forms = List.of(orderForm());
    var shortcut = new WorkbenchExport("w1", "工作台", null, new WorkbenchLayoutRecord(1, List.of(
      new WorkbenchComponentRecord("c1", "待办", new WorkbenchPlacementRecord(0, 0, 4, 4), new WorkbenchTasksConfig()),
      new WorkbenchComponentRecord("c2", "快捷", new WorkbenchPlacementRecord(4, 0, 4, 4),
        new WorkbenchShortcutsConfig(List.of("nav_1"), 2)))));
    var withFormNav = content(forms, List.of(navEntry("nav_1", "订单", "FORM", "f_order", null, null)),
      List.of(shortcut), Map.of());
    assertTrue(validate(withFormNav).valid(), validate(withFormNav).issues().toString());

    var withoutNav = content(forms, List.of(), List.of(shortcut), Map.of());
    var result = validate(withoutNav);
    assertFalse(result.valid());
    assertTrue(codes(result).contains("WORKBENCH_INVALID"));

    var groupOnlyNav = content(forms,
      List.of(new AppNavRecord("nav_g", "GROUP", "分组", null, null, null, List.of(), null)),
      List.of(shortcut), Map.of());
    assertTrue(codes(validate(groupOnlyNav)).contains("WORKBENCH_INVALID"));
  }

  @org.junit.jupiter.api.Test
  void rejectsManifestStatsMismatch() {
    var forms = List.of(orderForm());
    var data = Map.<String, List<Map<String, Object>>>of(
      "f_order", List.of(Map.of("id", "o1", "name", "订单", "amount", 1)));
    var honest = new Content(manifest(forms, data), new AppInfo("App", "", "Boxes", "blue"),
      forms, List.of(), List.of(), List.of(), List.of(), data, Map.of());
    assertTrue(validate(honest).valid());

    var forged = new Content(new Manifest(FORMAT, VERSION, "t", new ManifestSource("a", "t"),
      List.of(new ManifestForm("f_order", 9, true, 99L)), List.of()),
      new AppInfo("App", "", "Boxes", "blue"),
      forms, List.of(), List.of(), List.of(), List.of(), data, Map.of());
    var result = validate(forged);
    assertTrue(codes(result).contains("MANIFEST_MISMATCH"));

    var declaredButMissing = new Content(new Manifest(FORMAT, VERSION, "t", new ManifestSource("a", "t"),
      List.of(new ManifestForm("f_order", 2, true, 5L)), List.of()),
      new AppInfo("App", "", "Boxes", "blue"),
      forms, List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of());
    assertTrue(codes(validate(declaredButMissing)).contains("MANIFEST_MISMATCH"));
  }

  @org.junit.jupiter.api.Test
  void rejectsUnknownVersionAndFormat() {
    var forms = List.of(orderForm());
    var bad = new Content(new Manifest("other-format", VERSION, "t", new ManifestSource("a", "t"),
      List.of(), List.of()), new AppInfo("App", "", "Boxes", "blue"),
      forms, List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of());
    assertTrue(codes(validate(bad)).contains("PACKAGE_FORMAT"));

    var future = new Content(new Manifest(FORMAT, 99, "t", new ManifestSource("a", "t"),
      List.of(), List.of()), new AppInfo("App", "", "Boxes", "blue"),
      forms, List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of());
    assertTrue(codes(validate(future)).contains("PACKAGE_VERSION"));
  }

  @org.junit.jupiter.api.Test
  void toleratesRowProblems() {
    var order = form("f_order", List.of(required("name", "TEXT"), field("amount", "NUMBER")));
    var data = Map.<String, List<Map<String, Object>>>of("f_order", List.of(
      Map.of("id", "o1", "name", "订单"),
      Map.of("id", "o1", "name", "重复主键"),
      Map.of("id", "o2", "ghost_field", "x"),
      Map.of("id", "o3", "name", "类型错误", "amount", "not-a-number")));
    var result = validate(content(List.of(order), data));
    assertTrue(result.valid(), result.issues().toString());
    assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("数据主键重复")),
      result.warnings().toString());
    assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("必填字段缺失")),
      result.warnings().toString());
    assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("数据字段不存在已忽略")),
      result.warnings().toString());
    assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("必须是有效数字")),
      result.warnings().toString());
  }

  @org.junit.jupiter.api.Test
  void toleratesDanglingBusinessAndSystemReferences() {
    var order = form("f_order", List.of(required("name", "TEXT"), reference("customer", "f_customer"),
      reference("owner", "f_user")));
    var data = Map.<String, List<Map<String, Object>>>of("f_order", List.of(
      Map.of("id", "o1", "name", "订单", "customer", "ghost", "owner", "u1"),
      Map.of("id", "o2", "name", "订单2", "customer", "c1", "owner", "ghost_user")));
    var result = validate(content(List.of(order, customerForm()), data));
    assertTrue(result.valid(), result.issues().toString());
    assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("业务引用记录不在包内")),
      result.warnings().toString());
    assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("引用的系统目录记录在目标环境不存在")),
      result.warnings().toString());
  }

  @org.junit.jupiter.api.Test
  void skipsAuthorizationWithUnknownDirectoryMember() {
    var rule = new AuthorizationExport("f_order", "规则", true,
      List.of(new dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Selection("USER", List.of("user_1"), false)),
      List.of(), List.of(new dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Control(
      "f_order", true, true, false, Map.of(), null)));
    var result = validate(new Content(manifest(List.of(orderForm()), Map.of()),
      new AppInfo("App", "", "Boxes", "blue"), List.of(orderForm()), List.of(), List.of(),
      List.of(), List.of(rule), Map.of(), Map.of()));
    assertTrue(result.valid(), result.issues().toString());
    assertTrue(result.warnings().isEmpty(), result.warnings().toString());

    var missing = new AuthorizationExport("f_order", "规则", true,
      List.of(new dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Selection("USER", List.of("ghost"), false)),
      List.of(), List.of(new dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Control(
      "f_order", true, true, false, Map.of(), null)));
    var warned = validate(new Content(manifest(List.of(orderForm()), Map.of()),
      new AppInfo("App", "", "Boxes", "blue"), List.of(orderForm()), List.of(), List.of(),
      List.of(), List.of(missing), Map.of(), Map.of()));
    assertTrue(warned.valid(), warned.issues().toString());
    assertTrue(warned.warnings().stream().anyMatch(warning -> warning.contains("目标目录中不存在用户: ghost")),
      warned.warnings().toString());
  }

  @org.junit.jupiter.api.Test
  void acceptsAnyFieldNameWithoutEscapeBreakingCharacters() {
    var record = form("f_member", List.of(required("name", "TEXT"), field("memberType", "TEXT"),
      field("joinDate", "DATE"), field("会员等级", "TEXT"), field("first-name", "TEXT")));
    var result = validate(content(List.of(record), Map.of()));
    assertTrue(result.valid(), result.issues().toString());

    var unsafe = form("f_bad", List.of(required("name", "TEXT"), field("bad\"name", "TEXT")));
    var rejected = validate(content(List.of(unsafe), Map.of()));
    assertFalse(rejected.valid(), rejected.issues().toString());
  }

  @org.junit.jupiter.api.Test
  void unknownFieldConfigIsToleratedWhenNull() {
    AppFieldConfig config = null;
    var record = new AppFormRecord("f_order", "订单", "", "name", "name",
      LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0),
      List.of(required("name", "TEXT"), new AppFormFieldRecord("amount", "金额", "DECIMAL", false, false, null, null, config)),
      new AppFormLayoutRecord(List.of(
        new AppFormItemRecord("FIELD", "name", "名称", null, "FULL", null, null, null, null),
        new AppFormItemRecord("FIELD", "amount", "金额", null, "FULL", null, null, null, null))),
      List.of());
    assertTrue(validate(content(List.of(record), Map.of())).valid());
  }

  @org.junit.jupiter.api.Test
  void validatesTypedViewConfiguration() {
    var record = formWithViews(List.of(
      new AppFormViewTabRecord("all", "全部", "TABLE", null, null, List.of(), AppViewConfig.empty()),
      new AppFormViewTabRecord("board", "看板", "KANBAN",
        Map.of("_or", List.of(Map.of("status", Map.of("_eq", "1")))),
        new AppViewSort("name", "ASC"), List.of("name", "start"),
        new AppViewConfig("status", "name", null, null, null, null, null, null, null, null, null)),
      new AppFormViewTabRecord("cal", "日历", "CALENDAR", null, null, List.of(),
        new AppViewConfig(null, "name", null, null, "start", "end", "MONTH", null, null, null, null))));
    assertTrue(validate(content(List.of(record), Map.of())).valid());
  }

  @org.junit.jupiter.api.Test
  void rejectsUnknownViewTypeAndMissingConfig() {
    var unknownType = formWithViews(List.of(
      new AppFormViewTabRecord("bad", "地图", "MAP", null, null, List.of(), null)));
    assertTrue(codes(validate(content(List.of(unknownType), Map.of()))).contains("VIEW_TAB_INVALID"));

    var missingConfig = formWithViews(List.of(
      new AppFormViewTabRecord("board", "看板", "KANBAN", null, null, List.of(), null)));
    assertTrue(codes(validate(content(List.of(missingConfig), Map.of()))).contains("VIEW_TAB_INVALID"));
  }

  private AppFormRecord formWithViews(List<AppFormViewTabRecord> views) {
    var fields = List.of(
      required("name", "TEXT"),
      field("status", "SELECT"),
      field("start", "DATE"),
      field("end", "DATE"));
    List<AppFormItemRecord> items = fields.stream()
      .map(item -> new AppFormItemRecord("FIELD", item.name(), item.label(), null, "FULL", null, null, null, null))
      .toList();
    return new AppFormRecord("f_order", "订单", "", "name", "name",
      LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0),
      fields, new AppFormLayoutRecord(items), views);
  }
}
