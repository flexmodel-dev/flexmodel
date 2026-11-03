package dev.flexmodel.apps.apppackage;

import dev.flexmodel.apps.apppackage.AppPackageZip.Content;
import dev.flexmodel.apps.apppackage.dto.AppPackage;
import dev.flexmodel.apps.dataauthorization.dto.AppDataAuthorization.Selection;
import dev.flexmodel.apps.form.AppFieldRules;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.apps.navigation.dto.NavDestinationRecord;
import dev.flexmodel.apps.navigation.dto.NavVisibilityRule;
import dev.flexmodel.apps.workbench.dto.*;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.directory.DirectoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

import static dev.flexmodel.apps.apppackage.dto.AppPackage.*;
import static dev.flexmodel.common.Values.*;

/**
 * 整包导入验证器：上传预览与 commit 前共用同一套结构与引用校验。
 *
 * <p>错误返回 {code, path, message}；目录与系统记录存在性通过外部传入的目录快照与查询回调执行，
 * 便于纯单元测试。源包的 manifest 统计不作为结论，导入时重新统计并比对。</p>
 */
@ApplicationScoped
public class AppPackageValidator {
  private static final Pattern FORM_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
  // 字段标识不限命名风格，仅排除会破坏 SQL 标识符转义的字符。
  private static final Pattern FIELD_NAME = Pattern.compile("^[^\\x00-\\x1f`\"\\[\\]]{1,64}$");
  private static final Pattern NAV_ICON = Pattern.compile("^[A-Z][A-Za-z0-9]{0,63}$");
  private static final Pattern APP_ICON = Pattern.compile("^[A-Z][A-Za-z0-9]{0,63}$");
  private static final Pattern PRIMARY_COLOR = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
  private static final Pattern FLOW_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,120}$");
  private static final Pattern NODE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
  private static final Set<String> NAV_TYPES = Set.of("GROUP", "ENTRY");
  private static final Set<String> BUILTIN_DESTINATIONS = Set.of("workbench", "files", "approval");
  private static final Set<String> ITEM_TYPES = Set.of("FIELD", "SECTION", "SUBFORM");
  private static final Set<String> ITEM_WIDTHS = Set.of("QUARTER", "THIRD", "HALF", "TWO_THIRDS", "THREE_QUARTERS", "FULL");
  private static final Set<String> SYSTEM_FORMS = Set.of("f_user", "f_department", "f_role");
  private static final Set<String> BUILTIN_FIELDS = AppFormService.builtinFieldNames();
  private static final Set<String> FIELD_TYPES = AppFormService.fieldTypes();
  private static final Set<String> OPTION_TYPES = AppFormService.optionFieldTypes();
  private static final Set<String> APPROVAL_KINDS = Set.of(
    "start", "end", "approval", "notification", "gateway", "branch", "create", "update", "query", "delete", "subflow");
  private static final Set<String> DATA_KINDS = Set.of("create", "update", "query", "delete");
  private static final Set<String> CHART_TYPES = Set.of("line", "bar", "pie");
  private static final Set<String> AGGREGATIONS = Set.of("count", "sum", "avg");
  private static final Set<String> PALETTES = Set.of("primary", "multi");
  private static final Set<String> MEASURE_TYPES = Set.of("NUMBER", "DECIMAL");
  private static final Set<String> GROUPABLE_TYPES = Set.of(
    "TEXT", "NTEXT", "PHONE", "EMAIL", "URL", "SERIES", "PICKLIST", "SELECT",
    "NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME", "CLASSIFICATION", "BOOL", "STATE");
  private static final Set<String> DYNAMIC_SCOPES = Set.of("SELF", "DIRECT_REPORTS", "ALL_REPORTS", "ALL");

  @Inject
  DirectoryService directoryService;
  @Inject
  ServerSessions sessions;

  /**
   * 系统目录记录存在性查询（f_user / f_department / f_role）。
   */
  public interface SystemRecordLookup {
    boolean exists(String formName, String id);
  }

  public record Result(List<ValidationIssue> issues, List<String> warnings) {
    public boolean valid() {
      return issues.isEmpty();
    }
  }

  boolean systemRecordExists(String formName, String id) {
    try (var session = sessions.open("system")) {
      return session.data().findById(formName, id).isPresent();
    } catch (RuntimeException error) {
      return false;
    }
  }

  private static final class Issues {
    final List<ValidationIssue> list = new ArrayList<>();

    void add(String code, String path, String message) {
      if (list.size() < 500) list.add(new ValidationIssue(code, path, message));
    }
  }

  private static final class FormInfo {
    final int index;
    final AppFormRecord record;
    final Map<String, AppFormFieldRecord> businessFields = new LinkedHashMap<>();
    final Map<String, AppFormFieldRecord> builtinFields = new LinkedHashMap<>();
    final Set<String> allFields = new LinkedHashSet<>();
    final Set<String> layoutFieldNames = new LinkedHashSet<>();

    FormInfo(int index, AppFormRecord record) {
      this.index = index;
      this.record = record;
      AppFormService.builtinFieldRecords().forEach((name, field) -> {
        builtinFields.put(name, field);
        allFields.add(name);
      });
    }

    String path() {
      return "forms[" + index + "]";
    }

    AppFormFieldRecord resolveField(String name) {
      AppFormFieldRecord field = businessFields.get(name);
      return field != null ? field : builtinFields.get(name);
    }
  }

  public Result validate(Content content) {
    return validate(content, directoryService.directory(), this::systemRecordExists);
  }

  /**
   * 包结构、限额、内部引用、目录身份与记录引用完整性校验。
   */
  public Result validate(Content content, Map<String, Object> directory, SystemRecordLookup system) {
    Issues issues = new Issues();
    List<String> warnings = new ArrayList<>();
    validateManifest(content, issues);
    Map<String, FormInfo> forms = validateForms(content, issues);
    Set<String> navFormEntryIds = validateNavigation(content, forms, directory, issues);
    validateWorkbenches(content, forms, navFormEntryIds, issues);
    Map<String, Map<String, Object>> approvals = validateApprovals(content, forms, directory, issues);
    validateAuthorizations(content, forms, directory, issues, warnings);
    validateData(content, forms, system, issues, warnings);
    return new Result(issues.list, warnings);
  }

  private void validateManifest(Content content, Issues issues) {
    Manifest manifest = content.manifest();
    if (manifest == null) {
      issues.add("MANIFEST_INVALID", "manifest", "缺少 manifest.json");
      return;
    }
    if (!FORMAT.equals(manifest.format())) {
      issues.add("PACKAGE_FORMAT", "manifest.format", "未知的应用包格式: " + manifest.format());
    }
    if (manifest.version() == null || manifest.version() != VERSION) {
      issues.add("PACKAGE_VERSION", "manifest.version", "仅支持协议版本 " + VERSION + ": " + manifest.version());
    }
    if (manifest.source() == null || isBlank(manifest.source().appId())) {
      issues.add("MANIFEST_INVALID", "manifest.source.appId", "manifest 缺少源应用标识");
    }
  }

  private Map<String, FormInfo> validateForms(Content content, Issues issues) {
    Map<String, FormInfo> forms = new LinkedHashMap<>();
    List<AppFormRecord> records = content.forms() == null ? List.of() : content.forms();
    if (records.size() > MAX_FORMS) {
      issues.add("FORM_LIMIT", "forms", "业务表单数量超过 " + MAX_FORMS + " 个上限");
    }
    for (int index = 0; index < records.size(); index++) {
      AppFormRecord record = records.get(index);
      String path = "forms[" + index + "]";
      FormInfo info = new FormInfo(index, record);
      if (record == null) {
        issues.add("FORM_INVALID", path, "表单定义为空");
        continue;
      }
      if (isBlank(record.name()) || !FORM_NAME.matcher(record.name()).matches()) {
        issues.add("FORM_NAME_INVALID", path + ".name", "表单标识不合法: " + record.name());
        continue;
      }
      if (AppFormService.reservedSystemFormNames().contains(record.name())
        || SYSTEM_FORMS.contains(record.name())) {
        issues.add("RESERVED_FORM_NAME", path + ".name", "系统保留表单名不允许出现在业务表单中: " + record.name());
        continue;
      }
      if (forms.put(record.name(), info) != null) {
        issues.add("FORM_DUPLICATED", path + ".name", "表单标识重复: " + record.name());
        continue;
      }
      if (isBlank(record.label()) || record.label().length() > 100) {
        issues.add("FORM_INVALID", path + ".label", "表单名称需要 1 至 100 个字符");
      }
      if (record.description() != null && record.description().length() > 500) {
        issues.add("FORM_INVALID", path + ".description", "表单描述不能超过 500 个字符");
      }
      validateFields(info, issues);
      if (!Objects.equals(record.displayField(), record.searchField())) {
        issues.add("SEARCH_FIELD_INVARIANT", path + ".searchField",
          "searchField 必须等于 displayField: " + record.displayField() + " / " + record.searchField());
      }
      if (!isBlank(record.displayField()) && !info.businessFields.containsKey(record.displayField())) {
        issues.add("FORM_INVALID", path + ".displayField", "主显字段不存在: " + record.displayField());
      }
      validateLayout(info, content, issues);
      validateViewTabs(info, issues);
    }
    validateFieldReferences(forms, issues);
    validateManifestForms(content, forms, issues);
    return forms;
  }

  /**
   * 业务 REFERENCE 的关联表单必须在包内或属于系统目录表单。
   */
  private void validateFieldReferences(Map<String, FormInfo> forms, Issues issues) {
    for (FormInfo info : forms.values()) {
      List<AppFormFieldRecord> fields = info.record.fields() == null ? List.of() : info.record.fields();
      for (int index = 0; index < fields.size(); index++) {
        AppFormFieldRecord field = fields.get(index);
        if (field == null || field.builtin() || !"REFERENCE".equals(field.type())) continue;
        String referenceForm = field.referenceForm();
        if (isBlank(referenceForm) || SYSTEM_FORMS.contains(referenceForm)) continue;
        if (!forms.containsKey(referenceForm)) {
          issues.add("FIELD_REFERENCE_INVALID", info.path() + ".fields[" + index + "].referenceForm",
            "引用表单不在包内: " + referenceForm);
        }
      }
    }
  }

  private void validateFields(FormInfo info, Issues issues) {
    AppFormRecord record = info.record;
    String path = info.path();
    List<AppFormFieldRecord> fields = record.fields() == null ? List.of() : record.fields();
    if (fields.size() > MAX_FIELDS_PER_FORM) {
      issues.add("FIELD_LIMIT", path + ".fields", "单表单字段超过 " + MAX_FIELDS_PER_FORM + " 个上限");
    }
    Set<String> names = new LinkedHashSet<>();
    for (int index = 0; index < fields.size(); index++) {
      AppFormFieldRecord field = fields.get(index);
      String fieldPath = path + ".fields[" + index + "]";
      if (field == null || isBlank(field.name()) || !FIELD_NAME.matcher(field.name()).matches()) {
        issues.add("FIELD_INVALID", fieldPath + ".name", "字段标识不合法");
        continue;
      }
      if (!names.add(field.name())) {
        issues.add("FIELD_DUPLICATED", fieldPath + ".name", "字段标识重复: " + field.name());
        continue;
      }
      info.allFields.add(field.name());
      if (field.builtin()) {
        if (!BUILTIN_FIELDS.contains(field.name())) {
          issues.add("FIELD_INVALID", fieldPath + ".builtin", "未知内置字段: " + field.name());
        } else {
          info.builtinFields.put(field.name(), field);
        }
        continue;
      }
      if (isBlank(field.label()) || field.label().length() > 100) {
        issues.add("FIELD_INVALID", fieldPath + ".label", "字段名称需要 1 至 100 个字符");
      }
      if (isBlank(field.type()) || !FIELD_TYPES.contains(field.type())) {
        issues.add("FIELD_INVALID", fieldPath + ".type", "字段类型不合法: " + field.type());
        continue;
      }
      if (!OPTION_TYPES.contains(field.type()) && field.options() != null) {
        issues.add("FIELD_OPTIONS_INVALID", fieldPath + ".options", "字段类型 " + field.type() + " 不支持选项");
      }
      if (OPTION_TYPES.contains(field.type())) {
        validateOptions(info, field, fieldPath, issues);
      }
      if ("REFERENCE".equals(field.type())) {
        if (isBlank(field.referenceForm())) {
          issues.add("FIELD_REFERENCE_INVALID", fieldPath + ".referenceForm", "引用字段必须声明关联表单");
        } else if (!SYSTEM_FORMS.contains(field.referenceForm())) {
          // 业务引用表单必须也在包内；包内存在性由调用方在 forms 全部收集后复核。
        }
      } else if (field.referenceForm() != null) {
        issues.add("FIELD_REFERENCE_INVALID", fieldPath + ".referenceForm", "非引用字段不允许声明关联表单");
      }
      try {
        AppFieldRules.validateConfig(field.type(), field.config());
      } catch (RuntimeException error) {
        issues.add("FIELD_CONFIG_INVALID", fieldPath + ".config", error.getMessage());
      }
      info.businessFields.put(field.name(), field);
    }
  }

  private void validateOptions(FormInfo info, AppFormFieldRecord field, String fieldPath, Issues issues) {
    if (field.options() == null) return;
    Set<String> ids = new LinkedHashSet<>();
    int defaults = 0;
    for (int index = 0; index < field.options().size(); index++) {
      var option = field.options().get(index);
      String optionPath = fieldPath + ".options[" + index + "]";
      if (option == null || isBlank(option.id()) || isBlank(option.text())) {
        issues.add("FIELD_OPTIONS_INVALID", optionPath, "字段选项的ID和展示文本不能为空");
        continue;
      }
      if (!ids.add(option.id())) {
        issues.add("FIELD_OPTIONS_INVALID", optionPath, "字段选项ID重复: " + option.id());
      }
      if (Boolean.TRUE.equals(option.defaultSelected())) defaults++;
    }
    if (!"MULTISELECT".equals(field.type()) && defaults > 1) {
      issues.add("FIELD_OPTIONS_INVALID", fieldPath + ".options", "单选字段只能设置一个默认选项");
    }
  }

  private void validateLayout(FormInfo info, Content content, Issues issues) {
    AppFormLayoutRecord layout = info.record.layout();
    if (layout == null || layout.items() == null || layout.items().isEmpty()) return;
    String path = info.path() + ".layout.items";
    if (layout.items().size() > MAX_LAYOUT_ITEMS) {
      issues.add("LAYOUT_LIMIT", path, "布局项超过 " + MAX_LAYOUT_ITEMS + " 个上限");
    }
    Set<String> usedFields = new LinkedHashSet<>();
    Set<String> subformKeys = new LinkedHashSet<>();
    boolean hasFieldItem = false;
    for (int index = 0; index < layout.items().size(); index++) {
      AppFormItemRecord item = layout.items().get(index);
      String itemPath = path + "[" + index + "]";
      if (item == null || !ITEM_TYPES.contains(item.type())) {
        issues.add("LAYOUT_INVALID", itemPath + ".type", "布局项类型不合法: " + (item == null ? null : item.type()));
        continue;
      }
      if (item.width() != null && !ITEM_WIDTHS.contains(item.width())) {
        issues.add("LAYOUT_INVALID", itemPath + ".width", "字段宽度类型不合法: " + item.width());
      }
      switch (item.type()) {
        case "FIELD" -> {
          hasFieldItem = true;
          if (isBlank(item.name()) || !info.businessFields.containsKey(item.name())) {
            issues.add("LAYOUT_INVALID", itemPath + ".name", "布局字段不存在: " + item.name());
          } else if (!usedFields.add(item.name())) {
            issues.add("LAYOUT_INVALID", itemPath + ".name", "布局字段重复: " + item.name());
          } else {
            info.layoutFieldNames.add(item.name());
          }
          if (!isBlank(item.formName()) || !isBlank(item.referenceField())) {
            issues.add("LAYOUT_INVALID", itemPath, "字段布局项不允许配置子表单");
          }
        }
        case "SECTION" -> {
          if (isBlank(item.label())) {
            issues.add("LAYOUT_INVALID", itemPath + ".label", "分栏名称不能为空");
          }
          if (!isBlank(item.name()) || !isBlank(item.formName()) || !isBlank(item.referenceField())) {
            issues.add("LAYOUT_INVALID", itemPath, "分栏布局项配置不合法");
          }
        }
        default -> {
          if (!isBlank(item.name())) {
            issues.add("LAYOUT_INVALID", itemPath + ".name", "子表单布局项不允许配置字段名");
          }
          if (isBlank(item.formName()) || isBlank(item.referenceField())) {
            issues.add("LAYOUT_INVALID", itemPath, "子表单布局必须声明 formName 与 referenceField");
            continue;
          }
          if (!subformKeys.add(item.formName() + '\0' + item.referenceField())) {
            issues.add("LAYOUT_INVALID", itemPath, "子表单重复: " + item.formName());
          }
        }
      }
    }
    if (!hasFieldItem) {
      issues.add("LAYOUT_INVALID", path, "非空布局必须包含字段项");
    }
  }

  private void validateViewTabs(FormInfo info, Issues issues) {
    List<AppFormViewTabRecord> viewTabs = info.record.viewTabs();
    if (viewTabs == null || viewTabs.isEmpty()) return;
    String path = info.path() + ".viewTabs";
    Set<String> ids = new LinkedHashSet<>();
    Set<String> labels = new LinkedHashSet<>();
    int customViews = 0;
    for (int index = 0; index < viewTabs.size(); index++) {
      AppFormViewTabRecord tab = viewTabs.get(index);
      String tabPath = path + "[" + index + "]";
      if (tab == null) {
        issues.add("VIEW_TAB_INVALID", tabPath, "视图配置不能为空");
        continue;
      }
      String id = isBlank(tab.id()) ? null : tab.id().trim();
      if (id != null && !ids.add(id)) {
        issues.add("VIEW_TAB_INVALID", tabPath + ".id", "视图标识重复: " + id);
      }
      customViews++;
      if (customViews > MAX_VIEW_TABS) {
        issues.add("VIEW_TAB_LIMIT", path, "视图数量超过 " + MAX_VIEW_TABS + " 个自定义视图");
        return;
      }
      if (id != null && id.length() > 64) {
        issues.add("VIEW_TAB_INVALID", tabPath + ".id", "视图标识不能超过 64 个字符");
      }
      if (isBlank(tab.label()) || tab.label().length() > 32) {
        issues.add("VIEW_TAB_INVALID", tabPath + ".label", "视图名称需要 1 至 32 个字符");
      } else if (!labels.add(tab.label())) {
        issues.add("VIEW_TAB_INVALID", tabPath + ".label", "视图名称重复: " + tab.label());
      }
      String type = isBlank(tab.type()) ? "TABLE" : tab.type().trim().toUpperCase(Locale.ROOT);
      if (!AppFormService.viewTypes().contains(type)) {
        issues.add("VIEW_TAB_INVALID", tabPath + ".type", "视图类型不合法: " + tab.type());
        continue;
      }
      if (tab.filter() != null) {
        validateViewTabFilter(info, tab.filter(), tabPath + ".filter", issues);
      }
      validateViewTabSort(info, tab.sort(), tabPath + ".sort", issues);
      validateViewDisplayFields(info, tab.displayFields(), tabPath + ".displayFields", issues);
      validateViewTabConfig(info, type, tab.config(), tabPath + ".config", issues);
    }
  }

  private void validateViewTabSort(FormInfo info, AppViewSort sort, String path, Issues issues) {
    if (sort == null) return;
    if (isBlank(sort.field())) {
      issues.add("VIEW_TAB_INVALID", path + ".field", "排序字段不能为空");
      return;
    }
    String field = sort.field().trim();
    if (!info.allFields.contains(field)) {
      issues.add("VIEW_TAB_INVALID", path + ".field", "排序字段不存在: " + field);
    }
    if (!isBlank(sort.order()) && !Set.of("ASC", "DESC").contains(sort.order().trim().toUpperCase(Locale.ROOT))) {
      issues.add("VIEW_TAB_INVALID", path + ".order", "排序方向不合法: " + sort.order());
    }
  }

  private void validateViewDisplayFields(
    FormInfo info,
    List<String> displayFields,
    String path,
    Issues issues
  ) {
    if (displayFields == null || displayFields.isEmpty()) return;
    if (displayFields.size() > 60) {
      issues.add("VIEW_TAB_INVALID", path, "显示字段不能超过 60 个");
    }
    Set<String> seen = new LinkedHashSet<>();
    for (int index = 0; index < displayFields.size(); index++) {
      String fieldName = displayFields.get(index);
      if (isBlank(fieldName) || "id".equals(fieldName) || !info.allFields.contains(fieldName)) {
        issues.add("VIEW_TAB_INVALID", path + "[" + index + "]", "显示字段不存在: " + fieldName);
      } else if (!seen.add(fieldName)) {
        issues.add("VIEW_TAB_INVALID", path + "[" + index + "]", "显示字段重复: " + fieldName);
      }
    }
  }

  private void validateViewTabConfig(
    FormInfo info,
    String type,
    AppViewConfig config,
    String path,
    Issues issues
  ) {
    if (config == null) {
      if (Set.of("KANBAN", "CALENDAR", "HIERARCHY", "GANTT").contains(type)) {
        issues.add("VIEW_TAB_INVALID", path, "视图缺少专属配置");
      }
      return;
    }
    switch (type) {
      case "KANBAN" -> {
        AppFormFieldRecord group = requireViewField(info, config.groupField(), path + ".groupField", "看板分组字段", issues);
        if (group != null && !Set.of("PICKLIST", "SELECT", "CLASSIFICATION", "BOOL", "REFERENCE").contains(group.type())) {
          issues.add("VIEW_TAB_INVALID", path + ".groupField", "看板分组字段类型不支持: " + group.label());
        }
        requireViewField(info, config.titleField(), path + ".titleField", "卡片标题字段", issues);
      }
      case "CALENDAR" -> {
        AppFormFieldRecord start = requireViewField(info, config.startField(), path + ".startField", "日历开始字段", issues);
        AppFormFieldRecord end = requireViewField(info, config.endField(), path + ".endField", "日历结束字段", issues);
        validateViewDateFields(start, end, path, issues);
        requireViewField(info, config.titleField(), path + ".titleField", "日历标题字段", issues);
        validateViewEnum(config.calendarUnit(), Set.of("MONTH", "WEEK", "DAY"), path + ".calendarUnit", issues);
      }
      case "GALLERY" -> {
        AppFormFieldRecord cover = requireViewField(info, config.coverField(), path + ".coverField", "画廊封面字段", issues);
        if (cover != null && !Set.of("FILE", "IMAGE", "AVATAR").contains(cover.type())) {
          issues.add("VIEW_TAB_INVALID", path + ".coverField", "画廊封面字段必须是附件类字段: " + cover.label());
        }
        requireViewField(info, config.titleField(), path + ".titleField", "画廊标题字段", issues);
        validateViewEnum(config.imageFit(), Set.of("COVER", "CONTAIN"), path + ".imageFit", issues);
      }
      case "DETAIL" -> validateViewEnum(config.detailMode(), Set.of("LIST", "SINGLE"), path + ".detailMode", issues);
      case "HIERARCHY" -> {
        AppFormFieldRecord parent = requireViewField(info, config.parentField(), path + ".parentField", "层级父节点字段", issues);
        if (parent != null && (!"REFERENCE".equals(parent.type())
          || !info.record.name().equals(parent.referenceForm()))) {
          issues.add("VIEW_TAB_INVALID", path + ".parentField", "层级父节点字段必须关联本表单: " + parent.label());
        }
        validateViewEnum(config.hierarchyMode(), Set.of("TREE", "TABLE"), path + ".hierarchyMode", issues);
      }
      case "GANTT" -> {
        AppFormFieldRecord start = requireViewField(info, config.startField(), path + ".startField", "甘特开始字段", issues);
        AppFormFieldRecord end = requireViewField(info, config.endField(), path + ".endField", "甘特结束字段", issues);
        validateViewDateFields(start, end, path, issues);
        if (start == null || end == null || isBlank(config.endField())) {
          issues.add("VIEW_TAB_INVALID", path, "甘特必须配置开始和结束字段");
        }
        requireViewField(info, config.titleField(), path + ".titleField", "甘特标题字段", issues);
        validateViewEnum(config.ganttScale(), Set.of("DAY", "WEEK", "MONTH"), path + ".ganttScale", issues);
      }
      default -> {
      }
    }
  }

  private AppFormFieldRecord requireViewField(
    FormInfo info,
    String fieldName,
    String path,
    String label,
    Issues issues
  ) {
    if (isBlank(fieldName)) return null;
    AppFormFieldRecord field = info.resolveField(fieldName.trim());
    if (field == null) {
      issues.add("VIEW_TAB_INVALID", path, label + "不存在: " + fieldName);
    }
    return field;
  }

  private void validateViewDateFields(
    AppFormFieldRecord start,
    AppFormFieldRecord end,
    String path,
    Issues issues
  ) {
    Set<String> dateTypes = Set.of("DATE", "DATETIME");
    if (start != null && !dateTypes.contains(start.type())) {
      issues.add("VIEW_TAB_INVALID", path + ".startField", "开始字段必须是日期或日期时间: " + start.label());
    }
    if (end != null) {
      if (!dateTypes.contains(end.type())) {
        issues.add("VIEW_TAB_INVALID", path + ".endField", "结束字段必须是日期或日期时间: " + end.label());
      } else if (start != null && !start.type().equals(end.type())) {
        issues.add("VIEW_TAB_INVALID", path + ".endField", "起止字段必须同为日期或同为日期时间");
      }
    }
  }

  private void validateViewEnum(String value, Set<String> allowed, String path, Issues issues) {
    if (!isBlank(value) && !allowed.contains(value.trim().toUpperCase(Locale.ROOT))) {
      issues.add("VIEW_TAB_INVALID", path, "配置值不合法: " + value);
    }
  }

  private void validateViewTabFilter(FormInfo info, Map<String, Object> filter, String path, Issues issues) {
    if (filter.size() != 1) {
      issues.add("VIEW_TAB_INVALID", path, "视图筛选条件必须包含全部或任一条件组");
      return;
    }
    String logic = String.valueOf(filter.keySet().iterator().next());
    if (!Set.of("_and", "_or").contains(logic) || !(filter.get(logic) instanceof List<?> conditions)) {
      issues.add("VIEW_TAB_INVALID", path, "视图筛选条件必须为 _and 或 _or 结构");
      return;
    }
    if (conditions.isEmpty() || conditions.size() > 10) {
      issues.add("VIEW_TAB_INVALID", path, "视图筛选条件数量必须在 1 到 10 之间");
      return;
    }
    for (int index = 0; index < conditions.size(); index++) {
      Object value = conditions.get(index);
      String conditionPath = path + "." + logic + "[" + index + "]";
      if (!(value instanceof Map<?, ?> condition) || condition.size() != 1) {
        issues.add("VIEW_TAB_INVALID", conditionPath, "视图每个筛选条件只能包含一个字段");
        continue;
      }
      String fieldName = String.valueOf(condition.keySet().iterator().next());
      AppFormFieldRecord field = info.resolveField(fieldName);
      if ("id".equals(fieldName)) {
        issues.add("VIEW_TAB_INVALID", conditionPath, "视图筛选条件不支持 id 字段");
        continue;
      }
      if (field == null) {
        issues.add("VIEW_TAB_INVALID", conditionPath, "视图筛选字段不存在: " + fieldName);
        continue;
      }
      if (!(condition.get(fieldName) instanceof Map<?, ?> operatorMap) || operatorMap.size() != 1) {
        issues.add("VIEW_TAB_INVALID", conditionPath + "." + fieldName, "视图筛选字段只能配置一个操作符");
        continue;
      }
      String operator = String.valueOf(operatorMap.keySet().iterator().next());
      if (!viewTabOperators(field.type()).contains(operator)) {
        issues.add("VIEW_TAB_INVALID", conditionPath + "." + fieldName + "." + operator,
          "字段不支持筛选操作符: " + field.label() + " " + operator);
        continue;
      }
      Object operand = operatorMap.get(operator);
      if (operand == null
        || ("BOOL".equals(field.type()) && !(operand instanceof Boolean))
        || ("NUMBER".equals(field.type()) || "DECIMAL".equals(field.type())) && !(operand instanceof Number)) {
        issues.add("VIEW_TAB_INVALID", conditionPath + "." + fieldName + "." + operator,
          "筛选值类型不合法: " + field.label());
      } else if (operand instanceof String text) {
        try {
          switch (field.type()) {
            case "DATE" -> LocalDate.parse(text);
            case "DATETIME" -> LocalDateTime.parse(text.replace(' ', 'T'));
            case "TIME" -> LocalTime.parse(text);
            default -> {
            }
          }
        } catch (RuntimeException error) {
          issues.add("VIEW_TAB_INVALID", conditionPath + "." + fieldName + "." + operator,
            "筛选值格式不合法: " + field.label());
        }
      }
    }
  }

  private Set<String> viewTabOperators(String type) {
    return switch (type) {
      case "NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME" -> Set.of("_eq", "_ne", "_gt", "_gte", "_lt", "_lte");
      case "BOOL" -> Set.of("_eq", "_ne");
      case "PICKLIST", "SELECT", "CLASSIFICATION", "MULTISELECT", "REFERENCE" -> Set.of("_eq", "_ne");
      default -> Set.of("_eq", "_ne", "_contains", "_not_contains", "_starts_with", "_ends_with");
    };
  }

  private void validateManifestForms(Content content, Map<String, FormInfo> forms, Issues issues) {
    Manifest manifest = content.manifest();
    if (manifest == null || manifest.forms() == null) return;
    Map<String, ManifestForm> declared = new LinkedHashMap<>();
    for (int index = 0; index < manifest.forms().size(); index++) {
      ManifestForm form = manifest.forms().get(index);
      if (form == null || isBlank(form.name())) {
        issues.add("MANIFEST_MISMATCH", "manifest.forms[" + index + "]", "manifest 表单统计缺少名称");
        continue;
      }
      if (declared.put(form.name(), form) != null) {
        issues.add("MANIFEST_MISMATCH", "manifest.forms[" + index + "].name", "manifest 表单统计重复: " + form.name());
      }
    }
    for (FormInfo info : forms.values()) {
      ManifestForm form = declared.get(info.record.name());
      String path = "manifest.forms[" + info.record.name() + "]";
      if (form == null) {
        issues.add("MANIFEST_MISMATCH", path, "manifest 缺少表单统计: " + info.record.name());
        continue;
      }
      int businessFields = info.businessFields.size();
      if (form.fieldCount() != null && form.fieldCount() != businessFields) {
        issues.add("MANIFEST_MISMATCH", path + ".fieldCount",
          "manifest 字段统计与实际不符: " + form.fieldCount() + " != " + businessFields);
      }
      boolean dataIncluded = content.data().containsKey(info.record.name());
      if (!Objects.equals(Boolean.TRUE.equals(form.dataIncluded()), dataIncluded)) {
        issues.add("MANIFEST_MISMATCH", path + ".dataIncluded",
          "manifest 数据标记与包内容不符: " + info.record.name());
      }
      if (dataIncluded && form.dataRows() != null) {
        long actual = content.data().get(info.record.name()).size();
        if (form.dataRows() != actual) {
          issues.add("MANIFEST_MISMATCH", path + ".dataRows",
            "manifest 行数统计与实际不符: " + form.dataRows() + " != " + actual);
        }
      }
    }
    for (String name : declared.keySet()) {
      if (!forms.containsKey(name)) {
        issues.add("MANIFEST_MISMATCH", "manifest.forms[" + name + "]", "manifest 声明了包内不存在的表单: " + name);
      }
    }
  }

  private Set<String> validateNavigation(Content content, Map<String, FormInfo> forms,
                                         Map<String, Object> directory, Issues issues) {
    Set<String> navIds = new LinkedHashSet<>();
    Set<String> formEntryIds = new LinkedHashSet<>();
    Set<String> workbenchIds = content.workbenches() == null ? Set.of() : content.workbenches().stream()
      .map(AppPackage.WorkbenchExport::id)
      .filter(java.util.Objects::nonNull)
      .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<AppNavRecord> navs = content.navigation() == null ? List.of() : content.navigation();
    int[] count = {0};
    for (int index = 0; index < navs.size(); index++) {
      collectNav(navs.get(index), "navigation[" + index + "]", null, forms, directory, issues, navIds, formEntryIds,
        workbenchIds, count, 1);
    }
    if (count[0] > MAX_NAV_NODES) {
      issues.add("NAV_LIMIT", "navigation", "导航节点超过 " + MAX_NAV_NODES + " 个上限");
    }
    return formEntryIds;
  }

  private void collectNav(AppNavRecord nav, String path, String expectedParent, Map<String, FormInfo> forms,
                          Map<String, Object> directory, Issues issues, Set<String> navIds, Set<String> formEntryIds,
                          Set<String> workbenchIds, int[] count, int depth) {
    if (nav == null) {
      issues.add("NAV_INVALID", path, "导航节点为空");
      return;
    }
    count[0]++;
    if (depth > MAX_NAV_DEPTH) {
      issues.add("NAV_LIMIT", path, "导航层级超过 " + MAX_NAV_DEPTH + " 层");
      return;
    }
    if (isBlank(nav.id()) || nav.id().length() > 64) {
      issues.add("NAV_INVALID", path + ".id", "导航ID不合法: " + nav.id());
    } else if (!navIds.add(nav.id())) {
      issues.add("NAV_INVALID", path + ".id", "导航ID重复: " + nav.id());
    }
    if (!NAV_TYPES.contains(nav.type())) {
      issues.add("NAV_INVALID", path + ".type", "导航类型不合法: " + nav.type());
    }
    if (isBlank(nav.name()) || nav.name().length() > 50) {
      issues.add("NAV_INVALID", path + ".name", "导航名称需要 1 至 50 个字符");
    }
    if (nav.iconName() != null && !NAV_ICON.matcher(nav.iconName()).matches()) {
      issues.add("NAV_INVALID", path + ".iconName", "导航图标不合法: " + nav.iconName());
    }
    if (expectedParent == null) {
      if (!isBlank(nav.parentId())) {
        issues.add("NAV_INVALID", path + ".parentId", "根级导航不允许声明父级");
      }
    } else if (!Objects.equals(expectedParent, nav.parentId())) {
      issues.add("NAV_PARENT_MISMATCH", path + ".parentId",
        "导航 parentId 与嵌套结构不一致: " + nav.parentId() + " 期望 " + expectedParent);
    }
    if ("ENTRY".equals(nav.type())) {
      NavDestinationRecord destination = nav.destination();
      if (destination == null || isBlank(destination.type()) || isBlank(destination.id())) {
        issues.add("NAV_INVALID", path + ".destination", "页面导航必须绑定关联项");
      } else {
        switch (destination.type()) {
          case "BUILTIN" -> {
            if (!BUILTIN_DESTINATIONS.contains(destination.id())) {
              issues.add("NAV_INVALID", path + ".destination.id", "内置模块不存在: " + destination.id());
            } else if (destination.workbenchId() != null && !destination.workbenchId().isBlank()) {
              if (!"workbench".equals(destination.id())) {
                issues.add("NAV_INVALID", path + ".destination.workbenchId", "仅工作台内置页面支持配置具体工作台");
              } else if (!workbenchIds.contains(destination.workbenchId())) {
                issues.add("NAV_INVALID", path + ".destination.workbenchId",
                  "导航关联工作台不在包内: " + destination.workbenchId());
              }
            }
          }
          case "FORM" -> {
            if (!forms.containsKey(destination.id())) {
              issues.add("NAV_INVALID", path + ".destination.id", "导航关联表单不在包内: " + destination.id());
            } else {
              formEntryIds.add(nav.id());
            }
          }
          case "LINK" -> validateLink(destination.id(), path + ".destination.id", issues);
          default -> issues.add("NAV_INVALID", path + ".destination.type", "导航关联类型不合法: " + destination.type());
        }
      }
      if (nav.children() != null && !nav.children().isEmpty()) {
        issues.add("NAV_INVALID", path + ".children", "页面导航不允许包含子节点");
      }
    } else if (nav.destination() != null) {
      issues.add("NAV_INVALID", path + ".destination", "分组不能绑定关联项");
    }
    if (nav.visibility() != null && !nav.visibility().isEmpty()) {
      if (nav.visibility().size() > 800) {
        issues.add("NAV_INVALID", path + ".visibility", "导航授权成员不能超过800项");
      }
      Set<String> distinct = new LinkedHashSet<>();
      for (int index = 0; index < nav.visibility().size(); index++) {
        NavVisibilityRule rule = nav.visibility().get(index);
        String rulePath = path + ".visibility[" + index + "]";
        if (rule == null || !Set.of("USER", "ROLE", "ORGANIZATION").contains(rule.type()) || isBlank(rule.id())) {
          issues.add("NAV_INVALID", rulePath, "导航授权成员类型或ID不合法");
          continue;
        }
        if (!distinct.add(rule.type() + '\0' + rule.id())) {
          issues.add("NAV_INVALID", rulePath, "导航授权成员重复");
          continue;
        }
        if (Boolean.TRUE.equals(rule.includeChildren()) && !"ORGANIZATION".equals(rule.type())) {
          issues.add("NAV_INVALID", rulePath, "只有组织支持包含子组织");
        }
        checkDirectoryMember(rule.type(), rule.id(), rulePath + ".id", directory, issues);
      }
    }
    if (nav.children() != null) {
      for (int index = 0; index < nav.children().size(); index++) {
        collectNav(nav.children().get(index), path + ".children[" + index + "]", nav.id(),
          forms, directory, issues, navIds, formEntryIds, workbenchIds, count, depth + 1);
      }
    }
  }

  private void validateLink(String url, String path, Issues issues) {
    if (isBlank(url) || url.length() > 2048) {
      issues.add("NAV_INVALID", path, "导航链接长度需要在 1 至 2048 之间");
      return;
    }
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || isBlank(uri.getHost())) {
        issues.add("NAV_INVALID", path, "导航链接必须是 http 或 https 地址: " + url);
      }
    } catch (IllegalArgumentException error) {
      issues.add("NAV_INVALID", path, "导航链接格式不正确: " + url);
    }
  }

  private void validateWorkbenches(Content content, Map<String, FormInfo> forms,
                                   Set<String> navFormEntryIds, Issues issues) {
    List<WorkbenchExport> workbenches = content.workbenches() == null ? List.of() : content.workbenches();
    if (workbenches.size() > MAX_WORKBENCHES) {
      issues.add("WORKBENCH_LIMIT", "workbenches", "工作台数量超过 " + MAX_WORKBENCHES + " 个上限");
    }
    Set<String> names = new LinkedHashSet<>();
    for (int index = 0; index < workbenches.size(); index++) {
      WorkbenchExport workbench = workbenches.get(index);
      String path = "workbenches[" + index + "]";
      if (workbench == null) {
        issues.add("WORKBENCH_INVALID", path, "工作台定义为空");
        continue;
      }
      if (isBlank(workbench.name()) || workbench.name().length() > 100) {
        issues.add("WORKBENCH_INVALID", path + ".name", "工作台名称需要 1 至 100 个字符");
      } else if (!names.add(workbench.name())) {
        issues.add("WORKBENCH_INVALID", path + ".name", "工作台名称重复: " + workbench.name());
      }
      if (workbench.description() != null && workbench.description().length() > 500) {
        issues.add("WORKBENCH_INVALID", path + ".description", "工作台描述不能超过 500 个字符");
      }
      WorkbenchLayoutRecord layout = workbench.layout();
      if (layout == null || layout.schemaVersion() == null
        || layout.schemaVersion() != WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION) {
        issues.add("WORKBENCH_INVALID", path + ".layout", "工作台布局协议版本不合法");
        continue;
      }
      List<WorkbenchComponentRecord> components = layout.components();
      if (components == null) {
        issues.add("WORKBENCH_INVALID", path + ".layout.components", "工作台组件不能为空");
        continue;
      }
      if (components.size() > MAX_WORKBENCH_COMPONENTS) {
        issues.add("WORKBENCH_LIMIT", path + ".layout.components", "单工作台组件超过 100 个上限");
      }
      Set<String> ids = new LinkedHashSet<>();
      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
        WorkbenchComponentRecord component = components.get(componentIndex);
        String componentPath = path + ".layout.components[" + componentIndex + "]";
        if (component == null || isBlank(component.id())) {
          issues.add("WORKBENCH_INVALID", componentPath + ".id", "工作台组件ID不能为空");
          continue;
        }
        if (!ids.add(component.id())) {
          issues.add("WORKBENCH_INVALID", componentPath + ".id", "工作台组件ID重复: " + component.id());
        }
        if (isBlank(component.title()) || component.title().trim().length() > 100) {
          issues.add("WORKBENCH_INVALID", componentPath + ".title", "工作台组件标题需要 1 至 100 个字符");
        }
        validatePlacement(component.placement(), componentPath + ".placement", issues);
        validateWorkbenchConfig(component.config(), componentPath + ".config", forms, navFormEntryIds, issues);
      }
    }
  }

  private void validatePlacement(WorkbenchPlacementRecord placement, String path, Issues issues) {
    if (placement == null || placement.x() == null || placement.x() < 0
      || placement.y() == null || placement.y() < 0
      || placement.w() == null || placement.w() < 1 || placement.w() > 12
      || placement.h() == null || placement.h() < 1 || placement.h() > 24
      || placement.x() + placement.w() > 12 || placement.y() + placement.h() > 24) {
      issues.add("WORKBENCH_INVALID", path, "工作台布局位置不合法");
    }
  }

  private void validateWorkbenchConfig(dev.flexmodel.apps.workbench.dto.WorkbenchComponentConfig config, String path,
                                       Map<String, FormInfo> forms, Set<String> navFormEntryIds, Issues issues) {
    if (config == null) {
      issues.add("WORKBENCH_INVALID", path, "工作台组件配置不能为空");
      return;
    }
    if (config instanceof WorkbenchTasksConfig) return;
    if (config instanceof WorkbenchMetricConfig metric) {
      FormInfo form = requireWorkbenchForm(metric.formName(), path + ".formName", forms, issues);
      if (metric.aggregation() == null || !AGGREGATIONS.contains(metric.aggregation())) {
        issues.add("WORKBENCH_INVALID", path + ".aggregation", "工作台指标聚合方式不合法");
        return;
      }
      if ("count".equals(metric.aggregation())) {
        if (!isBlank(metric.field())) {
          issues.add("WORKBENCH_INVALID", path + ".field", "记录数指标不能配置聚合字段");
        }
      } else if (form != null) {
        requireMeasureField(form, metric.field(), path + ".field", issues);
      }
      return;
    }
    if (config instanceof WorkbenchChartConfig chart) {
      FormInfo form = requireWorkbenchForm(chart.formName(), path + ".formName", forms, issues);
      if (chart.chartType() == null || !CHART_TYPES.contains(chart.chartType())) {
        issues.add("WORKBENCH_INVALID", path + ".chartType", "工作台图表类型不合法");
      }
      if (form != null) {
        AppFormFieldRecord dimension = form.resolveField(chart.dimensionField());
        if (isBlank(chart.dimensionField())) {
          issues.add("WORKBENCH_INVALID", path + ".dimensionField", "工作台图表必须配置分组字段");
        } else if (dimension == null) {
          issues.add("WORKBENCH_INVALID", path + ".dimensionField", "工作台分组字段不存在: " + chart.dimensionField());
        } else if (!GROUPABLE_TYPES.contains(dimension.type())) {
          issues.add("WORKBENCH_INVALID", path + ".dimensionField", "工作台分组字段类型不支持: " + dimension.type());
        }
      }
      if (chart.aggregation() == null || !AGGREGATIONS.contains(chart.aggregation())) {
        issues.add("WORKBENCH_INVALID", path + ".aggregation", "工作台聚合方式不合法");
      } else if (!"count".equals(chart.aggregation())) {
        if (form != null) requireMeasureField(form, chart.measureField(), path + ".measureField", issues);
      } else if (!isBlank(chart.measureField())) {
        issues.add("WORKBENCH_INVALID", path + ".measureField", "记录数图表不能配置指标字段");
      }
      if (chart.showLegend() == null || chart.showLabel() == null
        || ("line".equals(chart.chartType()) && chart.smooth() == null)) {
        issues.add("WORKBENCH_INVALID", path, "工作台图表显示配置不能为空");
      }
      if (chart.palette() == null || !PALETTES.contains(chart.palette())) {
        issues.add("WORKBENCH_INVALID", path + ".palette", "工作台配色不合法");
      }
      return;
    }
    if (config instanceof WorkbenchDataLoaderConfig dataLoader) {
      FormInfo form = requireWorkbenchForm(dataLoader.formName(), path + ".formName", forms, issues);
      if (dataLoader.visibleColumns() == null || dataLoader.visibleColumns().isEmpty()) {
        issues.add("WORKBENCH_INVALID", path + ".visibleColumns", "工作台明细组件必须配置显示列");
        return;
      }
      Set<String> columns = new LinkedHashSet<>();
      for (int index = 0; index < dataLoader.visibleColumns().size(); index++) {
        String column = dataLoader.visibleColumns().get(index);
        String columnPath = path + ".visibleColumns[" + index + "]";
        if (isBlank(column) || !columns.add(column)) {
          issues.add("WORKBENCH_INVALID", columnPath, "工作台明细列不合法: " + column);
          continue;
        }
        if (form != null && !form.allFields.contains(column)) {
          issues.add("WORKBENCH_INVALID", columnPath, "工作台明细列字段不存在: " + column);
        }
      }
      return;
    }
    if (config instanceof WorkbenchShortcutsConfig shortcuts) {
      if (shortcuts.columnsPerRow() == null || shortcuts.columnsPerRow() < 1 || shortcuts.columnsPerRow() > 12) {
        issues.add("WORKBENCH_INVALID", path + ".columnsPerRow", "快捷入口每行数量必须在 1 到 12 之间");
      }
      List<String> entries = shortcuts.shortcuts() == null ? List.of() : shortcuts.shortcuts();
      Set<String> seen = new LinkedHashSet<>();
      for (int index = 0; index < entries.size(); index++) {
        String navId = entries.get(index);
        String entryPath = path + ".shortcuts[" + index + "]";
        if (isBlank(navId) || !seen.add(navId)) {
          issues.add("WORKBENCH_INVALID", entryPath, "工作台快捷入口菜单不合法");
          continue;
        }
        if (!navFormEntryIds.contains(navId)) {
          issues.add("WORKBENCH_INVALID", entryPath, "工作台快捷入口必须关联表单菜单: " + navId);
        }
      }
    }
  }

  private FormInfo requireWorkbenchForm(String formName, String path, Map<String, FormInfo> forms, Issues issues) {
    if (isBlank(formName)) {
      issues.add("WORKBENCH_INVALID", path, "工作台组件必须配置表单");
      return null;
    }
    FormInfo form = forms.get(formName);
    if (form == null) {
      issues.add("WORKBENCH_INVALID", path, "工作台组件表单不在包内: " + formName);
    }
    return form;
  }

  private void requireMeasureField(FormInfo form, String field, String path, Issues issues) {
    AppFormFieldRecord record = form.resolveField(field);
    if (isBlank(field) || record == null) {
      issues.add("WORKBENCH_INVALID", path, "工作台指标聚合字段不存在: " + field);
    } else if (!MEASURE_TYPES.contains(record.type())) {
      issues.add("WORKBENCH_INVALID", path, "工作台指标聚合字段必须是数字字段: " + field);
    }
  }

  private Map<String, Map<String, Object>> validateApprovals(Content content, Map<String, FormInfo> forms,
                                                             Map<String, Object> directory, Issues issues) {
    List<Map<String, Object>> approvals = content.approvals() == null ? List.of() : content.approvals();
    Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    if (approvals.size() > MAX_APPROVAL_FLOWS) {
      issues.add("APPROVAL_LIMIT", "approvals", "审批流程数量超过 " + MAX_APPROVAL_FLOWS + " 个上限");
    }
    Set<String> flowKeys = new LinkedHashSet<>();
    for (int index = 0; index < approvals.size(); index++) {
      Map<String, Object> flow = approvals.get(index);
      String path = "approvals[" + index + "]";
      if (flow == null) {
        issues.add("APPROVAL_INVALID", path, "审批流程定义为空");
        continue;
      }
      String id = text(flow, "id");
      if (isBlank(id)) {
        issues.add("APPROVAL_INVALID", path + ".id", "审批流程缺少源ID");
      } else if (byId.put(id, flow) != null) {
        issues.add("APPROVAL_INVALID", path + ".id", "审批流程源ID重复: " + id);
      }
      String flowKey = text(flow, "flowKey");
      if (isBlank(flowKey) || !FLOW_KEY.matcher(flowKey).matches()) {
        issues.add("APPROVAL_INVALID", path + ".flowKey", "流程标识无效: " + flowKey);
      } else if (!flowKeys.add(flowKey)) {
        issues.add("APPROVAL_INVALID", path + ".flowKey", "流程标识重复: " + flowKey);
      }
      String name = text(flow, "name");
      if (isBlank(name) || name.length() > 120) {
        issues.add("APPROVAL_INVALID", path + ".name", "流程名称不能为空且不超过 120 字符");
      }
      if (text(flow, "description").length() > 512) {
        issues.add("APPROVAL_INVALID", path + ".description", "流程备注不能超过 512 字符");
      }
      String status = text(flow, "status");
      if (!Set.of("draft", "disabled", "enabled").contains(status)) {
        issues.add("APPROVAL_INVALID", path + ".status", "流程状态无效: " + status);
      }
      Map<String, Object> form = object(flow.get("form"));
      if (!"app".equals(text(form, "kind")) || !forms.containsKey(text(form, "name"))) {
        issues.add("APPROVAL_INVALID", path + ".form", "流程必须绑定包内业务表单");
      }
      List<Map<String, Object>> nodes = objects(object(flow.get("definition")).get("nodes"));
      if (nodes.isEmpty() || !"start".equals(text(nodes.getFirst(), "type"))
        || !"end".equals(text(nodes.getLast(), "type"))) {
        issues.add("APPROVAL_INVALID", path + ".definition.nodes", "开始和结束节点必须保留在流程首尾");
      }
      List<Map<String, Object>> flattened = flattenNodes(nodes);
      if (flattened.size() > MAX_APPROVAL_NODES) {
        issues.add("APPROVAL_LIMIT", path + ".definition.nodes", "流程节点超过 " + MAX_APPROVAL_NODES + " 个上限");
      }
      Set<String> nodeIds = new LinkedHashSet<>();
      long starts = 0;
      long ends = 0;
      for (int nodeIndex = 0; nodeIndex < flattened.size(); nodeIndex++) {
        Map<String, Object> node = flattened.get(nodeIndex);
        String nodePath = path + ".definition.nodes[" + nodeIndex + "]";
        String nodeId = text(node, "id");
        String kind = text(node, "type");
        if (!NODE_ID.matcher(nodeId).matches() || !nodeIds.add(nodeId)) {
          issues.add("APPROVAL_INVALID", nodePath + ".id", "节点标识无效或重复");
        }
        if (!APPROVAL_KINDS.contains(kind)) {
          issues.add("APPROVAL_INVALID", nodePath + ".type", "节点类型无效: " + kind);
        }
        if ("start".equals(kind)) starts++;
        if ("end".equals(kind)) ends++;
        Map<String, Object> config = object(object(node.get("data")).get("config"));
        if ("approval".equals(kind) || "notification".equals(kind)) {
          if ("approval".equals(kind) && !Set.of("all", "any", "sequential").contains(text(config, "signMode"))) {
            issues.add("APPROVAL_INVALID", nodePath + ".config.signMode", "签核类型无效");
          }
          List<Map<String, Object>> rules = objects(config.get("rules"));
          if (rules.isEmpty()) {
            issues.add("APPROVAL_INVALID", nodePath + ".config.rules", "至少配置一组执行人");
          }
          for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            Map<String, Object> rule = rules.get(ruleIndex);
            String rulePath = nodePath + ".config.rules[" + ruleIndex + "]";
            String source = text(rule, "source");
            if (!DirectoryService.SOURCES.contains(source)
              || !DirectoryService.SCOPES.contains(text(rule, "scope"))) {
              issues.add("APPROVAL_INVALID", rulePath, "执行人来源或人员范围无效");
              continue;
            }
            List<String> values = strings(rule.get("values"));
            if (Set.of("users", "roles", "organizations").contains(source)) {
              if (values.isEmpty()) {
                issues.add("APPROVAL_INVALID", rulePath + ".values", "请选择执行人来源");
                continue;
              }
              for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                String memberType = switch (source) {
                  case "users" -> "USER";
                  case "roles" -> "ROLE";
                  default -> "ORGANIZATION";
                };
                checkDirectoryMember(memberType, values.get(valueIndex),
                  rulePath + ".values[" + valueIndex + "]", directory, issues);
              }
            }
          }
        }
        if (DATA_KINDS.contains(kind)) {
          FormInfo boundForm = forms.get(text(object(config.get("form")), "name"));
          if (boundForm == null) {
            issues.add("APPROVAL_INVALID", nodePath + ".config.form", "数据节点必须绑定包内业务表单");
          } else {
            for (String section : List.of("assignments", "conditions")) {
              for (var entry : objects(config.get(section))) {
                String field = text(entry, "field");
                if (!boundForm.allFields.contains(field)) {
                  issues.add("APPROVAL_INVALID", nodePath + ".config." + section,
                    "字段不存在: " + field);
                } else if ("assignments".equals(section)
                  && Set.of("id", "created_by", "created_at", "updated_by", "updated_at").contains(field)) {
                  issues.add("APPROVAL_INVALID", nodePath + ".config.assignments", "不能赋值系统字段: " + field);
                }
              }
            }
          }
        }
      }
      if (starts != 1 || ends != 1) {
        issues.add("APPROVAL_INVALID", path + ".definition.nodes", "流程必须恰好包含一个开始和结束节点");
      }
    }
    validateSubflows(approvals, byId, issues);
    return byId;
  }

  private void validateSubflows(List<Map<String, Object>> approvals, Map<String, Map<String, Object>> byId,
                                Issues issues) {
    Map<String, Set<String>> graph = new LinkedHashMap<>();
    for (int index = 0; index < approvals.size(); index++) {
      Map<String, Object> flow = approvals.get(index);
      String id = text(flow, "id");
      Set<String> children = new LinkedHashSet<>();
      for (Map<String, Object> node : flattenNodes(objects(object(flow.get("definition")).get("nodes")))) {
        if ("subflow".equals(text(node, "type"))) {
          String child = text(object(object(node.get("data")).get("config")), "flowId");
          if (!byId.containsKey(child)) {
            issues.add("APPROVAL_INVALID", "approvals[" + index + "].definition",
              "子流程不在包内: " + child);
          } else if (children.add(child) && "enabled".equals(text(flow, "status"))
            && !"enabled".equals(text(byId.get(child), "status"))) {
            issues.add("APPROVAL_DEPENDENCY", "approvals[" + index + "].definition",
              "启用流程依赖未启用的子流程: " + child);
          }
        }
      }
      graph.put(id, children);
    }
    for (String id : graph.keySet()) {
      if (cyclic(id, graph, new HashSet<>(), new HashSet<>())) {
        issues.add("APPROVAL_SUBFLOW_CYCLE", "approvals", "子流程存在直接或间接循环引用: " + id);
      }
    }
  }

  private boolean cyclic(String id, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
    if (visiting.contains(id)) return true;
    if (!visited.add(id)) return false;
    visiting.add(id);
    for (String child : graph.getOrDefault(id, Set.of())) {
      if (cyclic(child, graph, visiting, visited)) return true;
    }
    visiting.remove(id);
    return false;
  }

  private List<Map<String, Object>> flattenNodes(List<Map<String, Object>> nodes) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> node : nodes) {
      result.add(node);
      result.addAll(flattenNodes(objects(node.get("blocks"))));
    }
    return result;
  }

  /**
   * 数据权限依赖目标实例的目录与表单，无效规则只提示并在导入时跳过，不阻断整包导入。
   */
  private void validateAuthorizations(Content content, Map<String, FormInfo> forms,
                                      Map<String, Object> directory, Issues issues, List<String> warnings) {
    List<AuthorizationExport> rules = content.authorizations() == null ? List.of() : content.authorizations();
    if (rules.size() > MAX_RULES_PER_APP) {
      issues.add("AUTHORIZATION_LIMIT", "data-authorizations", "数据权限规则数量超过上限");
    }
    for (int index = 0; index < rules.size(); index++) {
      AuthorizationExport rule = rules.get(index);
      String path = "data-authorizations[" + index + "]";
      if (rule == null) {
        skipAuthorization(warnings, path, "数据权限规则为空");
        continue;
      }
      FormInfo main = forms.get(rule.formName());
      if (main == null) {
        skipAuthorization(warnings, path + ".formName", "规则表单不在包内: " + rule.formName());
        continue;
      }
      if (isBlank(rule.name()) || rule.name().length() > 255) {
        skipAuthorization(warnings, path + ".name", "规则名称需要1至255个字符");
      }
      validateSelections(rule.subjects(), path + ".subjects", false, directory, warnings);
      validateSelections(rule.dataScope(), path + ".dataScope", true, directory, warnings);
      if (rule.dataScope() != null && rule.dataScope().size() > 1
        && rule.dataScope().stream().anyMatch(selection -> "ALL".equals(selection.type()))) {
        skipAuthorization(warnings, path + ".dataScope", "全部数据范围不能与其他范围混选");
      }
      Set<String> targets = new LinkedHashSet<>();
      targets.add(main.record.name());
      for (AppFormItemRecord item : main.record.layout() == null || main.record.layout().items() == null
        ? List.<AppFormItemRecord>of() : main.record.layout().items()) {
        if ("SUBFORM".equals(item.type()) && !isBlank(item.formName())) targets.add(item.formName());
      }
      if (rule.controls() == null || rule.controls().isEmpty()) {
        skipAuthorization(warnings, path + ".controls", "控制明细必须为数组");
        continue;
      }
      Set<String> seen = new LinkedHashSet<>();
      boolean hasMain = false;
      for (int controlIndex = 0; controlIndex < rule.controls().size(); controlIndex++) {
        var control = rule.controls().get(controlIndex);
        String controlPath = path + ".controls[" + controlIndex + "]";
        if (control == null) {
          skipAuthorization(warnings, controlPath, "控制明细必须为对象");
          continue;
        }
        if (!targets.contains(control.formName()) || !seen.add(control.formName())) {
          skipAuthorization(warnings, controlPath + ".formName",
            "明细表单不属于当前主子关系或重复: " + control.formName());
          continue;
        }
        if (main.record.name().equals(control.formName())) hasMain = true;
        FormInfo targetForm = forms.get(control.formName());
        if (targetForm == null) continue;
        if (control.fieldControls() != null) {
          control.fieldControls().forEach((fieldName, flags) -> {
            String fieldPath = controlPath + ".fieldControls." + fieldName;
            if (!targetForm.allFields.contains(fieldName)) {
              skipAuthorization(warnings, fieldPath, "权限字段不存在: " + fieldName);
            } else if (BUILTIN_FIELDS.contains(fieldName) && Boolean.TRUE.equals(flags.write())) {
              skipAuthorization(warnings, fieldPath, "内置字段不能授权修改");
            }
          });
        }
        if (control.condition() != null && control.condition().isEmpty()) {
          skipAuthorization(warnings, controlPath + ".condition", "权限条件不能为空对象");
        }
      }
      if (!hasMain) {
        skipAuthorization(warnings, path + ".controls", "必须包含主表权限明细");
      }
    }
  }

  private void skipAuthorization(List<String> warnings, String path, String message) {
    warn(warnings, "数据权限已跳过 " + path + ": " + message);
  }

  private static void warn(List<String> warnings, String message) {
    if (warnings.size() < 500) warnings.add(message);
  }

  private void validateSelections(List<Selection> selections, String path, boolean scope,
                                  Map<String, Object> directory, List<String> warnings) {
    if (selections == null) return;
    if (selections.size() > 100) {
      skipAuthorization(warnings, path, "人员选择必须为不超过100项的数组");
    }
    for (int index = 0; index < selections.size(); index++) {
      Selection selection = selections.get(index);
      String selectionPath = path + "[" + index + "]";
      if (selection == null || selection.type() == null) {
        skipAuthorization(warnings, selectionPath, "人员选择项必须为对象");
        continue;
      }
      String type = selection.type();
      boolean entity = Set.of("USER", "ROLE", "ORGANIZATION").contains(type);
      if (!entity && (!scope || !DYNAMIC_SCOPES.contains(type))) {
        skipAuthorization(warnings, selectionPath + ".type", "未知人员选择类型: " + type);
        continue;
      }
      if (Boolean.TRUE.equals(selection.includeChildren()) && !"ORGANIZATION".equals(type)) {
        skipAuthorization(warnings, selectionPath + ".includeChildren", "只有组织支持包含子组织");
      }
      if (entity) {
        List<String> ids = selection.ids() == null ? List.of() : selection.ids();
        if (ids.isEmpty() || ids.size() > 800 || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
          skipAuthorization(warnings, selectionPath + ".ids", "成员ID必须为非空字符串数组");
          continue;
        }
        for (int idIndex = 0; idIndex < ids.size(); idIndex++) {
          String missing = directoryMemberMissing(type, ids.get(idIndex), directory);
          if (missing != null) skipAuthorization(warnings, selectionPath + ".ids[" + idIndex + "]", missing);
        }
      } else if (selection.ids() != null && !selection.ids().isEmpty()) {
        skipAuthorization(warnings, selectionPath + ".ids", "动态范围不接受指定成员ID");
      }
    }
  }

  /**
   * 记录数据按「尽量导入」处理：行级问题只提示不阻断，写入阶段宽松落库。
   */
  private void validateData(Content content, Map<String, FormInfo> forms,
                            SystemRecordLookup system, Issues issues, List<String> warnings) {
    Map<String, List<Map<String, Object>>> data = content.data();
    long total = 0;
    for (var entry : data.entrySet()) {
      String formName = entry.getKey();
      FormInfo info = forms.get(formName);
      String path = "data/" + formName;
      if (info == null) {
        issues.add("DATA_FORM_UNKNOWN", path, "记录数据文件对应的表单不存在: " + formName);
        continue;
      }
      List<Map<String, Object>> rows = entry.getValue();
      if (rows.size() > MAX_ROWS_PER_FORM) {
        issues.add("DATA_ROW_LIMIT", path, "单表单记录超过 " + MAX_ROWS_PER_FORM + " 行上限");
      }
      total += rows.size();
      Set<String> ids = new LinkedHashSet<>();
      for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
        Map<String, Object> row = rows.get(rowIndex);
        String rowPath = path + "[" + rowIndex + "]";
        if (row == null) {
          warn(warnings, "数据记录已忽略 " + rowPath + ": 记录为空");
          continue;
        }
        Object id = row.get("id");
        if (!(id instanceof String recordId) || recordId.isBlank() || recordId.length() > 64) {
          warn(warnings, "数据主键不是有效字符串 " + rowPath + ".id: " + id);
        } else if (!ids.add(recordId)) {
          warn(warnings, "数据主键重复 " + rowPath + ".id: " + recordId);
        }
        for (var column : row.entrySet()) {
          if ("id".equals(column.getKey())) continue;
          String fieldPath = rowPath + "." + column.getKey();
          AppFormFieldRecord field = info.businessFields.get(column.getKey());
          if (field == null) {
            warn(warnings, "数据字段不存在已忽略 " + fieldPath + ": " + column.getKey());
            continue;
          }
          if (AppFieldRules.isEmpty(column.getValue())) {
            if (field.required()) warn(warnings, "必填字段为空 " + fieldPath + ": " + field.label());
            continue;
          }
          try {
            AppFieldRules.validateValue(fieldResponse(field), column.getValue());
          } catch (RuntimeException error) {
            warn(warnings, "数据值校验未通过 " + fieldPath + ": " + error.getMessage());
          }
        }
        for (AppFormFieldRecord field : info.businessFields.values()) {
          if (field.required() && !row.containsKey(field.name())) {
            warn(warnings, "必填字段缺失 " + rowPath + "." + field.name() + ": " + field.label());
          }
        }
      }
    }
    if (total > MAX_TOTAL_ROWS) {
      issues.add("DATA_ROW_LIMIT", "data", "整包记录超过 " + MAX_TOTAL_ROWS + " 行上限");
    }
    validateDataReferences(content, forms, system, warnings);
  }

  private void validateDataReferences(Content content, Map<String, FormInfo> forms,
                                      SystemRecordLookup system, List<String> warnings) {
    for (var entry : content.data().entrySet()) {
      FormInfo info = forms.get(entry.getKey());
      if (info == null) continue;
      for (int rowIndex = 0; rowIndex < entry.getValue().size(); rowIndex++) {
        Map<String, Object> row = entry.getValue().get(rowIndex);
        if (row == null) continue;
        String rowPath = "data/" + entry.getKey() + "[" + rowIndex + "]";
        for (var field : info.businessFields.values()) {
          if (!"REFERENCE".equals(field.type())) continue;
          Object value = row.get(field.name());
          if (AppFieldRules.isEmpty(value)) continue;
          String referenceId = String.valueOf(value);
          String fieldPath = rowPath + "." + field.name();
          if (SYSTEM_FORMS.contains(field.referenceForm())) {
            if (!system.exists(field.referenceForm(), referenceId)) {
              warn(warnings, "引用的系统目录记录在目标环境不存在 " + fieldPath + ": "
                + field.referenceForm() + "/" + referenceId);
            }
          } else {
            List<Map<String, Object>> targetRows = content.data().get(field.referenceForm());
            boolean present = targetRows != null && targetRows.stream()
              .anyMatch(target -> referenceId.equals(String.valueOf(target.get("id"))));
            if (!present) {
              warn(warnings, "业务引用记录不在包内 " + fieldPath + ": "
                + field.referenceForm() + "/" + referenceId);
            }
          }
        }
      }
    }
  }

  private AppFormFieldResponse fieldResponse(AppFormFieldRecord field) {
    return new AppFormFieldResponse(field.name(), field.label(), field.type(), field.required(), false,
      field.options(), field.referenceForm(), false, false, field.config());
  }

  private void checkDirectoryMember(String type, String id, String path, Map<String, Object> directory,
                                    Issues issues) {
    String message = directoryMemberMissing(type, id, directory);
    if (message == null) return;
    String code = switch (type) {
      case "USER" -> "DIRECTORY_MEMBER_MISSING";
      case "ROLE" -> "DIRECTORY_ROLE_MISSING";
      default -> "DIRECTORY_ORGANIZATION_MISSING";
    };
    issues.add(code, path, message);
  }

  private String directoryMemberMissing(String type, String id, Map<String, Object> directory) {
    if (directory == null) return null;
    String section = switch (type) {
      case "USER" -> "members";
      case "ROLE" -> "roles";
      default -> "organizations";
    };
    boolean present = objects(directory.get(section)).stream().anyMatch(item -> id.equals(text(item, "id")));
    if (present) return null;
    String label = switch (type) {
      case "USER" -> "用户";
      case "ROLE" -> "角色";
      default -> "组织";
    };
    return "目标目录中不存在" + label + ": " + id;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
