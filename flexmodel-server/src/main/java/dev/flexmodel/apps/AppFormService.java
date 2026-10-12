package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormCreateRequest;
import dev.flexmodel.apps.dto.AppFormItemPropertiesRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRequest;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldOptionResponse;
import dev.flexmodel.apps.dto.AppFormFieldRecord;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormFieldUpdateRequest;
import dev.flexmodel.apps.dto.AppFormSubformRecord;
import dev.flexmodel.apps.dto.AppFormRecord;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppFormUpdateRequest;
import dev.flexmodel.apps.dto.AppMenuRecord;
import dev.flexmodel.apps.dto.AppMenuTargetRecord;
import dev.flexmodel.codegen.entity.Project;
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
import dev.flexmodel.project.ProjectService;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
public class AppFormService {

  private static final String METADATA_APP_KEY = "app";
  private static final String METADATA_FORMS_KEY = "forms";
  private static final String METADATA_FORM_LAYOUTS_KEY = "formLayouts";
  private static final String FORM_DIVIDER_PREFIX = "$DIVIDER$-";
  private static final String FORM_SUBFORM_PREFIX = "$SUBFORM$-";
  private static final Set<Integer> FIELD_WIDTHS = Set.of(25, 33, 50, 66, 75, 100);
  private static final String NAME_FIELD = "name";
  private static final Set<String> BUILTIN_FIELDS = Set.of(
    "id", "created_by", "updated_by", "created_at", "updated_at");
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
  ProjectService projectService;

  @Inject
  SessionContext sessionContext;

  @Inject
  EventBus eventBus;

  public List<AppFormResponse> findForms(String appId) {
    Project project = requireAppProject(appId);
    Map<String, AppFormRecord> records = formRecords(project).stream()
      .collect(Collectors.toMap(AppFormRecord::entityName, Function.identity(), (left, right) -> right,
        LinkedHashMap::new));
    return entities(appId).stream()
      .map(entity -> toResponse(project, entity, records.get(entity.getName())))
      .toList();
  }

  public AppFormResponse findForm(String appId, String formName) {
    Project project = requireAppProject(appId);
    ResolvedForm form = requireForm(project, appId, formName);
    return toResponse(project, form.entity(), form.record());
  }

  public String resolveEntityName(String appId, String formName) {
    Project project = requireAppProject(appId);
    return requireForm(project, appId, formName).entity().getName();
  }

  public AppFormResponse createForm(String appId, AppFormCreateRequest request) {
    Project project = requireAppProject(appId);
    String formName = request.name().trim();
    ensureUniqueFormName(project, appId, formName);
    String entityName = generateEntityName(appId);
    EntityDefinition entity = new EntityDefinition(entityName);
    entity.setComment(request.label().trim());
    systemFields().forEach(entity::addField);
    if (request.createNameField()) {
      entity.addField(new StringField(NAME_FIELD)
        .setLength(255)
        .setComment("名称")
        .setNullable(false));
      entity.addIndex(new IndexDefinition(entityName, "idx_" + entityName + "_name")
        .addField(NAME_FIELD));
    }

    modelingService.createModel(appId, entity);
    AppFormRecord record = new AppFormRecord(
      entityName,
      formName,
      request.label().trim(),
      request.description() == null ? "" : request.description(),
      request.createNameField() ? NAME_FIELD : null,
      request.createNameField() ? NAME_FIELD : null,
      LocalDateTime.now(),
      LocalDateTime.now(),
      entity.getFields().stream().map(field -> toFieldRecord(field, null)).toList());
    try {
      saveFormRecord(project, record);
    } catch (RuntimeException caught) {
      modelingService.dropModel(appId, entityName);
      throw caught;
    }
    return toResponse(project, entity, record);
  }

  public AppFormResponse updateForm(String appId, String formName, AppFormUpdateRequest request) {
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
      current.entityName(),
      current.name(),
      request.label().trim(),
      request.description() == null ? current.description() : request.description(),
      displayField,
      searchField,
      current.createdAt(),
      LocalDateTime.now(),
      current.fields());
    saveFormRecord(project, updated);
    return toResponse(project, entity, updated);
  }

  public AppFormResponse saveFormLayout(
    String appId,
    String formName,
    AppFormLayoutRequest request
  ) {
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    String canonicalFormName = resolved.record() == null ? formName(entity) : resolved.record().name();
    AppFormLayoutRecord layout = normalizeLayout(project, appId, canonicalFormName, entity, request);
    Map<String, AppFormLayoutRecord> layouts = formLayouts(project);
    layouts.put(canonicalFormName, layout);
    persistFormLayouts(project, layouts);
    return toResponse(project, entity, resolved.record());
  }

  public void deleteForm(String appId, String formName) {
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
    String canonicalFormName = resolved.record() == null ? formName(entity) : resolved.record().name();
    modelingService.dropModel(appId, entityName);
    List<AppFormRecord> records = formRecords(project).stream()
      .filter(record -> !record.entityName().equals(entityName))
      .toList();
    persistFormRecords(project, records);
    Map<String, AppFormLayoutRecord> layouts = formLayouts(project);
    layouts.remove(canonicalFormName);
    persistFormLayouts(project, layouts);
  }

  public AppFormFieldResponse createField(
    String appId,
    String formName,
    AppFormFieldCreateRequest request
  ) {
    validateFieldType(request.type());
    Project project = requireAppProject(appId);
    ResolvedForm resolved = requireForm(project, appId, formName);
    EntityDefinition entity = resolved.entity();
    String entityName = entity.getName();
    String fieldName = generateFieldName(entity);
    List<AppFormFieldOptionResponse> options = normalizeOptions(request.type(), request.options());
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
      referenceForm);
    try {
      AppFormRecord current = findFormRecord(project, entityName).orElseGet(() -> defaultRecord(project, entity));
      List<AppFormFieldRecord> fields = new ArrayList<>(current.fields());
      fields.add(fieldRecord);
      saveFormRecord(project, withFields(current, fields));
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
    List<AppFormFieldOptionResponse> options = normalizeOptions(currentType, request.options());
    String referenceForm = normalizeReferenceForm(appId, currentType, request.referenceForm());
    field.setComment(request.label().trim());
    field.setNullable(!request.required());
    modelingService.modifyField(appId, field);

    AppFormFieldRecord updatedRecord = new AppFormFieldRecord(
      fieldName,
      request.label().trim(),
      currentType,
      request.required(),
      false,
      options,
      referenceForm);
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
        current.entityName(),
        current.name(),
        current.label(),
        current.description(),
        displayField,
        searchField,
        current.createdAt(),
        LocalDateTime.now(),
        fields));
    });
    String canonicalFormName = resolved.record() == null ? formName(entity) : resolved.record().name();
    Map<String, AppFormLayoutRecord> layouts = formLayouts(project);
    AppFormLayoutRecord layout = layouts.get(canonicalFormName);
    if (layout != null) {
      layouts.put(canonicalFormName, removeLayoutField(layout, fieldName));
      persistFormLayouts(project, layouts);
    }
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  private ResolvedForm requireForm(Project project, String appId, String formName) {
    AppFormRecord record = formRecords(project).stream()
      .filter(item -> item.name().equals(formName))
      .findFirst()
      .orElse(null);
    if (record != null) {
      return new ResolvedForm(requireEntity(appId, record.entityName()), record);
    }
    EntityDefinition entity = entities(appId).stream()
      .filter(item -> formName(item).equals(formName))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("表单不存在: " + formName));
    return new ResolvedForm(entity, null);
  }

  private String formName(EntityDefinition entity) {
    return entity.getName();
  }

  private record ResolvedForm(EntityDefinition entity, AppFormRecord record) {
  }

  private List<EntityDefinition> entities(String appId) {
    return modelingService.findModels(appId).stream()
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
      case "NTEXT" -> new StringField(fieldName).setLength(4000);
      case "PHONE", "EMAIL", "URL", "TAG", "FILE", "IMAGE", "AVATAR", "BARCODE", "LOCATION", "SIGN", "STATE" ->
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
    if (options == null || options.isEmpty()) {
      return List.of(
        new AppFormFieldOptionResponse("1", "选项一", null, false),
        new AppFormFieldOptionResponse("2", "选项二", null, false));
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    return options.stream()
      .map(option -> {
        if (option == null || isBlank(option.id()) || isBlank(option.text())) {
          throw new ValidationException("字段选项的ID和展示文本不能为空");
        }
        if (!ids.add(option.id())) {
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
    AppFormLayoutRecord layout = formLayouts(project).get(formName);
    return new AppFormResponse(
      formName,
      label,
      description,
      displayField,
      searchField,
      record != null,
      record == null ? project.getCreatedAt() : record.createdAt(),
      record == null ? project.getUpdatedAt() : record.updatedAt(),
      entity.getFields().stream()
        .map(field -> toFieldResponse(project, field, findFieldRecord(record, field.getName()).orElse(null)))
        .toList(),
      layout);
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
      referenceForm);
  }

  private String referenceForm(Project project, TypedField<?, ?> field) {
    if (!(field instanceof ModelRefField refField)) {
      return null;
    }
    String targetEntityName = refField.getFrom();
    return formRecords(project).stream()
      .filter(record -> record.entityName().equals(targetEntityName))
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
      record == null ? null : record.referenceForm());
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
      formName(entity),
      blankToDefault(entity.getComment(), entity.getName()),
      "",
      displayField,
      displayField,
      project.getCreatedAt(),
      project.getUpdatedAt(),
      entity.getFields().stream().map(field -> toFieldRecord(field, null)).toList());
  }

  private String defaultDisplayField(EntityDefinition entity) {
    return entity.getField(NAME_FIELD) == null ? null : NAME_FIELD;
  }

  private AppFormRecord withFields(AppFormRecord record, List<AppFormFieldRecord> fields) {
    return new AppFormRecord(
      record.entityName(),
      record.name(),
      record.label(),
      record.description(),
      record.displayField(),
      record.searchField(),
      record.createdAt(),
      LocalDateTime.now(),
      fields);
  }

  private java.util.Optional<AppFormRecord> findFormRecord(Project project, String entityName) {
    return formRecords(project).stream()
      .filter(record -> record.entityName().equals(entityName))
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
      .filter(record -> !record.entityName().equals(replacement.entityName()))
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
    metadata.put(METADATA_APP_KEY, app);
    project.setMetadata(metadata);
    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());
    projectService.updateProject(project);
  }

  private AppFormLayoutRecord normalizeLayout(
    Project project,
    String appId,
    String formName,
    EntityDefinition entity,
    AppFormLayoutRequest request
  ) {
    if (request.fields() == null) {
      throw new ValidationException("表单布局字段不能为空");
    }
    Set<String> fieldNames = entity.getFields().stream()
      .map(TypedField::getName)
      .collect(Collectors.toSet());
    LinkedHashSet<String> orderedFields = new LinkedHashSet<>();
    for (String fieldName : request.fields()) {
      if (isBlank(fieldName)) {
        throw new ValidationException("表单布局字段不能为空");
      }
      if (!fieldNames.contains(fieldName)
        && !fieldName.startsWith(FORM_DIVIDER_PREFIX)
        && !fieldName.startsWith(FORM_SUBFORM_PREFIX)) {
        throw new ValidationException("表单布局字段不存在: " + fieldName);
      }
      if (fieldName.startsWith(FORM_SUBFORM_PREFIX)
        && (request.subforms() == null || !request.subforms().containsKey(fieldName))) {
        throw new ValidationException("子表单布局配置不存在: " + fieldName);
      }
      if (!orderedFields.add(fieldName)) {
        throw new ValidationException("表单布局字段重复: " + fieldName);
      }
    }
    Map<String, AppFormSubformRecord> subforms = normalizeSubforms(
      project, appId, formName, orderedFields, request.subforms());
    return new AppFormLayoutRecord(
      List.copyOf(orderedFields),
      normalizeWidths(orderedFields, request.widths()),
      normalizeProperties(orderedFields, request.properties()),
      subforms,
      request.advanced() == null ? Map.of() : request.advanced());
  }

  private Map<String, AppFormSubformRecord> normalizeSubforms(
    Project project,
    String appId,
    String mainFormName,
    Set<String> fieldNames,
    Map<String, AppFormSubformRecord> subforms
  ) {
    if (subforms == null || subforms.isEmpty()) {
      return Map.of();
    }
    Map<String, AppFormSubformRecord> result = new LinkedHashMap<>();
    subforms.forEach((id, subform) -> {
      if (!fieldNames.contains(id) || !id.startsWith(FORM_SUBFORM_PREFIX)) {
        throw new ValidationException("子表单布局配置不存在: " + id);
      }
      if (subform == null || isBlank(subform.formName()) || isBlank(subform.referenceField())) {
        throw new ValidationException("子表单配置不完整: " + id);
      }
      requireSubform(project, appId, mainFormName, subform);
      result.put(id, new AppFormSubformRecord(
        subform.formName().trim(),
        subform.referenceField().trim()));
    });
    return result;
  }

  private void requireSubform(
    Project project,
    String appId,
    String mainFormName,
    AppFormSubformRecord subform
  ) {
    ResolvedForm child = requireForm(project, appId, subform.formName().trim());
    AppFormFieldRecord field = child.record() == null
      ? null
      : child.record().fields().stream()
      .filter(item -> item.name().equals(subform.referenceField().trim()))
      .findFirst()
      .orElse(null);
    if (field == null) {
      throw new ValidationException("子表单引用字段不合法: " + subform.referenceField());
    }
  }

  private boolean isFormUsedAsSubform(Project project, String formName) {
    return formLayouts(project).values().stream()
      .flatMap(layout -> layout.subforms() == null ? java.util.stream.Stream.empty() : layout.subforms().values().stream())
      .anyMatch(subform -> subform != null && formName.equals(subform.formName()));
  }

  private boolean isFieldUsedAsSubform(Project project, String formName, String fieldName) {
    return formLayouts(project).values().stream()
      .flatMap(layout -> layout.subforms() == null ? java.util.stream.Stream.empty() : layout.subforms().values().stream())
      .anyMatch(subform -> subform != null
        && formName.equals(subform.formName())
        && fieldName.equals(subform.referenceField()));
  }

  private AppFormLayoutRecord removeLayoutField(AppFormLayoutRecord layout, String fieldName) {
    List<String> fields = layout.fields() == null
      ? List.of()
      : layout.fields().stream().filter(name -> !name.equals(fieldName)).toList();
    Map<String, Integer> widths = new LinkedHashMap<>(layout.widths() == null ? Map.of() : layout.widths());
    widths.remove(fieldName);
    Map<String, AppFormItemPropertiesRecord> properties = new LinkedHashMap<>(
      layout.properties() == null ? Map.of() : layout.properties());
    properties.remove(fieldName);
    Map<String, AppFormSubformRecord> subforms = new LinkedHashMap<>(
      layout.subforms() == null ? Map.of() : layout.subforms());
    subforms.keySet().removeIf(fields::contains);
    return new AppFormLayoutRecord(fields, widths, properties, subforms, layout.advanced());
  }

  private Map<String, Integer> normalizeWidths(Set<String> fieldNames, Map<String, Integer> widths) {
    if (widths == null) {
      return Map.of();
    }
    Map<String, Integer> result = new LinkedHashMap<>();
    widths.forEach((fieldName, width) -> {
      if (!fieldNames.contains(fieldName)) {
        throw new ValidationException("字段宽度配置不存在: " + fieldName);
      }
      if (width == null || !FIELD_WIDTHS.contains(width)) {
        throw new ValidationException("字段宽度不合法: " + fieldName);
      }
      result.put(fieldName, width);
    });
    return result;
  }

  private Map<String, AppFormItemPropertiesRecord> normalizeProperties(
    Set<String> fieldNames,
    Map<String, AppFormItemPropertiesRecord> properties
  ) {
    if (properties == null) {
      return Map.of();
    }
    Map<String, AppFormItemPropertiesRecord> result = new LinkedHashMap<>();
    properties.forEach((fieldName, property) -> {
      if (!fieldNames.contains(fieldName)) {
        throw new ValidationException("字段属性配置不存在: " + fieldName);
      }
      if (property == null) {
        throw new ValidationException("字段属性不能为空: " + fieldName);
      }
      result.put(fieldName, new AppFormItemPropertiesRecord(
        normalizeLayoutText(property.label()),
        normalizeLayoutText(property.tip()),
        property.required(),
        property.readonly(),
        property.hidden()));
    });
    return result;
  }

  private String normalizeLayoutText(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private Map<String, AppFormLayoutRecord> formLayouts(Project project) {
    Object value = appNode(project).get(METADATA_FORM_LAYOUTS_KEY);
    if (!(value instanceof Map<?, ?> layouts)) {
      return new LinkedHashMap<>();
    }
    Map<String, AppFormLayoutRecord> result = new LinkedHashMap<>();
    layouts.forEach((key, layout) -> {
      if (layout != null) {
        result.put(String.valueOf(key), JsonUtils.convertValue(layout, AppFormLayoutRecord.class));
      }
    });
    return result;
  }

  private void persistFormLayouts(Project project, Map<String, AppFormLayoutRecord> layouts) {
    Map<String, Object> metadata = metadataMap(project);
    Map<String, Object> app = appNode(project);
    Map<String, Object> converted = new LinkedHashMap<>();
    layouts.forEach((formName, layout) -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> value = JsonUtils.convertValue(layout, Map.class);
      converted.put(formName, value);
    });
    app.put(METADATA_FORM_LAYOUTS_KEY, converted);
    metadata.put(METADATA_APP_KEY, app);
    project.setMetadata(metadata);
    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());
    projectService.updateProject(project);
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

  private String generateEntityName(String appId) {
    Set<String> existing = entities(appId).stream()
      .map(SchemaObject::getName)
      .collect(Collectors.toSet());
    String candidate;
    do {
      candidate = "Form_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        .toLowerCase(Locale.ROOT);
    } while (existing.contains(candidate));
    return candidate;
  }

  private void ensureUniqueFormName(Project project, String appId, String formName) {
    boolean metadataExists = formRecords(project).stream()
      .anyMatch(record -> record.name().equals(formName));
    boolean modelExists = entities(appId).stream()
      .map(this::formName)
      .anyMatch(formName::equals);
    if (metadataExists || modelExists) {
      throw new ValidationException("表单内部标识已存在: " + formName);
    }
  }

  private String generateFieldName(EntityDefinition entity) {
    Set<String> existing = entity.getFields().stream()
      .map(TypedField::getName)
      .collect(Collectors.toSet());
    String candidate;
    do {
      candidate = "field_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        .toLowerCase(Locale.ROOT);
    } while (existing.contains(candidate) || BUILTIN_FIELDS.contains(candidate) || NAME_FIELD.equals(candidate));
    return candidate;
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
