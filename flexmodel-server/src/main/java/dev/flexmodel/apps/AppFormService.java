package dev.flexmodel.apps;

import dev.flexmodel.apps.approval.AppApprovalLifecycle;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormCreateRequest;
import dev.flexmodel.apps.dto.AppFieldConfig;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRequest;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldOptionResponse;
import dev.flexmodel.apps.dto.AppFormFieldRecord;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormFieldUpdateRequest;
import dev.flexmodel.apps.dto.AppFormPatchRequest;
import dev.flexmodel.apps.dto.AppFormRecord;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppFormUpdateRequest;
import dev.flexmodel.apps.dto.AppFormViewTabRecord;
import dev.flexmodel.apps.dto.AppMenuRecord;
import dev.flexmodel.apps.dto.AppMenuTargetRecord;
import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.RoleResource;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.codegen.entity.UserRole;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.graphql.dto.GraphQLRefreshEvent;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.IndexDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.BooleanField;
import dev.flexmodel.model.field.DateField;
import dev.flexmodel.model.field.DateTimeField;
import dev.flexmodel.model.field.DefaultValue;
import dev.flexmodel.model.field.FloatField;
import dev.flexmodel.model.field.IntField;
import dev.flexmodel.model.field.JSONField;
import dev.flexmodel.model.field.ModelRefField;
import dev.flexmodel.model.field.StringField;
import dev.flexmodel.model.field.TimeField;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.modeling.ModelingService;
import dev.flexmodel.modeling.ModelService;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.system.SystemFormService;
import dev.flexmodel.system.dto.SystemFormItemRecord;
import dev.flexmodel.system.dto.SystemFormLayoutRecord;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 应用表单服务。界面概念为表单，底层始终映射为 Flexmodel 实体。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class AppFormService {
  @Inject
  AppApprovalLifecycle appApprovalLifecycle;

  private static final String METADATA_APP_KEY = "app";
  private static final String METADATA_FORMS_KEY = "forms";
  private static final String METADATA_LEGACY_FORM_LAYOUTS_KEY = "formLayouts";
  private static final Pattern FORM_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
  private static final int MAX_LAYOUT_ITEMS = 500;
  private static final int MAX_VIEW_TABS = 20;
  private static final int MAX_VIEW_TAB_CONDITIONS = 10;
  private static final Set<String> FORM_ITEM_TYPES = Set.of("FIELD", "SECTION", "SUBFORM");
  private static final Set<String> FORM_ITEM_WIDTHS = Set.of(
    "QUARTER", "THIRD", "HALF", "TWO_THIRDS", "THREE_QUARTERS", "FULL");
  private static final String NAME_FIELD = "name";
  private static final Set<String> BUILTIN_FIELDS = Set.of(
    "id", "owner_id", "created_by", "updated_by", "created_at", "updated_at");
  private static final Set<String> RESERVED_SYSTEM_FORM_NAMES = Set.of(
    dev.flexmodel.apps.dto.AppTransfer.RECEIPT,
    ReflectionUtils.getModelNameFromClass(User.class), ReflectionUtils.getModelNameFromClass(Department.class), ReflectionUtils.getModelNameFromClass(Role.class),
    ReflectionUtils.getModelNameFromClass(UserDepartment.class), ReflectionUtils.getModelNameFromClass(UserRole.class), ReflectionUtils.getModelNameFromClass(RoleResource.class));
  private static final Set<String> BUILTIN_SYSTEM_FORM_NAMES = Set.of(
    ReflectionUtils.getModelNameFromClass(User.class), ReflectionUtils.getModelNameFromClass(Department.class), ReflectionUtils.getModelNameFromClass(Role.class));
  private static final Set<String> FIELD_TYPES = Set.of(
    "TEXT", "NTEXT", "PHONE", "EMAIL", "URL", "SERIES", "PICKLIST", "SELECT",
    "NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME", "CLASSIFICATION",
    "MULTISELECT", "TAG", "REFERENCE", "FILE", "IMAGE", "AVATAR", "BARCODE",
    "LOCATION", "SIGN", "BOOL", "STATE");
  private static final Set<String> OPTION_TYPES = Set.of(
    "PICKLIST", "SELECT", "CLASSIFICATION", "MULTISELECT");
  private static final Set<String> REFERENCE_TYPES = Set.of("REFERENCE");

  @Inject
  ModelingService modelingService;

  @Inject
  ModelService modelService;

  @Inject
  ProjectService projectService;

  @Inject
  SessionContext sessionContext;

  @Inject
  AppRecordDiscussionRepository appRecordDiscussionRepository;

  @Inject
  EventBus eventBus;

  @Inject
  SystemFormService systemFormService;

  @Inject
  AppDataAuthorizationService dataAuthorization;

  public List<AppFormResponse> findForms(String appId) {
    if ("system".equals(appId)) {
      return systemFormService.findForms().stream()
        .map(systemFormService::toAppFormResponse)
        .toList();
    }
    Project project = requireAppProject(appId);
    Map<String, AppFormRecord> records = formRecords(project).stream()
      .collect(Collectors.toMap(AppFormRecord::name, Function.identity(), (left, right) -> right,
        LinkedHashMap::new));
    List<AppFormResponse> builtinForms = systemFormService.findForms().stream()
      .map(systemFormService::toAppFormResponse)
      .toList();
    List<AppFormResponse> userForms = entities(appId).stream()
      .filter(entity -> !RESERVED_SYSTEM_FORM_NAMES.contains(entity.getName()))
      .map(entity -> toResponse(project, entity, records.get(entity.getName())))
      .toList();
    List<AppFormResponse> result = new ArrayList<>(builtinForms);
    result.addAll(userForms);
    return result;
  }

  public AppFormResponse findForm(String appId, String formName) {
    if (BUILTIN_SYSTEM_FORM_NAMES.contains(formName)) {
      return systemFormService.toAppFormResponse(systemFormService.findForm(formName));
    }
    Project project = requireAppProject(appId);
    ResolvedForm form = requireForm(project, appId, formName);
    return toResponse(project, form.entity(), form.record());
  }

  public AppFormResponse createForm(String appId, AppFormCreateRequest request) {
    return createFormWithFields(appId, request, List.of());
  }

  public AppFormResponse createFormWithFields(
    String appId,
    AppFormCreateRequest request,
    List<AppFormFieldCreateRequest> fields
  ) {
    Project project = requireAppProject(appId);
    String formName = validateFormName(request.name());
    String label = validateFormLabel(request.label());
    List<PreparedField> preparedFields = prepareFields(appId, fields);
    ensureUniqueFormName(project, appId, formName);
    EntityDefinition entity = buildFormEntity(formName, label, request.createDisplayField());
    Map<String, AppFormFieldRecord> fieldRecords = systemFieldRecords();
    fieldRecords.putAll(addPreparedFields(formName, entity, preparedFields));

    modelingService.createModel(appId, entity);
    AppFormRecord record = new AppFormRecord(
      formName,
      label,
      request.description() == null ? "" : request.description(),
      request.createDisplayField() ? NAME_FIELD : null,
      request.createDisplayField() ? NAME_FIELD : null,
      LocalDateTime.now(),
      LocalDateTime.now(),
      entity.getFields().stream()
        .map(field -> toFieldRecord(field, fieldRecords.get(field.getName())))
        .toList(),
      new AppFormLayoutRecord(List.of()),
      List.of());
    try {
      saveFormRecord(project, record);
    } catch (RuntimeException caught) {
      try {
        modelingService.dropModel(appId, formName);
      } catch (RuntimeException cleanupException) {
        caught.addSuppressed(cleanupException);
      }
      throw caught;
    }
    return toResponse(project, entity, record);
  }

  private EntityDefinition buildFormEntity(String formName, String label, boolean createDisplayField) {
    EntityDefinition entity = new EntityDefinition(formName);
    entity.setComment(label);
    systemFields().forEach(entity::addField);
    entity.addIndex(new IndexDefinition(formName, "idx_" + formName + "_owner").addField("owner_id"));
    if (createDisplayField) {
      entity.addField(new StringField(NAME_FIELD)
        .setLength(255)
        .setComment("名称")
        .setNullable(false));
      entity.addIndex(new IndexDefinition(formName, "idx_" + formName + "_name")
        .addField(NAME_FIELD));
    }
    return entity;
  }

  private Map<String, AppFormFieldRecord> addPreparedFields(
    String entityName,
    EntityDefinition entity,
    List<PreparedField> preparedFields
  ) {
    Map<String, AppFormFieldRecord> fieldRecords = new LinkedHashMap<>();
    for (PreparedField preparedField : preparedFields) {
      String fieldName = resolveFieldName(entity, preparedField.name(), preparedField.label());
      TypedField<?, ?> field = buildField(
        entityName, fieldName, preparedField.type(), preparedField.label(), preparedField.required());
      entity.addField(field);
      fieldRecords.put(fieldName, new AppFormFieldRecord(
        fieldName,
        preparedField.label(),
        preparedField.type(),
        preparedField.required(),
        false,
        preparedField.options(),
        preparedField.referenceForm(), preparedField.config()));
    }
    return fieldRecords;
  }

  private String validateFormName(String name) {
    if (isBlank(name)) {
      throw new ValidationException("表单名称不能为空");
    }
    String formName = name.trim();
    if (!FORM_NAME_PATTERN.matcher(formName).matches()) {
      throw new ValidationException("表单名称只能以小写字母开头，并包含小写字母、数字或下划线");
    }
    return formName;
  }

  private String validateFormLabel(String label) {
    if (isBlank(label)) {
      throw new ValidationException("表单名称不能为空");
    }
    return label.trim();
  }

  private List<PreparedField> prepareFields(
    String appId,
    List<AppFormFieldCreateRequest> fields
  ) {
    if (fields == null) {
      return List.of();
    }
    return fields.stream().map(field -> {
      if (field == null) {
        throw new ValidationException("字段定义不能为空");
      }
      if (isBlank(field.label())) {
        throw new ValidationException("字段名称不能为空");
      }
      if (isBlank(field.type())) {
        throw new ValidationException("字段类型不能为空");
      }
      validateFieldType(field.type());
      return new PreparedField(
        field.label().trim(),
        normalizeFieldName(field.name()),
        field.type(),
        field.required(),
        normalizeOptions(field.type(), field.options()),
        normalizeReferenceForm(appId, field.type(), field.referenceForm()),
        AppFieldRules.validateConfig(field.type(), field.config()));
    }).toList();
  }

  public AppFormResponse updateForm(String appId, String formName, AppFormUpdateRequest request) {
    requireNotBuiltinForm(formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    AppFormRecord current = resolved.record() == null ? defaultRecord(project, entity) : resolved.record();
    String displayField = current.displayField();
    String searchField = current.searchField();
    if (request.displayField() != null) {
      String requestedDisplayField = request.displayField().trim();
      if (requestedDisplayField.isBlank()) {
        displayField = null;
        searchField = null;
      } else {
        requireField(entity, requestedDisplayField);
        if (BUILTIN_FIELDS.contains(requestedDisplayField)) {
          throw new ValidationException("主显字段不能是内置字段: " + requestedDisplayField);
        }
        displayField = requestedDisplayField;
        searchField = requestedDisplayField;
      }
    }
    AppFormRecord updated = new AppFormRecord(
      current.name(),
      request.label().trim(),
      request.description() == null ? current.description() : request.description(),
      displayField,
      searchField,
      current.createdAt(),
      LocalDateTime.now(),
      current.fields(),
      current.layout(),
      current.viewTabs());
    saveFormRecord(project, updated);
    return toResponse(project, entity, updated);
  }

  public AppFormResponse saveFormLayout(
    String appId,
    String formName,
    AppFormLayoutRequest request
  ) {
    if (BUILTIN_SYSTEM_FORM_NAMES.contains(formName)) {
      SystemFormLayoutRecord layout = new SystemFormLayoutRecord(request.items().stream()
        .map(item -> new SystemFormItemRecord(
          item.type(),
          item.name(),
          item.label(),
          item.tip(),
          item.width(),
          item.hidden(),
          item.readonly(),
          item.formName(),
          item.referenceField()))
        .toList());
      return systemFormService.toAppFormResponse(systemFormService.saveLayout(formName, layout));
    }
    requireNotBuiltinForm(formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    AppFormRecord current = resolved.record() == null ? defaultRecord(project, entity) : resolved.record();
    AppFormLayoutRecord layout = normalizeLayoutItems(project, appId, entity, request);
    AppFormRecord updated = withLayout(current, layout);
    saveFormRecord(project, updated);
    return toResponse(project, entity, updated);
  }

  public AppFormResponse patchForm(String appId, String formName, AppFormPatchRequest request) {
    requireNotBuiltinForm(formName);
    if (request == null || request.viewTabs() == null) {
      throw new ValidationException("viewTabs 不能为空");
    }
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    AppFormRecord current = resolved.record() == null ? defaultRecord(project, entity) : resolved.record();
    List<AppFormViewTabRecord> viewTabs = normalizeViewTabs(current, request.viewTabs());
    AppFormRecord updated = new AppFormRecord(
      current.name(),
      current.label(),
      current.description(),
      current.displayField(),
      current.searchField(),
      current.createdAt(),
      LocalDateTime.now(),
      current.fields(),
      current.layout(),
      viewTabs);
    saveFormRecord(project, updated);
    return toResponse(project, entity, updated);
  }

  private List<AppFormViewTabRecord> normalizeViewTabs(
    AppFormRecord current,
    List<AppFormViewTabRecord> viewTabs
  ) {
    if (viewTabs.size() > MAX_VIEW_TABS) {
      throw new ValidationException("视图页签数量不能超过 " + MAX_VIEW_TABS);
    }
    Map<String, AppFormFieldRecord> fields = current.fields().stream()
      .collect(Collectors.toMap(AppFormFieldRecord::name, Function.identity(), (left, right) -> right));
    Set<String> ids = new LinkedHashSet<>();
    Set<String> labels = new LinkedHashSet<>();
    List<AppFormViewTabRecord> result = new ArrayList<>();
    for (AppFormViewTabRecord tab : viewTabs) {
      if (tab == null || isBlank(tab.label())) {
        throw new ValidationException("视图页签名称不能为空");
      }
      String label = tab.label().trim();
      if (label.length() > 32) {
        throw new ValidationException("视图页签名称不能超过 32 个字符");
      }
      if (!labels.add(label)) {
        throw new ValidationException("视图页签名称重复: " + label);
      }
      String id = isBlank(tab.id()) ? UUID.randomUUID().toString() : tab.id().trim();
      if (id.length() > 64 || "all".equals(id) || !ids.add(id)) {
        throw new ValidationException("视图页签标识不合法或重复: " + id);
      }
      result.add(new AppFormViewTabRecord(id, label, normalizeViewTabFilter(fields, tab.filter())));
    }
    return List.copyOf(result);
  }

  private Map<String, Object> normalizeViewTabFilter(
    Map<String, AppFormFieldRecord> fields,
    Map<String, Object> filter
  ) {
    if (filter == null || filter.size() != 1 || !(filter.get("_and") instanceof List<?> conditions)) {
      throw new ValidationException("视图页签筛选条件必须为 _and 结构");
    }
    if (conditions.isEmpty() || conditions.size() > MAX_VIEW_TAB_CONDITIONS) {
      throw new ValidationException("视图页签筛选条件数量必须在 1 到 " + MAX_VIEW_TAB_CONDITIONS + " 之间");
    }
    List<Object> normalizedConditions = new ArrayList<>();
    for (Object conditionValue : conditions) {
      if (!(conditionValue instanceof Map<?, ?> condition) || condition.size() != 1) {
        throw new ValidationException("视图页签每个筛选条件只能包含一个字段");
      }
      String fieldName = String.valueOf(condition.keySet().iterator().next());
      if ("id".equals(fieldName)) {
        throw new ValidationException("视图页签筛选条件不支持 id 字段");
      }
      AppFormFieldRecord field = fields.get(fieldName);
      if (field == null) {
        throw new ValidationException("视图页签筛选字段不存在: " + fieldName);
      }
      if (!(condition.get(fieldName) instanceof Map<?, ?> operatorMap) || operatorMap.size() != 1) {
        throw new ValidationException("视图页签筛选字段只能配置一个操作符: " + field.label());
      }
      String operator = String.valueOf(operatorMap.keySet().iterator().next());
      if (!viewTabOperators(field.type()).contains(operator)) {
        throw new ValidationException("字段不支持筛选操作符: " + field.label() + " " + operator);
      }
      Object value = normalizeViewTabValue(field, operatorMap.get(operator));
      Map<String, Object> normalizedOperator = new LinkedHashMap<>();
      normalizedOperator.put(operator, value);
      Map<String, Object> normalizedCondition = new LinkedHashMap<>();
      normalizedCondition.put(fieldName, normalizedOperator);
      normalizedConditions.add(normalizedCondition);
    }
    return Map.of("_and", normalizedConditions);
  }

  private Set<String> viewTabOperators(String type) {
    return switch (type) {
      case "NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME" -> Set.of("_eq", "_ne", "_gt", "_gte", "_lt", "_lte");
      case "BOOL" -> Set.of("_eq", "_ne");
      case "PICKLIST", "SELECT", "CLASSIFICATION", "MULTISELECT", "REFERENCE" -> Set.of("_eq", "_ne");
      default -> Set.of("_eq", "_ne", "_contains", "_not_contains", "_starts_with", "_ends_with");
    };
  }

  private Object normalizeViewTabValue(AppFormFieldRecord field, Object value) {
    if (value == null) {
      throw new ValidationException("视图页签筛选值不能为空: " + field.label());
    }
    return switch (field.type()) {
      case "NUMBER", "DECIMAL" -> {
        if (!(value instanceof Number)) {
          throw new ValidationException("数字筛选值不合法: " + field.label());
        }
        yield value;
      }
      case "BOOL" -> {
        if (!(value instanceof Boolean)) {
          throw new ValidationException("布尔筛选值不合法: " + field.label());
        }
        yield value;
      }
      case "DATE" -> {
        if (!(value instanceof String text) || text.isBlank()) {
          throw new ValidationException("日期筛选值不合法: " + field.label());
        }
        try {
          yield LocalDate.parse(text).toString();
        } catch (RuntimeException caught) {
          throw new ValidationException("日期筛选值不合法: " + field.label());
        }
      }
      case "DATETIME" -> {
        if (!(value instanceof String text) || text.isBlank()) {
          throw new ValidationException("日期时间筛选值不合法: " + field.label());
        }
        try {
          yield LocalDateTime.parse(text).toString();
        } catch (RuntimeException caught) {
          throw new ValidationException("日期时间筛选值不合法: " + field.label());
        }
      }
      case "TIME" -> {
        if (!(value instanceof String text) || text.isBlank()) {
          throw new ValidationException("时间筛选值不合法: " + field.label());
        }
        try {
          yield LocalTime.parse(text).toString();
        } catch (RuntimeException caught) {
          throw new ValidationException("时间筛选值不合法: " + field.label());
        }
      }
      default -> {
        if (!(value instanceof String text) || text.isBlank()) {
          throw new ValidationException("筛选值不能为空: " + field.label());
        }
        yield text;
      }
    };
  }

  private List<AppFormViewTabRecord> withoutViewTabField(
    List<AppFormViewTabRecord> viewTabs,
    String fieldName
  ) {
    if (viewTabs == null || viewTabs.isEmpty()) {
      return List.of();
    }
    List<AppFormViewTabRecord> result = new ArrayList<>();
    for (AppFormViewTabRecord tab : viewTabs) {
      if (tab == null || tab.filter() == null || !(tab.filter().get("_and") instanceof List<?> conditions)) {
        continue;
      }
      List<?> filtered = conditions.stream()
        .filter(condition -> !(condition instanceof Map<?, ?> map) || !map.containsKey(fieldName))
        .toList();
      if (filtered.isEmpty()) {
        continue;
      }
      if (filtered.size() == conditions.size()) {
        result.add(tab);
      } else {
        result.add(new AppFormViewTabRecord(tab.id(), tab.label(), Map.of("_and", filtered)));
      }
    }
    return List.copyOf(result);
  }

  public void deleteForm(String appId, String formName) {
    requireNotBuiltinForm(formName);
    appApprovalLifecycle.beforeDeleteForm(appId, formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    if (isBoundToMenu(project, formName)) {
      throw new ValidationException("表单已被菜单绑定，请先解除菜单关联");
    }
    if (isFormUsedAsSubform(project, formName)) {
      throw new ValidationException("表单已被子表单引用，请先移除相关子表单布局");
    }
    modelingService.dropModel(appId, entityName);
    try {
      appRecordDiscussionRepository.deleteByForm(appId, formName);
    } catch (RuntimeException error) {
      log.warn("Failed to clean form discussions for {}/{}: {}", appId, formName, error.getMessage());
    }
    List<AppFormRecord> records = formRecords(project).stream()
      .filter(record -> !record.name().equals(entityName))
      .toList();
    persistFormRecords(project, records);
  }

  public AppFormFieldResponse createField(
    String appId,
    String formName,
    AppFormFieldCreateRequest request
  ) {
    if (BUILTIN_SYSTEM_FORM_NAMES.contains(formName)) {
      return systemFormService.createField(formName, request);
    }
    requireNotBuiltinForm(formName);
    validateFieldType(request.type());
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    String fieldName = resolveFieldName(entity, request.name(), request.label());
    List<AppFormFieldOptionResponse> options = normalizeOptions(request.type(), request.options());
    AppFieldConfig config = AppFieldRules.validateConfig(request.type(), request.config());
    String referenceForm = normalizeReferenceForm(appId, request.type(), request.referenceForm());
    TypedField<?, ?> field = buildField(entityName, fieldName, request.type(), request.label().trim(),
      request.required());
    modelingService.createField(appId, field);
    AppFormFieldRecord fieldRecord = new AppFormFieldRecord(
      fieldName,
      request.label().trim(),
      request.type(),
      request.required(),
      false,
      options,
      referenceForm, config);
    try {
      AppFormRecord current = findFormRecord(project, entityName).orElseGet(() -> defaultRecord(project, entity));
      List<AppFormFieldRecord> fields = new ArrayList<>(current.fields());
      fields.add(fieldRecord);
      AppFormLayoutRecord layout = current.layout() == null
        ? new AppFormLayoutRecord(List.of())
        : current.layout();
      saveFormRecord(project, withLayout(withFields(current, fields), layout));
    } catch (RuntimeException caught) {
      modelingService.dropField(appId, entityName, fieldName);
      throw caught;
    }
    return toFieldResponse(project, field, fieldRecord);
  }

  public AppFormFieldResponse updateField(
    String appId,
    String formName,
    String fieldName,
    AppFormFieldUpdateRequest request
  ) {
    if (BUILTIN_SYSTEM_FORM_NAMES.contains(formName)) {
      return systemFormService.updateField(formName, fieldName, request);
    }
    requireNotBuiltinForm(formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    TypedField<?, ?> field = requireField(entity, fieldName);
    if (BUILTIN_FIELDS.contains(fieldName)) {
      throw new ValidationException("内置字段不允许修改");
    }
    String currentType = java.util.Optional.ofNullable(resolved.record())
      .flatMap(record -> record.fields().stream()
        .filter(item -> item.name().equals(fieldName))
        .findFirst())
      .map(AppFormFieldRecord::type)
      .orElseGet(() -> inferFieldType(field));
    String currentReferenceForm = resolved.record() == null
      ? null
      : resolved.record().fields().stream()
      .filter(item -> item.name().equals(fieldName))
      .findFirst()
      .map(AppFormFieldRecord::referenceForm)
      .orElse(null);
    if (isFieldUsedAsSubform(project, formName, fieldName)
      && request.referenceForm() != null
      && !request.referenceForm().trim().equals(currentReferenceForm)) {
      throw new ValidationException("子表单引用字段不允许修改引用表单");
    }
    List<AppFormFieldOptionResponse> currentOptions = resolved.record() == null ? null : resolved.record().fields().stream()
      .filter(item -> item.name().equals(fieldName)).map(AppFormFieldRecord::options)
      .filter(Objects::nonNull).findFirst().orElse(null);
    List<AppFormFieldOptionResponse> options = normalizeOptions(currentType,
      request.options() == null ? currentOptions : request.options());
    AppFieldConfig currentConfig = resolved.record() == null ? null : resolved.record().fields().stream()
      .filter(item -> item.name().equals(fieldName)).map(AppFormFieldRecord::config)
      .filter(Objects::nonNull).findFirst().orElse(null);
    AppFieldConfig config = AppFieldRules.validateConfig(currentType,
      request.config() == null ? currentConfig : request.config());
    String referenceForm = normalizeReferenceForm(appId, currentType,
      request.referenceForm() == null ? currentReferenceForm : request.referenceForm());
    field.setComment(request.label().trim());
    field.setNullable(!request.required());
    if (Set.of("FILE", "IMAGE", "AVATAR").contains(currentType) && field instanceof StringField stringField) {
      stringField.setLength(Math.max(stringField.getLength() == null ? 0 : stringField.getLength(), 4000));
    }
    modelingService.modifyField(appId, field);

    AppFormFieldRecord updatedRecord = new AppFormFieldRecord(
      fieldName,
      request.label().trim(),
      currentType,
      request.required(),
      false,
      options,
      referenceForm, config);
    AppFormRecord current = findFormRecord(project, entityName).orElseGet(() -> defaultRecord(project, entity));
    List<AppFormFieldRecord> fields = current.fields().stream()
      .map(item -> item.name().equals(fieldName) ? updatedRecord : item)
      .collect(Collectors.toCollection(ArrayList::new));
    if (fields.stream().noneMatch(item -> item.name().equals(fieldName))) {
      fields.add(updatedRecord);
    }
    saveFormRecord(project, withFields(current, fields));
    return toFieldResponse(project, field, updatedRecord);
  }

  public void deleteField(String appId, String formName, String fieldName) {
    requireNotBuiltinForm(formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    requireField(entity, fieldName);
    if (BUILTIN_FIELDS.contains(fieldName)) {
      throw new ValidationException("内置字段不允许删除");
    }
    if (isFieldUsedAsSubform(project, formName, fieldName)) {
      throw new ValidationException("字段已被子表单引用，请先移除相关子表单布局");
    }
    entity.getIndexes().stream()
      .filter(index -> index.containsField(fieldName))
      .toList()
      .forEach(index -> modelingService.dropIndex(appId, entityName, index.getName()));
    modelingService.dropField(appId, entityName, fieldName);
    java.util.Optional.ofNullable(resolved.record()).ifPresent(current -> {
      List<AppFormFieldRecord> fields = current.fields().stream()
        .filter(item -> !item.name().equals(fieldName))
        .toList();
      String displayField = fieldName.equals(current.displayField()) ? null : current.displayField();
      String searchField = fieldName.equals(current.searchField()) ? null : current.searchField();
      saveFormRecord(project, new AppFormRecord(
        current.name(),
        current.label(),
        current.description(),
        displayField,
        searchField,
        current.createdAt(),
        LocalDateTime.now(),
        fields,
        withoutLayoutField(current.layout(), fieldName),
        withoutViewTabField(current.viewTabs(), fieldName)));
    });
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  private ResolvedForm requireForm(Project project, String appId, String formName) {
    if (RESERVED_SYSTEM_FORM_NAMES.contains(formName)) {
      throw new NotFoundException("表单不存在: " + formName);
    }
    AppFormRecord record = formRecords(project).stream()
      .filter(item -> item.name().equals(formName))
      .findFirst()
      .orElse(null);
    if (record != null) {
      return new ResolvedForm(requireEntity(appId, record.name()), record);
    }
    EntityDefinition entity = entities(appId).stream()
      .filter(item -> formName(item).equals(formName))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("表单不存在: " + formName));
    return new ResolvedForm(entity, null);
  }

  private void requireNotBuiltinForm(String formName) {
    if (BUILTIN_SYSTEM_FORM_NAMES.contains(formName)) {
      throw new ValidationException("内置表单不允许修改");
    }
  }

  private String formName(EntityDefinition entity) {
    return entity.getName();
  }

  private record ResolvedForm(EntityDefinition entity, AppFormRecord record) {
  }

  private record PreparedField(
    String label,
    String name,
    String type,
    boolean required,
    List<AppFormFieldOptionResponse> options,
    String referenceForm,
    AppFieldConfig config
  ) {
  }

  private List<EntityDefinition> entities(String appId) {
    return modelingService.findModels(appId).stream()
      .filter(EntityDefinition.class::isInstance)
      .map(EntityDefinition.class::cast)
      .filter(entity -> !dev.flexmodel.apps.dto.AppTransfer.RECEIPT.equals(entity.getName()))
      .toList();
  }

  private EntityDefinition requireEntity(String appId, String entityName) {
    return entities(appId).stream()
      .filter(entity -> entity.getName().equals(entityName))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("表单不存在: " + entityName));
  }

  private TypedField<?, ?> requireField(EntityDefinition entity, String fieldName) {
    TypedField<?, ?> field = entity.getField(fieldName);
    if (field == null) {
      throw new NotFoundException("字段不存在: " + fieldName);
    }
    return field;
  }

  private Project requireAppProject(String appId) {
    Project project = projectService.findProject(appId);
    if (project == null || project.getParentProjectId() != null || "default".equals(appId)) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return project;
  }

  private List<TypedField<?, ?>> systemFields() {
    List<TypedField<?, ?>> fields = new ArrayList<>();
    fields.add(new StringField("id")
      .asIdentity()
      .setNullable(false)
      .setDefaultValue(DefaultValue.UUID)
      .setLength(64)
      .setComment("ID"));
    fields.add(new StringField("created_by").setLength(64).setComment("创建人"));
    fields.add(new StringField("owner_id").setLength(64).setNullable(false).setComment("所有者"));
    fields.add(new StringField("updated_by").setLength(64).setComment("更新人"));
    fields.add(new DateTimeField("created_at").setDefaultValue(DefaultValue.NOW).setComment("创建时间"));
    fields.add(new DateTimeField("updated_at").setDefaultValue(DefaultValue.NOW).setComment("更新时间"));
    return fields;
  }

  private Map<String, AppFormFieldRecord> systemFieldRecords() {
    Map<String, AppFormFieldRecord> fields = new LinkedHashMap<>();
    fields.put("owner_id", new AppFormFieldRecord(
      "owner_id", "所有者", "REFERENCE", false, true, null, SystemFormService.USER_FORM));
    fields.put("created_by", new AppFormFieldRecord(
      "created_by", "创建人", "REFERENCE", false, true, null, SystemFormService.USER_FORM));
    fields.put("updated_by", new AppFormFieldRecord(
      "updated_by", "更新人", "REFERENCE", false, true, null, SystemFormService.USER_FORM));
    return fields;
  }

  private TypedField<?, ?> buildField(
    String entityName,
    String fieldName,
    String type,
    String label,
    boolean required
  ) {
    TypedField<?, ?> field = switch (type) {
      case "NTEXT" -> new StringField(fieldName).setLength(4000);
      case "FILE", "IMAGE", "AVATAR" -> new StringField(fieldName).setLength(4000);
      case "PHONE", "EMAIL", "URL", "TAG", "BARCODE", "LOCATION", "SIGN", "STATE" ->
        new StringField(fieldName).setLength(512);
      case "SERIES" -> new StringField(fieldName).setLength(64);
      case "PICKLIST", "SELECT", "CLASSIFICATION" -> new StringField(fieldName).setLength(255);
      case "TEXT" -> new StringField(fieldName).setLength(255);
      case "NUMBER" -> new IntField(fieldName);
      case "DECIMAL" -> new FloatField(fieldName);
      case "DATE" -> new DateField(fieldName);
      case "DATETIME" -> new DateTimeField(fieldName);
      case "TIME" -> new TimeField(fieldName);
      case "BOOL" -> new BooleanField(fieldName);
      case "MULTISELECT" -> new JSONField(fieldName);
      case "REFERENCE" -> new StringField(fieldName).setLength(64);
      default -> throw new ValidationException("字段类型不合法: " + type);
    };
    field.setModelName(entityName);
    field.setComment(label);
    field.setNullable(!required);
    return field;
  }

  private void validateFieldType(String type) {
    if (!FIELD_TYPES.contains(type)) {
      throw new ValidationException("字段类型不合法: " + type);
    }
  }

  private List<AppFormFieldOptionResponse> normalizeOptions(
    String type,
    List<AppFormFieldOptionResponse> options
  ) {
    if (!OPTION_TYPES.contains(type)) {
      return null;
    }
    if (options == null) {
      return List.of(
        new AppFormFieldOptionResponse("1", "选项一", null, false),
        new AppFormFieldOptionResponse("2", "选项二", null, false));
    }
    if (!"MULTISELECT".equals(type) && options.stream().filter(Objects::nonNull)
      .filter(option -> Boolean.TRUE.equals(option.defaultSelected())).count() > 1) {
      throw new ValidationException("单选字段只能设置一个默认选项");
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    return options.stream()
      .map(option -> {
        if (option == null || isBlank(option.id()) || isBlank(option.text())) {
          throw new ValidationException("字段选项的ID和展示文本不能为空");
        }
        if (!ids.add(option.id().trim())) {
          throw new ValidationException("字段选项ID重复: " + option.id());
        }
        return new AppFormFieldOptionResponse(
          option.id().trim(),
          option.text().trim(),
          option.color(),
          Boolean.TRUE.equals(option.defaultSelected()));
      })
      .toList();
  }

  private String normalizeReferenceForm(String appId, String type, String referenceForm) {
    if (!REFERENCE_TYPES.contains(type)) {
      return null;
    }
    if (isBlank(referenceForm)) {
      throw new ValidationException("引用字段必须选择关联表单");
    }
    String normalizedReferenceForm = referenceForm.trim();
    if (findForms(appId).stream().noneMatch(form -> form.name().equals(normalizedReferenceForm))) {
      throw new ValidationException("引用表单不存在: " + referenceForm);
    }
    return normalizedReferenceForm;
  }

  private AppFormResponse toResponse(Project project, EntityDefinition entity, AppFormRecord record) {
    String formName = record == null ? formName(entity) : record.name();
    String label = record == null ? blankToDefault(entity.getComment(), entity.getName()) : record.label();
    String description = record == null ? "" : blankToDefault(record.description(), "");
    String displayField = record == null ? defaultDisplayField(entity) : record.displayField();
    String searchField = record == null ? displayField : record.searchField();
    AppFormLayoutRecord layout = record == null || record.layout() == null
      ? new AppFormLayoutRecord(List.of())
      : record.layout();
    List<AppFormViewTabRecord> viewTabs = record == null || record.viewTabs() == null
      ? List.of()
      : record.viewTabs();
    return new AppFormResponse(
      formName,
      label,
      description,
      displayField,
      searchField,
      record != null,
      false,
      record == null ? project.getCreatedAt() : record.createdAt(),
      record == null ? project.getUpdatedAt() : record.updatedAt(),
      entity.getFields().stream()
        .map(field -> toFieldResponse(project, field, findFieldRecord(record, field.getName()).orElse(null)))
        .toList(),
      layout,
      viewTabs);
  }

  private AppFormFieldResponse toFieldResponse(
    Project project,
    TypedField<?, ?> field,
    AppFormFieldRecord record
  ) {
    String type = record == null ? inferFieldType(field) : record.type();
    String referenceForm = record != null && !isBlank(record.referenceForm())
      ? record.referenceForm()
      : referenceForm(project, field);
    return new AppFormFieldResponse(
      field.getName(),
      record == null ? blankToDefault(field.getComment(), field.getName()) : record.label(),
      type,
      record == null ? !field.isNullable() : record.required(),
      BUILTIN_FIELDS.contains(field.getName()) || (record != null && record.builtin()),
      record == null ? null : record.options(),
      referenceForm, false, false, record == null ? null : record.config());
  }

  private String referenceForm(Project project, TypedField<?, ?> field) {
    if (!(field instanceof ModelRefField refField)) {
      return null;
    }
    String targetEntityName = refField.getFrom();
    return formRecords(project).stream()
      .filter(record -> record.name().equals(targetEntityName))
      .findFirst()
      .map(AppFormRecord::name)
      .orElse(targetEntityName);
  }

  private AppFormFieldRecord toFieldRecord(TypedField<?, ?> field, AppFormFieldRecord record) {
    String type = record == null ? inferFieldType(field) : record.type();
    return new AppFormFieldRecord(
      field.getName(),
      record == null ? blankToDefault(field.getComment(), field.getName()) : record.label(),
      type,
      record == null ? !field.isNullable() : record.required(),
      BUILTIN_FIELDS.contains(field.getName()) || (record != null && record.builtin()),
      record == null ? null : record.options(),
      record == null ? null : record.referenceForm(), record == null ? null : record.config());
  }

  private String inferFieldType(TypedField<?, ?> field) {
    if (field instanceof ModelRefField) return "REFERENCE";
    if (field instanceof JSONField) return "MULTISELECT";
    if (field instanceof IntField) return "NUMBER";
    if (field instanceof FloatField) return "DECIMAL";
    if (field instanceof BooleanField) return "BOOL";
    if (field instanceof DateField) return "DATE";
    if (field instanceof DateTimeField) return "DATETIME";
    if (field instanceof TimeField) return "TIME";
    return "TEXT";
  }

  private AppFormRecord defaultRecord(Project project, EntityDefinition entity) {
    String displayField = defaultDisplayField(entity);
    return new AppFormRecord(
      entity.getName(),
      blankToDefault(entity.getComment(), entity.getName()),
      "",
      displayField,
      displayField,
      project.getCreatedAt(),
      project.getUpdatedAt(),
      entity.getFields().stream().map(field -> toFieldRecord(field, null)).toList(),
      new AppFormLayoutRecord(List.of()),
      List.of());
  }

  private String defaultDisplayField(EntityDefinition entity) {
    return entity.getField(NAME_FIELD) == null ? null : NAME_FIELD;
  }

  private AppFormRecord withFields(AppFormRecord record, List<AppFormFieldRecord> fields) {
    return new AppFormRecord(
      record.name(),
      record.label(),
      record.description(),
      record.displayField(),
      record.searchField(),
      record.createdAt(),
      LocalDateTime.now(),
      fields,
      record.layout(),
      record.viewTabs());
  }

  private java.util.Optional<AppFormRecord> findFormRecord(Project project, String formName) {
    return formRecords(project).stream()
      .filter(record -> record.name().equals(formName))
      .findFirst();
  }

  private java.util.Optional<AppFormFieldRecord> findFieldRecord(AppFormRecord record, String fieldName) {
    if (record == null) {
      return java.util.Optional.empty();
    }
    return record.fields().stream()
      .filter(field -> field.name().equals(fieldName))
      .findFirst();
  }

  private void saveFormRecord(Project project, AppFormRecord replacement) {
    List<AppFormRecord> records = formRecords(project).stream()
      .filter(record -> !record.name().equals(replacement.name()))
      .collect(Collectors.toCollection(ArrayList::new));
    records.add(replacement);
    persistFormRecords(project, records);
  }

  private void persistFormRecords(Project project, List<AppFormRecord> records) {
    Map<String, Object> metadata = metadataMap(project);
    Map<String, Object> app = appNode(project);
    List<Map<String, Object>> converted = records.stream()
      .map(record -> {
        @SuppressWarnings("unchecked")
        Map<String, Object> value = JsonUtils.convertValue(record, Map.class);
        return value;
      })
      .toList();
    app.put(METADATA_FORMS_KEY, converted);
    Map<String, Set<String>> children = new LinkedHashMap<>();
    for (AppFormRecord record : records)
      children.put(record.name(), layoutItems(record.layout()).stream()
        .filter(item -> "SUBFORM".equals(item.type())).map(AppFormItemRecord::formName).collect(Collectors.toSet()));
    dataAuthorization.reconcile(project.getId(), records.stream().map(AppFormRecord::name).collect(Collectors.toSet()), children);
    app.remove(METADATA_LEGACY_FORM_LAYOUTS_KEY);
    metadata.put(METADATA_APP_KEY, app);
    project.setMetadata(metadata);
    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());
    projectService.updateProject(project);
  }

  private AppFormLayoutRecord normalizeLayoutItems(
    Project project,
    String appId,
    EntityDefinition entity,
    AppFormLayoutRequest request
  ) {
    if (request.items() == null || request.items().isEmpty() || request.items().size() > MAX_LAYOUT_ITEMS) {
      throw new ValidationException("表单布局项数量不合法");
    }
    Set<String> fieldNames = entity.getFields().stream()
      .map(TypedField::getName)
      .collect(Collectors.toSet());
    LinkedHashSet<String> usedFieldNames = new LinkedHashSet<>();
    Set<String> subformKeys = new LinkedHashSet<>();
    List<AppFormItemRecord> items = request.items().stream()
      .map(item -> normalizeLayoutItem(
        project, appId, fieldNames, item, usedFieldNames, subformKeys))
      .toList();
    if (items.stream().noneMatch(item -> "FIELD".equals(item.type()))) {
      throw new ValidationException("表单布局必须包含字段");
    }
    return new AppFormLayoutRecord(items);
  }

  private AppFormItemRecord normalizeLayoutItem(
    Project project,
    String appId,
    Set<String> fieldNames,
    AppFormItemRecord item,
    Set<String> usedFieldNames,
    Set<String> subformKeys
  ) {
    String type = item == null ? null : item.type();
    if (!FORM_ITEM_TYPES.contains(type)) {
      throw new ValidationException("表单项类型不合法: " + type);
    }
    if (item.width() != null && !FORM_ITEM_WIDTHS.contains(item.width())) {
      throw new ValidationException("字段宽度类型不合法: " + item.width());
    }
    return switch (type) {
      case "FIELD" -> {
        if (!isBlank(item.formName()) || !isBlank(item.referenceField())) {
          throw new ValidationException("布局项配置不合法");
        }
        String fieldName = item.name() == null ? null : item.name().trim();
        if (isBlank(fieldName) || !fieldNames.contains(fieldName)) {
          throw new ValidationException("布局字段不存在: " + item.name());
        }
        if (!usedFieldNames.add(fieldName)) {
          throw new ValidationException("布局字段重复: " + fieldName);
        }
        yield new AppFormItemRecord(
          "FIELD",
          fieldName,
          normalizeLayoutText(item.label()),
          normalizeLayoutText(item.tip()),
          item.width(),
          item.hidden(),
          item.readonly(),
          null,
          null);
      }
      case "SECTION" -> {
        if (!isBlank(item.name()) || !isBlank(item.formName()) || !isBlank(item.referenceField())) {
          throw new ValidationException("布局项配置不合法");
        }
        String label = normalizeLayoutText(item.label());
        if (label == null) {
          throw new ValidationException("分栏名称不能为空");
        }
        yield new AppFormItemRecord(
          "SECTION",
          null,
          label,
          null,
          item.width(),
          null,
          null,
          null,
          null);
      }
      default -> {
        if (!isBlank(item.name())) {
          throw new ValidationException("布局项字段名不合法");
        }
        if (isBlank(item.formName())) {
          throw new ValidationException("子表单不存在: " + item.formName());
        }
        if (isBlank(item.referenceField())) {
          throw new ValidationException("子表单引用字段不合法: " + item.referenceField());
        }
        String formName = item.formName().trim();
        String referenceField = item.referenceField().trim();
        validateSubformItem(project, appId, formName, referenceField);
        if (!subformKeys.add(formName + '\0' + referenceField)) {
          throw new ValidationException("子表单重复");
        }
        yield new AppFormItemRecord(
          "SUBFORM",
          null,
          normalizeLayoutText(item.label()),
          null,
          item.width(),
          null,
          null,
          formName,
          referenceField);
      }
    };
  }

  private void validateSubformItem(
    Project project,
    String appId,
    String formName,
    String referenceField
  ) {
    ResolvedForm child;
    try {
      child = requireForm(project, appId, formName);
    } catch (NotFoundException caught) {
      throw new ValidationException("子表单不存在: " + formName);
    }
    boolean referenceFieldExists = child.entity().getFields().stream()
      .map(TypedField::getName)
      .anyMatch(name -> name.equals(referenceField) && !BUILTIN_FIELDS.contains(name));
    if (!referenceFieldExists) {
      throw new ValidationException("子表单引用字段不合法: " + referenceField);
    }
  }

  private boolean isFormUsedAsSubform(Project project, String formName) {
    return formRecords(project).stream()
      .flatMap(record -> layoutItems(record.layout()).stream())
      .anyMatch(item -> "SUBFORM".equals(item.type()) && formName.equals(item.formName()));
  }

  private boolean isFieldUsedAsSubform(Project project, String formName, String fieldName) {
    return formRecords(project).stream()
      .flatMap(record -> layoutItems(record.layout()).stream())
      .anyMatch(item -> "SUBFORM".equals(item.type())
        && formName.equals(item.formName())
        && fieldName.equals(item.referenceField()));
  }

  private AppFormRecord withLayout(AppFormRecord record, AppFormLayoutRecord layout) {
    return new AppFormRecord(
      record.name(),
      record.label(),
      record.description(),
      record.displayField(),
      record.searchField(),
      record.createdAt(),
      LocalDateTime.now(),
      record.fields(),
      layout,
      record.viewTabs());
  }

  private java.util.Optional<AppFormItemRecord> findLayoutFieldItem(
    AppFormLayoutRecord layout,
    String fieldName
  ) {
    return layoutItems(layout).stream()
      .filter(item -> "FIELD".equals(item.type()) && fieldName.equals(item.name()))
      .findFirst();
  }

  private AppFormLayoutRecord withoutLayoutField(AppFormLayoutRecord layout, String fieldName) {
    return new AppFormLayoutRecord(layoutItems(layout).stream()
      .filter(item -> !"FIELD".equals(item.type()) || !fieldName.equals(item.name()))
      .toList());
  }

  private List<AppFormItemRecord> layoutItems(AppFormLayoutRecord layout) {
    return layout == null || layout.items() == null ? List.of() : layout.items();
  }

  private String normalizeLayoutText(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private List<AppFormRecord> formRecords(Project project) {
    Object value = appNode(project).get(METADATA_FORMS_KEY);
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
      .map(item -> JsonUtils.convertValue(item, AppFormRecord.class))
      .filter(Objects::nonNull)
      .toList();
  }

  private boolean isBoundToMenu(Project project, String formName) {
    Object value = appNode(project).get("menus");
    if (!(value instanceof List<?> list)) {
      return false;
    }
    AppMenuRecord[] menus = JsonUtils.convertValue(list, AppMenuRecord[].class);
    for (AppMenuRecord menu : menus) {
      if (containsFormTarget(menu, formName)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsFormTarget(AppMenuRecord menu, String formName) {
    if (menu == null) {
      return false;
    }
    AppMenuTargetRecord target = menu.target();
    if (target != null && "FORM".equals(target.type()) && formName.equals(target.id())) {
      return true;
    }
    return menu.children() != null && menu.children().stream().anyMatch(child -> containsFormTarget(child, formName));
  }

  private void ensureUniqueFormName(Project project, String appId, String formName) {
    if (RESERVED_SYSTEM_FORM_NAMES.contains(formName)) {
      throw new ValidationException("琛ㄥ崟鍐呯儴鏍囪瘑宸插瓨鍦? " + formName);
    }
    boolean metadataExists = formRecords(project).stream()
      .anyMatch(record -> record.name().equals(formName));
    boolean schemaObjectExists = modelingService.findModels(appId).stream()
      .map(SchemaObject::getName)
      .anyMatch(formName::equals);
    if (metadataExists || schemaObjectExists) {
      throw new ValidationException("表单内部标识已存在: " + formName);
    }
  }

  private String resolveFieldName(EntityDefinition entity, String requestedName, String label) {
    if (!isBlank(requestedName)) {
      String fieldName = requestedName.trim();
      if (!FORM_NAME_PATTERN.matcher(fieldName).matches()) {
        throw new ValidationException("字段标识只能以小写字母开头，并可包含小写字母、数字或下划线");
      }
      if (!isFieldNameAvailable(entity, fieldName)) {
        throw new ValidationException("字段标识已存在: " + fieldName);
      }
      return fieldName;
    }
    return generateFieldName(entity, label);
  }

  private String normalizeFieldName(String name) {
    if (name == null) {
      return null;
    }
    String normalized = name.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String generateFieldName(EntityDefinition entity, String label) {
    String candidate = friendlyFieldNameFromLabel(label);
    if (candidate != null && isFieldNameAvailable(entity, candidate)) {
      return candidate;
    }
    Set<String> existing = entity.getFields().stream()
      .map(TypedField::getName)
      .collect(Collectors.toSet());
    String generatedName;
    do {
      generatedName = "field_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        .toLowerCase(Locale.ROOT);
    } while (existing.contains(generatedName) || BUILTIN_FIELDS.contains(generatedName) || NAME_FIELD.equals(generatedName));
    return generatedName;
  }

  private String friendlyFieldNameFromLabel(String label) {
    if (isBlank(label)) {
      return null;
    }
    String normalized = label.trim()
      .toLowerCase(Locale.ROOT)
      .replaceAll("[^a-z0-9_]+", "_")
      .replaceAll("^_+|_+$", "");
    if (normalized.isEmpty() || normalized.length() > 64 || !Character.isLetter(normalized.charAt(0))) {
      return null;
    }
    return normalized;
  }

  private boolean isFieldNameAvailable(EntityDefinition entity, String fieldName) {
    return !BUILTIN_FIELDS.contains(fieldName)
      && !NAME_FIELD.equals(fieldName)
      && entity.getFields().stream().noneMatch(field -> field.getName().equals(fieldName));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> metadataMap(Project project) {
    Object metadata = project.getMetadata();
    if (metadata == null) {
      return new LinkedHashMap<>();
    }
    if (metadata instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), value));
      return result;
    }
    return JsonUtils.convertValue(metadata, Map.class);
  }

  private Map<String, Object> appNode(Project project) {
    Object app = metadataMap(project).get(METADATA_APP_KEY);
    if (app == null) {
      return new LinkedHashMap<>();
    }
    if (app instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), value));
      return result;
    }
    return JsonUtils.convertValue(app, Map.class);
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return isBlank(userId) ? "admin" : userId;
  }

  private String blankToDefault(String value, String defaultValue) {
    return isBlank(value) ? defaultValue : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
