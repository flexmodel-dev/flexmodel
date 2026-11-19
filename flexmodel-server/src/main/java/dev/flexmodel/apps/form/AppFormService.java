package dev.flexmodel.apps.form;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.AppRepository;
import dev.flexmodel.apps.flow.AppFlowLifecycle;
import dev.flexmodel.apps.dataauthorization.AppDataAuthorizationService;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.navigation.AppNavigationRepository;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.apps.navigation.dto.NavDestinationRecord;
import dev.flexmodel.apps.record.AppRecordDiscussionRepository;
import dev.flexmodel.apps.record.AppRecordHistoryRepository;
import dev.flexmodel.codegen.entity.*;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.graphql.dto.GraphQLRefreshEvent;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.IndexDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.*;
import dev.flexmodel.modeling.ModelService;
import dev.flexmodel.modeling.ModelingService;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.system.SystemFormService;
import dev.flexmodel.system.dto.SystemFormItemRecord;
import dev.flexmodel.system.dto.SystemFormLayoutRecord;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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
  AppFlowLifecycle appFlowLifecycle;

  private static final Pattern FORM_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
  // 字段标识不限命名风格，仅排除会破坏 SQL 标识符转义的字符。
  private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[^\\x00-\\x1f`\"\\[\\]]{1,64}$");
  private static final int MAX_LAYOUT_ITEMS = 500;
  private static final int MAX_CUSTOM_VIEWS = 20;
  private static final int MAX_VIEW_FILTER_CONDITIONS = 10;
  private static final int MAX_VIEW_DISPLAY_FIELDS = 60;
  private static final int VIEW_LABEL_MAX_LENGTH = 32;
  private static final int VIEW_ID_MAX_LENGTH = 64;
  public static final String ALL_VIEW_ID = "all";
  private static final String ALL_VIEW_LABEL = "全部";
  private static final Set<String> VIEW_TYPES = Set.of(
    "TABLE", "KANBAN", "CALENDAR", "GALLERY", "DETAIL", "HIERARCHY", "GANTT");
  private static final Set<String> VIEW_GROUP_FIELD_TYPES = Set.of(
    "PICKLIST", "SELECT", "CLASSIFICATION", "BOOL", "REFERENCE");
  private static final Set<String> VIEW_DATE_FIELD_TYPES = Set.of("DATE", "DATETIME");
  private static final Set<String> VIEW_ATTACHMENT_FIELD_TYPES = Set.of("FILE", "IMAGE", "AVATAR");
  private static final Set<String> FORM_ITEM_TYPES = Set.of("FIELD", "SECTION", "SUBFORM");
  private static final Set<String> FORM_ITEM_WIDTHS = Set.of(
    "QUARTER", "THIRD", "HALF", "TWO_THIRDS", "THREE_QUARTERS", "FULL");
  private static final Set<String> FORM_CONTROL_SCENES = Set.of("CREATE", "UPDATE");
  private static final String NAME_FIELD = "name";
  private static final Set<String> BUILTIN_FIELDS = Set.of(
    "id", "owner_id", "created_by", "updated_by", "created_at", "updated_at");
  private static final Set<String> RESERVED_SYSTEM_FORM_NAMES = Set.of(
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
  AppRepository appRepository;

  @Inject
  AppFormRepository appFormRepository;

  @Inject
  AppNavigationRepository appNavRecordStore;

  @Inject
  SessionContext sessionContext;

  @Inject
  AppRecordDiscussionRepository appRecordDiscussionRepository;

  @Inject
  AppRecordHistoryRepository appRecordHistoryRepository;

  @Inject
  EventBus eventBus;

  @Inject
  SystemFormService systemFormService;

  @Inject
  AppDataAuthorizationService dataAuthorization;

  /**
   * 整包导入导出使用：按 f_app_form 原始记录读取全部表单，不经过 findForms 的实体合成。
   */
  public List<AppFormRecord> rawFormRecords(String appId) {
    requireAppProject(appId);
    return formRecords(appId);
  }

  public static Set<String> reservedSystemFormNames() {
    return RESERVED_SYSTEM_FORM_NAMES;
  }

  public static Set<String> builtinFieldNames() {
    return BUILTIN_FIELDS;
  }

  /**
   * 内置字段的规范记录目录：即使表单记录未枚举内置条目，
   * 视图配置、能力目录与导出校验也能解析这些字段。
   */
  public static Map<String, AppFormFieldRecord> builtinFieldRecords() {
    Map<String, AppFormFieldRecord> fields = new LinkedHashMap<>();
    fields.put("id", new AppFormFieldRecord("id", "ID", "TEXT", false, true, null, null));
    fields.put("created_by", new AppFormFieldRecord(
      "created_by", "创建人", "REFERENCE", false, true, null, SystemFormService.USER_FORM));
    fields.put("owner_id", new AppFormFieldRecord(
      "owner_id", "所有者", "REFERENCE", false, true, null, SystemFormService.USER_FORM));
    fields.put("updated_by", new AppFormFieldRecord(
      "updated_by", "更新人", "REFERENCE", false, true, null, SystemFormService.USER_FORM));
    fields.put("created_at", new AppFormFieldRecord("created_at", "创建时间", "DATETIME", false, true, null, null));
    fields.put("updated_at", new AppFormFieldRecord("updated_at", "更新时间", "DATETIME", false, true, null, null));
    return fields;
  }

  public static Set<String> fieldTypes() {
    return FIELD_TYPES;
  }

  public static Set<String> optionFieldTypes() {
    return OPTION_TYPES;
  }

  public static Set<String> viewTypes() {
    return VIEW_TYPES;
  }

  public List<AppFormResponse> findForms(String appId) {
    if ("system".equals(appId)) {
      return systemFormService.findForms().stream()
        .map(systemFormService::toAppFormResponse)
        .toList();
    }
    Project project = requireAppProject(appId);
    Map<String, AppFormRecord> records = formRecords(appId).stream()
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
    ResolvedForm form = requireForm(appId, formName);
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
    ensureUniqueFormName(appId, formName);
    EntityDefinition entity = buildFormEntity(formName, label, request.createDisplayField());
    Map<String, AppFormFieldRecord> fieldRecords = builtinFieldRecords();
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
      List.of(allView()));
    try {
      saveFormRecord(appId, record);
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

  // ---------------------------------------------------------------- 整包导入

  /**
   * 导入期包内表单名快照：允许引用尚未建表的表单（包校验已保证引用存在）。
   */
  private static final ThreadLocal<Set<String>> IMPORT_FORM_NAMES = new ThreadLocal<>();
  /**
   * 导入期实体快照：布局子表单校验复用同一份实体，避免逐布局项查库。
   */
  private static final ThreadLocal<Map<String, EntityDefinition>> IMPORT_ENTITIES = new ThreadLocal<>();

  /**
   * 整包导入：一次建好全部表单（引用字段与主显字段元数据一并建列），最后统一写一次表单配置。
   * <p>原流程「骨架表单 → 逐字段 ALTER → 每个字段重写整份表单配置」会让 F 个表单产生 O(F²) 次写入，
   * 且引用字段必须等目标表单建好才能补列。包校验已保证引用完整，因此这里可以一次建表。</p>
   */
  public void createFormsForImport(String appId, List<AppFormRecord> records) {
    requireAppProject(appId);
    List<AppFormRecord> source = records == null ? List.of() : records;
    Set<String> names = source.stream().map(AppFormRecord::name)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    IMPORT_FORM_NAMES.set(names);
    try {
      List<ImportForm> prepared = new ArrayList<>(source.size());
      for (AppFormRecord record : source) prepared.add(prepareImportForm(appId, record));
      for (ImportForm form : prepared) modelingService.createModel(appId, form.entity());
      persistFormRecords(appId, prepared.stream().map(ImportForm::record).toList());
    } finally {
      IMPORT_FORM_NAMES.remove();
    }
  }

  /**
   * 整包导入：一次性写出全部表单的布局与视图配置（实体与现有配置各读一次）。
   */
  public void applyFormLayoutsForImport(String appId, List<AppFormRecord> records) {
    requireAppProject(appId);
    List<AppFormRecord> current = new ArrayList<>(formRecords(appId));
    Map<String, Integer> indexes = new LinkedHashMap<>();
    for (int index = 0; index < current.size(); index++) indexes.put(current.get(index).name(), index);
    Map<String, EntityDefinition> entities = new LinkedHashMap<>();
    for (EntityDefinition entity : entities(appId)) entities.put(entity.getName(), entity);
    IMPORT_ENTITIES.set(entities);
    try {
      for (AppFormRecord source : records == null ? List.<AppFormRecord>of() : records) {
        Integer index = indexes.get(source.name());
        EntityDefinition entity = entities.get(source.name());
        if (index == null || entity == null) throw new NotFoundException("表单不存在: " + source.name());
        AppFormRecord record = current.get(index);
        if (source.layout() != null && source.layout().items() != null && !source.layout().items().isEmpty()) {
          Set<String> builtinFieldNames = new HashSet<>(BUILTIN_FIELDS);
          record.fields().stream().filter(AppFormFieldRecord::builtin)
            .map(AppFormFieldRecord::name).forEach(builtinFieldNames::add);
          record = withLayout(record, normalizeLayoutItems(appId, entity,
            new AppFormLayoutRequest(source.layout().items()), builtinFieldNames));
        }
        if (source.viewTabs() != null && !source.viewTabs().isEmpty()) {
          record = withViewTabs(record, normalizeViews(record, source.viewTabs()));
        }
        current.set(index, record);
      }
      persistFormRecords(appId, List.copyOf(current));
    } finally {
      IMPORT_ENTITIES.remove();
    }
  }

  private record ImportForm(EntityDefinition entity, AppFormRecord record) {
  }

  /**
   * 按包内表单定义构建完整实体：主显字段元数据直接落到列上，引用字段与普通字段一起建列。
   */
  private ImportForm prepareImportForm(String appId, AppFormRecord source) {
    String formName = validateFormName(source.name());
    String label = validateFormLabel(source.label());
    List<AppFormFieldRecord> packageFields = source.fields() == null ? List.of() : source.fields();
    AppFormFieldRecord nameField = packageFields.stream()
      .filter(field -> field != null && !field.builtin() && NAME_FIELD.equals(field.name()))
      .findFirst().orElse(null);
    if (nameField != null && !"TEXT".equals(nameField.type())) {
      throw new ValidationException("表单 " + formName + " 的 name 字段类型必须为 TEXT");
    }
    List<AppFormFieldCreateRequest> requests = packageFields.stream()
      .filter(field -> field != null && !field.builtin() && !NAME_FIELD.equals(field.name()))
      .map(field -> new AppFormFieldCreateRequest(field.label(), field.name(), field.type(), field.required(),
        field.options(), field.referenceForm(), field.config()))
      .toList();
    List<PreparedField> preparedFields = prepareFields(appId, requests);

    EntityDefinition entity = buildFormEntity(formName, label, nameField != null);
    Map<String, AppFormFieldRecord> fieldRecords = builtinFieldRecords();
    if (nameField != null) {
      TypedField<?, ?> nameEntityField = entity.getField(NAME_FIELD);
      nameEntityField.setComment(nameField.label().trim());
      nameEntityField.setNullable(!nameField.required());
      fieldRecords.put(NAME_FIELD, new AppFormFieldRecord(NAME_FIELD, nameField.label().trim(), "TEXT",
        nameField.required(), false, null, null, AppFieldRules.validateConfig("TEXT", nameField.config())));
    }
    fieldRecords.putAll(addPreparedFields(formName, entity, preparedFields));

    String displayField = isBlank(source.displayField()) ? null : source.displayField().trim();
    if (displayField != null) {
      AppFormFieldRecord display = fieldRecords.get(displayField);
      if (display == null || display.builtin())
        throw new ValidationException("主显字段不合法: " + displayField);
    }
    AppFormRecord record = new AppFormRecord(
      formName,
      label,
      source.description() == null ? "" : source.description(),
      displayField,
      displayField,
      LocalDateTime.now(),
      LocalDateTime.now(),
      entity.getFields().stream().map(field -> toFieldRecord(field, fieldRecords.get(field.getName()))).toList(),
      new AppFormLayoutRecord(List.of()),
      List.of(allView()));
    return new ImportForm(entity, record);
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
    ResolvedForm resolved = requireForm(appId, formName);
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
    saveFormRecord(appId, updated);
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
    ResolvedForm resolved = requireForm(appId, formName);
    EntityDefinition entity = resolved.entity();
    AppFormRecord current = resolved.record() == null ? defaultRecord(project, entity) : resolved.record();
    Set<String> builtinFieldNames = new HashSet<>(BUILTIN_FIELDS);
    if (current.fields() != null) {
      current.fields().stream()
        .filter(AppFormFieldRecord::builtin)
        .map(AppFormFieldRecord::name)
        .forEach(builtinFieldNames::add);
    }
    AppFormLayoutRecord layout = normalizeLayoutItems(appId, entity, request, builtinFieldNames);
    AppFormRecord updated = withLayout(current, layout);
    saveFormRecord(appId, updated);
    return toResponse(project, entity, updated);
  }

  public AppFormResponse patchForm(String appId, String formName, AppFormPatchRequest request) {
    requireNotBuiltinForm(formName);
    if (request == null || request.viewTabs() == null) {
      throw new ValidationException("viewTabs 不能为空");
    }
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(appId, formName);
    EntityDefinition entity = resolved.entity();
    AppFormRecord current = resolved.record() == null ? defaultRecord(project, entity) : resolved.record();
    List<AppFormViewTabRecord> viewTabs = normalizeViews(current, request.viewTabs());
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
    saveFormRecord(appId, updated);
    return toResponse(project, entity, updated);
  }

  private List<AppFormViewTabRecord> normalizeViews(
    AppFormRecord current,
    List<AppFormViewTabRecord> viewTabs
  ) {
    Map<String, AppFormFieldRecord> fields = builtinFieldRecords();
    current.fields().forEach(field -> fields.put(field.name(), field));
    Set<String> ids = new LinkedHashSet<>();
    Set<String> labels = new LinkedHashSet<>();
    List<AppFormViewTabRecord> result = new ArrayList<>();
    int viewCount = 0;
    for (AppFormViewTabRecord tab : viewTabs) {
      if (tab == null) {
        throw new ValidationException("视图配置不能为空");
      }
      String id = isBlank(tab.id()) ? UUID.randomUUID().toString() : tab.id().trim();
      if (id.length() > VIEW_ID_MAX_LENGTH || !ids.add(id)) {
        throw new ValidationException("视图标识不合法或重复: " + id);
      }
      viewCount++;
      if (viewCount > MAX_CUSTOM_VIEWS) {
        throw new ValidationException("视图数量不能超过 " + MAX_CUSTOM_VIEWS);
      }
      result.add(normalizeCustomView(fields, current.name(), id, tab, labels));
    }
    return List.copyOf(result);
  }

  private AppFormViewTabRecord normalizeCustomView(
    Map<String, AppFormFieldRecord> fields,
    String formName,
    String id,
    AppFormViewTabRecord tab,
    Set<String> labels
  ) {
    if (isBlank(tab.label())) {
      throw new ValidationException("视图名称不能为空");
    }
    String label = tab.label().trim();
    if (label.length() > VIEW_LABEL_MAX_LENGTH) {
      throw new ValidationException("视图名称不能超过 " + VIEW_LABEL_MAX_LENGTH + " 个字符");
    }
    if (!labels.add(label)) {
      throw new ValidationException("视图名称重复: " + label);
    }
    String type = isBlank(tab.type()) ? "TABLE" : tab.type().trim().toUpperCase(Locale.ROOT);
    if (!VIEW_TYPES.contains(type)) {
      throw new ValidationException("视图类型不合法: " + type);
    }
    return new AppFormViewTabRecord(
      id,
      label,
      type,
      normalizeViewFilter(fields, tab.filter()),
      normalizeViewSort(fields, tab.sort()),
      normalizeDisplayFields(fields, tab.displayFields()),
      normalizeViewConfig(fields, formName, type, tab.config()));
  }

  private AppViewSort normalizeViewSort(
    Map<String, AppFormFieldRecord> fields,
    AppViewSort sort
  ) {
    if (sort == null || isBlank(sort.field())) {
      return null;
    }
    String field = sort.field().trim();
    if (!fields.containsKey(field)) {
      throw new ValidationException("视图排序字段不存在: " + field);
    }
    String order = isBlank(sort.order()) ? "ASC" : sort.order().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("ASC", "DESC").contains(order)) {
      throw new ValidationException("视图排序方向不合法: " + order);
    }
    return new AppViewSort(field, order);
  }

  private List<String> normalizeDisplayFields(
    Map<String, AppFormFieldRecord> fields,
    List<String> displayFields
  ) {
    if (displayFields == null || displayFields.isEmpty()) {
      return List.of();
    }
    if (displayFields.size() > MAX_VIEW_DISPLAY_FIELDS) {
      throw new ValidationException("视图显示字段不能超过 " + MAX_VIEW_DISPLAY_FIELDS + " 个");
    }
    Set<String> result = new LinkedHashSet<>();
    for (String value : displayFields) {
      if (isBlank(value)) {
        continue;
      }
      String fieldName = value.trim();
      if ("id".equals(fieldName) || !fields.containsKey(fieldName)) {
        throw new ValidationException("视图显示字段不存在: " + fieldName);
      }
      if (!result.add(fieldName)) {
        throw new ValidationException("视图显示字段重复: " + fieldName);
      }
    }
    return List.copyOf(result);
  }

  private AppViewConfig normalizeViewConfig(
    Map<String, AppFormFieldRecord> fields,
    String formName,
    String type,
    AppViewConfig config
  ) {
    AppViewConfig source = config == null ? AppViewConfig.empty() : config;
    return switch (type) {
      case "KANBAN" -> {
        AppFormFieldRecord groupField = requireViewField(fields, source.groupField(), "看板分组字段");
        if (!VIEW_GROUP_FIELD_TYPES.contains(groupField.type())) {
          throw new ValidationException("看板分组字段必须是单值选项、布尔或引用字段: " + groupField.label());
        }
        yield new AppViewConfig(groupField.name(), optionalViewField(fields, source.titleField(), "卡片标题字段"),
          null, null, null, null, null, null, null, null, null);
      }
      case "CALENDAR" -> {
        AppFormFieldRecord startField = requireViewField(fields, source.startField(), "日历开始字段");
        requireDateField(startField, "日历开始字段");
        AppFormFieldRecord endField = optionalViewFieldRecord(fields, source.endField(), "日历结束字段");
        if (endField != null && !endField.type().equals(startField.type())) {
          throw new ValidationException("日历起止字段必须同为日期或同为日期时间");
        }
        yield new AppViewConfig(null, optionalViewField(fields, source.titleField(), "日历标题字段"),
          null, null, startField.name(), endField == null ? null : endField.name(),
          normalizeEnum(source.calendarUnit(), Set.of("MONTH", "WEEK", "DAY"), "MONTH", "日历默认周期"),
          null, null, null, null);
      }
      case "GALLERY" -> {
        AppFormFieldRecord coverField = optionalViewFieldRecord(fields, source.coverField(), "画廊封面字段");
        if (coverField != null && !VIEW_ATTACHMENT_FIELD_TYPES.contains(coverField.type())) {
          throw new ValidationException("画廊封面字段必须是附件、图片或头像字段: " + coverField.label());
        }
        yield new AppViewConfig(null, optionalViewField(fields, source.titleField(), "画廊标题字段"),
          coverField == null ? null : coverField.name(),
          normalizeEnum(source.imageFit(), Set.of("COVER", "CONTAIN"), "COVER", "画廊图片裁切方式"),
          null, null, null, null, null, null, null);
      }
      case "DETAIL" -> new AppViewConfig(null, null, null, null, null, null, null,
        normalizeEnum(source.detailMode(), Set.of("LIST", "SINGLE"), "LIST", "详情展示模式"),
        null, null, null);
      case "HIERARCHY" -> {
        AppFormFieldRecord parentField = requireViewField(fields, source.parentField(), "层级父节点字段");
        if (!"REFERENCE".equals(parentField.type()) || !formName.equals(parentField.referenceForm())) {
          throw new ValidationException("层级父节点字段必须是关联本表单的引用字段: " + parentField.label());
        }
        yield new AppViewConfig(null, optionalViewField(fields, source.titleField(), "层级标题字段"),
          null, null, null, null, null, null, parentField.name(),
          normalizeEnum(source.hierarchyMode(), Set.of("TREE", "TABLE"), "TREE", "层级展示形态"), null);
      }
      case "GANTT" -> {
        AppFormFieldRecord startField = requireViewField(fields, source.startField(), "甘特开始字段");
        AppFormFieldRecord endField = requireViewField(fields, source.endField(), "甘特结束字段");
        requireDateField(startField, "甘特开始字段");
        requireDateField(endField, "甘特结束字段");
        if (!startField.type().equals(endField.type())) {
          throw new ValidationException("甘特起止字段必须同为日期或同为日期时间");
        }
        yield new AppViewConfig(null, optionalViewField(fields, source.titleField(), "甘特标题字段"),
          null, null, startField.name(), endField.name(), null, null, null, null,
          normalizeEnum(source.ganttScale(), Set.of("DAY", "WEEK", "MONTH"), "WEEK", "甘特默认刻度"));
      }
      default -> AppViewConfig.empty();
    };
  }

  private AppFormFieldRecord requireViewField(
    Map<String, AppFormFieldRecord> fields,
    String fieldName,
    String label
  ) {
    if (isBlank(fieldName)) {
      throw new ValidationException(label + "不能为空");
    }
    AppFormFieldRecord field = fields.get(fieldName.trim());
    if (field == null) {
      throw new ValidationException(label + "不存在: " + fieldName);
    }
    return field;
  }

  private String optionalViewField(
    Map<String, AppFormFieldRecord> fields,
    String fieldName,
    String label
  ) {
    if (isBlank(fieldName)) {
      return null;
    }
    return requireViewField(fields, fieldName, label).name();
  }

  private AppFormFieldRecord optionalViewFieldRecord(
    Map<String, AppFormFieldRecord> fields,
    String fieldName,
    String label
  ) {
    if (isBlank(fieldName)) {
      return null;
    }
    return requireViewField(fields, fieldName, label);
  }

  private void requireDateField(AppFormFieldRecord field, String label) {
    if (!VIEW_DATE_FIELD_TYPES.contains(field.type())) {
      throw new ValidationException(label + "必须是日期或日期时间字段: " + field.label());
    }
  }

  private String normalizeEnum(String value, Set<String> allowed, String fallback, String label) {
    if (isBlank(value)) {
      return fallback;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new ValidationException(label + "不合法: " + value);
    }
    return normalized;
  }

  public static AppFormViewTabRecord allView() {
    return new AppFormViewTabRecord(
      ALL_VIEW_ID, ALL_VIEW_LABEL, "TABLE", null, null, List.of(), AppViewConfig.empty());
  }

  /**
   * AI 生成视图页签前读取的能力目录：视图类型契约、按字段类型的筛选操作符、
   * 限制、当前表单字段与现有视图。目录直接由校验常量生成，与 patchForm 保持一致。
   */
  public AppViewCapabilityResponse findViewCapabilities(String appId, String formName) {
    requireNotBuiltinForm(formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(appId, formName);
    AppFormRecord current = resolved.record() == null ? defaultRecord(project, resolved.entity()) : resolved.record();
    Map<String, AppFormFieldRecord> fieldRecords = builtinFieldRecords();
    current.fields().forEach(field -> fieldRecords.put(field.name(), field));
    Map<String, List<String>> filterOperators = new LinkedHashMap<>();
    fieldRecords.values().stream()
      .map(AppFormFieldRecord::type)
      .filter(Objects::nonNull)
      .distinct()
      .forEach(type -> filterOperators.put(type, List.copyOf(viewTabOperators(type))));
    return new AppViewCapabilityResponse(
      viewTypeSpecs(),
      filterOperators,
      List.of("id", "created_at", "updated_at"),
      Map.of(
        "maxViews", MAX_CUSTOM_VIEWS,
        "maxFilterConditions", MAX_VIEW_FILTER_CONDITIONS,
        "maxDisplayFields", MAX_VIEW_DISPLAY_FIELDS,
        "labelMaxLength", VIEW_LABEL_MAX_LENGTH,
        "idMaxLength", VIEW_ID_MAX_LENGTH),
      fieldRecords.values().stream()
        .map(field -> new AppViewFieldOptionRecord(
          field.name(), field.label(), field.type(), field.builtin()))
        .toList(),
      storedViewTabs(current.viewTabs()),
      exampleViews());
  }

  private List<AppViewTypeSpecRecord> viewTypeSpecs() {
    List<AppViewTypeSpecRecord> result = new ArrayList<>();
    result.add(new AppViewTypeSpecRecord("TABLE", "表格", List.of()));
    result.add(new AppViewTypeSpecRecord("KANBAN", "看板", List.of(
      new AppViewConfigSpecRecord("groupField", true, List.copyOf(VIEW_GROUP_FIELD_TYPES), null, null,
        "分组字段，须为单值选项、布尔或引用字段"),
      new AppViewConfigSpecRecord("titleField", false, null, null, null, "卡片标题字段"))));
    result.add(new AppViewTypeSpecRecord("CALENDAR", "日历", List.of(
      new AppViewConfigSpecRecord("startField", true, List.copyOf(VIEW_DATE_FIELD_TYPES), null, null,
        "开始字段，日期或日期时间"),
      new AppViewConfigSpecRecord("endField", false, List.copyOf(VIEW_DATE_FIELD_TYPES), null, null,
        "结束字段，须与开始字段同为日期或同为日期时间"),
      new AppViewConfigSpecRecord("titleField", false, null, null, null, "事件标题字段"),
      new AppViewConfigSpecRecord("calendarUnit", false, null, List.of("MONTH", "WEEK", "DAY"), "MONTH",
        "默认周期"))));
    result.add(new AppViewTypeSpecRecord("GALLERY", "画廊", List.of(
      new AppViewConfigSpecRecord("coverField", false, List.copyOf(VIEW_ATTACHMENT_FIELD_TYPES), null, null,
        "封面字段，附件、图片或头像"),
      new AppViewConfigSpecRecord("titleField", false, null, null, null, "卡片标题字段"),
      new AppViewConfigSpecRecord("imageFit", false, null, List.of("COVER", "CONTAIN"), "COVER",
        "图片裁切方式"))));
    result.add(new AppViewTypeSpecRecord("DETAIL", "详情", List.of(
      new AppViewConfigSpecRecord("detailMode", false, null, List.of("LIST", "SINGLE"), "LIST",
        "详情展示模式"))));
    result.add(new AppViewTypeSpecRecord("HIERARCHY", "层级", List.of(
      new AppViewConfigSpecRecord("parentField", true, List.of("REFERENCE"), null, null,
        "父节点字段，须为关联本表单的引用字段"),
      new AppViewConfigSpecRecord("titleField", false, null, null, null, "节点标题字段"),
      new AppViewConfigSpecRecord("hierarchyMode", false, null, List.of("TREE", "TABLE"), "TREE",
        "层级展示形态"))));
    result.add(new AppViewTypeSpecRecord("GANTT", "甘特", List.of(
      new AppViewConfigSpecRecord("startField", true, List.copyOf(VIEW_DATE_FIELD_TYPES), null, null,
        "开始字段，日期或日期时间"),
      new AppViewConfigSpecRecord("endField", true, List.copyOf(VIEW_DATE_FIELD_TYPES), null, null,
        "结束字段，须与开始字段同为日期或同为日期时间"),
      new AppViewConfigSpecRecord("titleField", false, null, null, null, "任务标题字段"),
      new AppViewConfigSpecRecord("ganttScale", false, null, List.of("DAY", "WEEK", "MONTH"), "WEEK",
        "默认刻度"))));
    return result;
  }

  private List<Map<String, Object>> exampleViews() {
    return List.of(
      Map.of("id", "all", "label", "全部", "type", "TABLE",
        "sort", Map.of("field", "created_at", "order", "DESC")),
      Map.of("label", "高优先级", "type", "TABLE",
        "filter", Map.of("_and", List.of(Map.of("priority", Map.of("_eq", "high"))))),
      Map.of("label", "状态看板", "type", "KANBAN",
        "config", Map.of("groupField", "status", "titleField", "name")));
  }

  public Map<String, Object> normalizeQueryFilter(AppFormResponse form, String json) {
    if (json == null || json.isBlank()) return null;
    if (json.length() > 16000) throw new ValidationException("高级查询条件过长");
    Map<String, Object> filter;
    try {
      filter = JsonUtils.parseToMap(json);
    } catch (RuntimeException caught) {
      throw new ValidationException("高级查询条件必须为 JSON 对象");
    }
    if (filter == null || filter.size() != 1) throw new ValidationException("高级查询必须包含全部或任一条件组");
    String logic = filter.keySet().iterator().next();
    if (!Set.of("_and", "_or").contains(logic) || !(filter.get(logic) instanceof List<?>)) {
      throw new ValidationException("高级查询条件组不合法");
    }
    Map<String, AppFormFieldRecord> fields = new LinkedHashMap<>();
    for (AppFormFieldResponse field : form.fields()) {
      if (!field.builtin()) fields.put(field.name(), new AppFormFieldRecord(
        field.name(), field.label(), field.type(), field.required(), false, field.options(), field.referenceForm()));
    }
    Map<String, Object> normalized = normalizeViewFilter(fields, Map.of("_and", filter.get(logic)));
    return Map.of(logic, normalized.get("_and"));
  }

  private Map<String, Object> normalizeViewFilter(
    Map<String, AppFormFieldRecord> fields,
    Map<String, Object> filter
  ) {
    if (filter == null) {
      return null;
    }
    if (filter.size() != 1) {
      throw new ValidationException("视图筛选条件必须包含全部或任一条件组");
    }
    String logic = filter.keySet().iterator().next();
    if (!Set.of("_and", "_or").contains(logic) || !(filter.get(logic) instanceof List<?> conditions)) {
      throw new ValidationException("视图筛选条件必须为 _and 或 _or 结构");
    }
    if (conditions.isEmpty() || conditions.size() > MAX_VIEW_FILTER_CONDITIONS) {
      throw new ValidationException("视图筛选条件数量必须在 1 到 " + MAX_VIEW_FILTER_CONDITIONS + " 之间");
    }
    List<Object> normalizedConditions = new ArrayList<>();
    for (Object conditionValue : conditions) {
      if (!(conditionValue instanceof Map<?, ?> condition) || condition.size() != 1) {
        throw new ValidationException("视图每个筛选条件只能包含一个字段");
      }
      String fieldName = String.valueOf(condition.keySet().iterator().next());
      if ("id".equals(fieldName)) {
        throw new ValidationException("视图筛选条件不支持 id 字段");
      }
      AppFormFieldRecord field = fields.get(fieldName);
      if (field == null) {
        throw new ValidationException("视图筛选字段不存在: " + fieldName);
      }
      if (!(condition.get(fieldName) instanceof Map<?, ?> operatorMap) || operatorMap.size() != 1) {
        throw new ValidationException("视图筛选字段只能配置一个操作符: " + field.label());
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
    return Map.of(logic, normalizedConditions);
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

  public void deleteForm(String appId, String formName) {
    requireNotBuiltinForm(formName);
    appFlowLifecycle.beforeDeleteForm(appId, formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    if (isBoundToNavigation(project, formName)) {
      throw new ValidationException("表单已被菜单绑定，请先解除菜单关联");
    }
    if (isFormUsedAsSubform(appId, formName)) {
      throw new ValidationException("表单已被子表单引用，请先移除相关子表单布局");
    }
    modelingService.dropModel(appId, entityName);
    try {
      appRecordDiscussionRepository.deleteByForm(appId, formName);
    } catch (RuntimeException error) {
      log.warn("Failed to clean form discussions for {}/{}: {}", appId, formName, error.getMessage());
    }
    try {
      appRecordHistoryRepository.deleteByForm(appId, formName);
    } catch (RuntimeException error) {
      log.warn("Failed to clean form histories for {}/{}: {}", appId, formName, error.getMessage());
    }
    List<AppFormRecord> records = formRecords(appId).stream()
      .filter(record -> !record.name().equals(entityName))
      .toList();
    persistFormRecords(appId, records);
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
    ResolvedForm resolved = requireForm(appId, formName);
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
      AppFormRecord current = findFormRecord(appId, entityName).orElseGet(() -> defaultRecord(project, entity));
      List<AppFormFieldRecord> fields = new ArrayList<>(current.fields());
      fields.add(fieldRecord);
      AppFormLayoutRecord layout = current.layout() == null
        ? new AppFormLayoutRecord(List.of())
        : current.layout();
      saveFormRecord(appId, withLayout(withFields(current, fields), layout));
    } catch (RuntimeException caught) {
      modelingService.dropField(appId, entityName, fieldName);
      throw caught;
    }
    return toFieldResponse(appId, field, fieldRecord);
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
    ResolvedForm resolved = requireForm(appId, formName);
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
    if (isFieldUsedAsSubform(appId, formName, fieldName)
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
    AppFormRecord current = findFormRecord(appId, entityName).orElseGet(() -> defaultRecord(project, entity));
    List<AppFormFieldRecord> fields = current.fields().stream()
      .map(item -> item.name().equals(fieldName) ? updatedRecord : item)
      .collect(Collectors.toCollection(ArrayList::new));
    if (fields.stream().noneMatch(item -> item.name().equals(fieldName))) {
      fields.add(updatedRecord);
    }
    saveFormRecord(appId, withFields(current, fields));
    return toFieldResponse(appId, field, updatedRecord);
  }

  public void deleteField(String appId, String formName, String fieldName) {
    appFlowLifecycle.beforeDeleteField(appId, formName, fieldName);
    requireNotBuiltinForm(formName);
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    requireField(entity, fieldName);
    if (BUILTIN_FIELDS.contains(fieldName)) {
      throw new ValidationException("内置字段不允许删除");
    }
    if (isFieldUsedAsSubform(appId, formName, fieldName)) {
      throw new ValidationException("字段已被子表单引用，请先移除相关子表单布局");
    }
    if (resolved.record() != null) {
      List<String> viewLabels = viewsReferencingField(resolved.record().viewTabs(), fieldName);
      if (!viewLabels.isEmpty()) {
        throw new ValidationException("字段已被视图引用，请先调整视图配置: " + String.join("、", viewLabels));
      }
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
      saveFormRecord(appId, new AppFormRecord(
        current.name(),
        current.label(),
        current.description(),
        displayField,
        searchField,
        current.createdAt(),
        LocalDateTime.now(),
        fields,
        withoutLayoutField(current.layout(), fieldName),
        current.viewTabs()));
    });
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  private ResolvedForm requireForm(String appId, String formName) {
    if (RESERVED_SYSTEM_FORM_NAMES.contains(formName)) {
      throw new NotFoundException("表单不存在: " + formName);
    }
    AppFormRecord record = formRecords(appId).stream()
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
      .filter(model -> !model.getName().equals("f_runtime_message"))
      .filter(EntityDefinition.class::isInstance)
      .map(EntityDefinition.class::cast)
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
    return appNavRecordStore.requireAppProject(appId);
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

  private TypedField<?, ?> buildField(
    String entityName,
    String fieldName,
    String type,
    String label,
    boolean required
  ) {
    TypedField<?, ?> field = switch (type) {
      // 大文本/附件字段使用 Text，避免 MySQL 下多个 varchar(4000) 触发 65535 行长度限制
      case "NTEXT", "FILE", "IMAGE", "AVATAR" -> new TextField(fieldName);
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
    Set<String> importNames = IMPORT_FORM_NAMES.get();
    if (importNames != null) {
      // 导入期以包内表单名快照为准：目标表单可能尚未建表（互相引用），存在性已由包校验保证。
      if (!importNames.contains(normalizedReferenceForm)
        && !BUILTIN_SYSTEM_FORM_NAMES.contains(normalizedReferenceForm)) {
        throw new ValidationException("引用表单不存在: " + referenceForm);
      }
      return normalizedReferenceForm;
    }
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
    List<AppFormViewTabRecord> viewTabs = storedViewTabs(record == null ? null : record.viewTabs());
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
        .map(field -> toFieldResponse(project.getId(), field, findFieldRecord(record, field.getName()).orElse(null)))
        .toList(),
      layout,
      viewTabs);
  }

  /**
   * 读取时补齐历史配置缺失的通用字段，视图本身按存储顺序原样返回。
   */
  private List<AppFormViewTabRecord> storedViewTabs(List<AppFormViewTabRecord> viewTabs) {
    if (viewTabs == null || viewTabs.isEmpty()) {
      return List.of();
    }
    List<AppFormViewTabRecord> result = new ArrayList<>();
    for (AppFormViewTabRecord tab : viewTabs) {
      if (tab == null) {
        continue;
      }
      result.add(new AppFormViewTabRecord(
        tab.id(),
        tab.label(),
        isBlank(tab.type()) ? "TABLE" : tab.type(),
        tab.filter(),
        tab.sort(),
        tab.displayFields() == null ? List.of() : tab.displayFields(),
        tab.config() == null ? AppViewConfig.empty() : tab.config()));
    }
    return List.copyOf(result);
  }

  private AppFormFieldResponse toFieldResponse(
    String appId,
    TypedField<?, ?> field,
    AppFormFieldRecord record
  ) {
    String type = record == null ? inferFieldType(field) : record.type();
    String referenceForm = record != null && !isBlank(record.referenceForm())
      ? record.referenceForm()
      : referenceForm(appId, field);
    return new AppFormFieldResponse(
      field.getName(),
      record == null ? blankToDefault(field.getComment(), field.getName()) : record.label(),
      type,
      record == null ? !field.isNullable() : record.required(),
      BUILTIN_FIELDS.contains(field.getName()) || (record != null && record.builtin()),
      record == null ? null : record.options(),
      referenceForm, false, false, record == null ? null : record.config());
  }

  private String referenceForm(String appId, TypedField<?, ?> field) {
    if (!(field instanceof ModelRefField refField)) {
      return null;
    }
    String targetEntityName = refField.getFrom();
    return formRecords(appId).stream()
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
    if (field instanceof TextField) return "NTEXT";
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
      List.of(allView()));
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

  private java.util.Optional<AppFormRecord> findFormRecord(String appId, String formName) {
    return formRecords(appId).stream()
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

  private void saveFormRecord(String appId, AppFormRecord replacement) {
    List<AppFormRecord> records = formRecords(appId).stream()
      .filter(record -> !record.name().equals(replacement.name()))
      .collect(Collectors.toCollection(ArrayList::new));
    records.add(replacement);
    persistFormRecords(appId, records);
  }

  private void persistFormRecords(String appId, List<AppFormRecord> records) {
    Map<String, Set<String>> children = new LinkedHashMap<>();
    for (AppFormRecord record : records)
      children.put(record.name(), layoutItems(record.layout()).stream()
        .filter(item -> "SUBFORM".equals(item.type())).map(AppFormItemRecord::formName).collect(Collectors.toSet()));
    dataAuthorization.reconcile(appId, records.stream().map(AppFormRecord::name).collect(Collectors.toSet()), children);
    appFormRepository.deleteByAppId(appId);
    for (AppFormRecord record : records) {
      appFormRepository.save(toAppFormRow(appId, record));
    }
    App app = appRepository.findApp(appId);
    if (app == null) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    app.setUpdatedBy(currentUserId());
    app.setUpdatedAt(LocalDateTime.now());
    appRepository.save(app);
  }

  private AppForm toAppFormRow(String appId, AppFormRecord record) {
    AppForm row = new AppForm();
    row.setId(UUID.randomUUID().toString());
    row.setAppId(appId);
    row.setName(record.name());
    row.setContent(JsonUtils.convertValue(record, Map.class));
    return row;
  }

  private AppFormRecord toAppFormRecord(AppForm row) {
    if (row.getContent() == null) {
      return new AppFormRecord(
        row.getName(), row.getName(), "",
        null, null, null, null, List.of(), new AppFormLayoutRecord(List.of()), List.of());
    }
    return JsonUtils.convertValue(row.getContent(), AppFormRecord.class);
  }

  private AppFormLayoutRecord normalizeLayoutItems(
    String appId,
    EntityDefinition entity,
    AppFormLayoutRequest request,
    Set<String> builtinFieldNames
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
        appId, fieldNames, item, usedFieldNames, subformKeys))
      .toList();
    if (items.stream().noneMatch(item -> "FIELD".equals(item.type()))) {
      throw new ValidationException("表单布局必须包含字段");
    }
    return new AppFormLayoutRecord(items, normalizeControls(request.controls(), items, builtinFieldNames));
  }

  private Map<String, AppFormFieldControls> normalizeControls(
    Map<String, AppFormFieldControls> controls,
    List<AppFormItemRecord> items,
    Set<String> builtinFieldNames
  ) {
    if (controls == null || controls.isEmpty()) {
      return null;
    }
    if (controls.size() > MAX_LAYOUT_ITEMS) {
      throw new ValidationException("字段控制数量不能超过 " + MAX_LAYOUT_ITEMS);
    }
    Set<String> fieldNames = items.stream()
      .filter(item -> "FIELD".equals(item.type()))
      .map(AppFormItemRecord::name)
      .collect(Collectors.toSet());
    Map<String, AppFormFieldControls> result = new LinkedHashMap<>();
    for (Map.Entry<String, AppFormFieldControls> entry : controls.entrySet()) {
      String fieldName = entry.getKey();
      if (!fieldNames.contains(fieldName)) {
        throw new ValidationException("字段控制对应的字段不在布局中: " + fieldName);
      }
      // 内置字段始终展示且只读，不接受控制配置。
      if (builtinFieldNames.contains(fieldName)) {
        continue;
      }
      AppFormFieldControls value = entry.getValue();
      if (value == null) {
        continue;
      }
      AppFormFieldControls normalized = new AppFormFieldControls(
        normalizeControl(value.hidden(), "隐藏"),
        normalizeControl(value.required(), "必填"),
        normalizeControl(value.readonly(), "只读"));
      if (normalized.hidden() == null && normalized.required() == null && normalized.readonly() == null) {
        continue;
      }
      result.put(fieldName, normalized);
    }
    return result.isEmpty() ? null : result;
  }

  private AppFormFieldControlRecord normalizeControl(AppFormFieldControlRecord control, String label) {
    if (control == null) {
      return null;
    }
    if (control.scenes() == null || control.scenes().isEmpty()) {
      throw new ValidationException(label + "控制至少选择一个场景");
    }
    Set<String> scenes = new LinkedHashSet<>();
    for (String scene : control.scenes()) {
      String normalized = scene == null ? "" : scene.trim().toUpperCase(Locale.ROOT);
      if (!FORM_CONTROL_SCENES.contains(normalized)) {
        throw new ValidationException(label + "控制场景不合法: " + scene);
      }
      if (!scenes.add(normalized)) {
        throw new ValidationException(label + "控制场景重复: " + scene);
      }
    }
    if (control.condition() != null && control.condition().isEmpty()) {
      throw new ValidationException(label + "控制条件不能为空对象");
    }
    return new AppFormFieldControlRecord(List.copyOf(scenes), control.condition());
  }

  private AppFormItemRecord normalizeLayoutItem(
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
          null,
          null,
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
        validateSubformItem(appId, formName, referenceField);
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
    String appId,
    String formName,
    String referenceField
  ) {
    Map<String, EntityDefinition> snapshot = IMPORT_ENTITIES.get();
    EntityDefinition snapshotEntity = snapshot == null ? null : snapshot.get(formName);
    ResolvedForm child;
    try {
      child = snapshotEntity != null ? new ResolvedForm(snapshotEntity, null) : requireForm(appId, formName);
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

  private boolean isFormUsedAsSubform(String appId, String formName) {
    return formRecords(appId).stream()
      .flatMap(record -> layoutItems(record.layout()).stream())
      .anyMatch(item -> "SUBFORM".equals(item.type()) && formName.equals(item.formName()));
  }

  private boolean isFieldUsedAsSubform(String appId, String formName, String fieldName) {
    return formRecords(appId).stream()
      .flatMap(record -> layoutItems(record.layout()).stream())
      .anyMatch(item -> "SUBFORM".equals(item.type())
        && formName.equals(item.formName())
        && fieldName.equals(item.referenceField()));
  }

  private List<String> viewsReferencingField(List<AppFormViewTabRecord> viewTabs, String fieldName) {
    if (viewTabs == null || viewTabs.isEmpty()) {
      return List.of();
    }
    return viewTabs.stream()
      .filter(Objects::nonNull)
      .filter(tab -> viewReferencesField(tab, fieldName))
      .map(AppFormViewTabRecord::label)
      .toList();
  }

  private boolean viewReferencesField(AppFormViewTabRecord tab, String fieldName) {
    if (tab.sort() != null && fieldName.equals(tab.sort().field())) {
      return true;
    }
    if (tab.displayFields() != null && tab.displayFields().contains(fieldName)) {
      return true;
    }
    AppViewConfig config = tab.config();
    if (config != null && (fieldName.equals(config.groupField())
      || fieldName.equals(config.titleField())
      || fieldName.equals(config.coverField())
      || fieldName.equals(config.startField())
      || fieldName.equals(config.endField())
      || fieldName.equals(config.parentField()))) {
      return true;
    }
    return filterReferencesField(tab.filter(), fieldName);
  }

  private boolean filterReferencesField(Object filter, String fieldName) {
    if (!(filter instanceof Map<?, ?> map)) {
      return false;
    }
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      if (key.startsWith("_")) {
        if (entry.getValue() instanceof List<?> conditions
          && conditions.stream().anyMatch(condition -> filterReferencesField(condition, fieldName))) {
          return true;
        }
      } else if (fieldName.equals(key)) {
        return true;
      }
    }
    return false;
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

  private AppFormRecord withViewTabs(AppFormRecord record, List<AppFormViewTabRecord> viewTabs) {
    return new AppFormRecord(
      record.name(),
      record.label(),
      record.description(),
      record.displayField(),
      record.searchField(),
      record.createdAt(),
      LocalDateTime.now(),
      record.fields(),
      record.layout(),
      viewTabs);
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
    Map<String, AppFormFieldControls> controls = layout == null ? null : layout.controls();
    if (controls != null && controls.containsKey(fieldName)) {
      Map<String, AppFormFieldControls> remaining = new LinkedHashMap<>(controls);
      remaining.remove(fieldName);
      controls = remaining.isEmpty() ? null : remaining;
    }
    return new AppFormLayoutRecord(layoutItems(layout).stream()
      .filter(item -> !"FIELD".equals(item.type()) || !fieldName.equals(item.name()))
      .toList(), controls);
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

  private List<AppFormRecord> formRecords(String appId) {
    return appFormRepository.findByAppId(appId).stream()
      .map(this::toAppFormRecord)
      .toList();
  }

  private boolean isBoundToNavigation(Project project, String formName) {
    App app = appRepository.findApp(project.getId());
    Object value = app == null ? null : app.getNavigation();
    if (!(value instanceof List<?> list)) {
      return false;
    }
    AppNavRecord[] navs = JsonUtils.convertValue(list, AppNavRecord[].class);
    for (AppNavRecord nav : navs) {
      if (containsFormDestination(nav, formName)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsFormDestination(AppNavRecord nav, String formName) {
    if (nav == null) {
      return false;
    }
    NavDestinationRecord destination = nav.destination();
    if (destination != null && "FORM".equals(destination.type()) && formName.equals(destination.id())) {
      return true;
    }
    return nav.children() != null && nav.children().stream().anyMatch(child -> containsFormDestination(child, formName));
  }

  private void ensureUniqueFormName(String appId, String formName) {
    if (RESERVED_SYSTEM_FORM_NAMES.contains(formName)) {
      throw new ValidationException("琛ㄥ崟鍐呯儴鏍囪瘑宸插瓨鍦? " + formName);
    }
    boolean metadataExists = formRecords(appId).stream()
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
      if (!FIELD_NAME_PATTERN.matcher(fieldName).matches()) {
        throw new ValidationException("字段标识不能包含引号、反引号、方括号或控制字符");
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
