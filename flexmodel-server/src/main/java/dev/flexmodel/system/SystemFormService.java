package dev.flexmodel.system;

import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormFieldUpdateRequest;
import dev.flexmodel.apps.dto.AppFormFieldOptionResponse;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.auth.dto.ResourceTreeResponse;
import dev.flexmodel.auth.service.ResourceService;
import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.config.EngineConfig;
import dev.flexmodel.graphql.dto.GraphQLRefreshEvent;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.BooleanField;
import dev.flexmodel.model.field.DateField;
import dev.flexmodel.model.field.DateTimeField;
import dev.flexmodel.model.field.FloatField;
import dev.flexmodel.model.field.IntField;
import dev.flexmodel.model.field.JSONField;
import dev.flexmodel.model.field.StringField;
import dev.flexmodel.model.field.TimeField;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.modeling.ModelService;
import dev.flexmodel.system.dto.SystemComponentRecord;
import dev.flexmodel.system.dto.SystemFormColumnRecord;
import dev.flexmodel.system.dto.SystemFormFieldRecord;
import dev.flexmodel.system.dto.SystemFormItemRecord;
import dev.flexmodel.system.dto.SystemFormLayoutRecord;
import dev.flexmodel.system.dto.SystemFormMetadataRecord;
import dev.flexmodel.system.dto.SystemFormPageRecord;
import dev.flexmodel.system.dto.SystemFormResponse;
import dev.flexmodel.system.dto.SystemFormSaveRequest;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 系统表单服务。表单配置直接保存在系统模型 metadata 中。
 */
@ApplicationScoped
public class SystemFormService {

  // 与 User/Department/Role 的 @ModelClass 模型名一致；switch 常量表达式要求编译期常量，故保留字面量
  public static final String USER_FORM = "f_user";
  public static final String DEPARTMENT_FORM = "f_department";
  public static final String ROLE_FORM = "f_role";
  public static final Set<String> SYSTEM_FORM_NAMES = Set.of(
    USER_FORM, DEPARTMENT_FORM, ROLE_FORM);
  private static final List<String> ORDERED_SYSTEM_FORM_NAMES = List.of(
    USER_FORM, DEPARTMENT_FORM, ROLE_FORM);

  private static final String SYSTEM_SCHEMA = EngineConfig.SYSTEM_DS_KEY;
  private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
  private static final Set<String> AUDIT_FIELDS = Set.of(
    "id", "created_by", "updated_by", "created_at", "updated_at");
  private static final Set<String> ITEM_TYPES = Set.of("FIELD", "SECTION", "COMPONENT", "SUBFORM");
  private static final Set<String> WIDTHS = Set.of(
    "QUARTER", "THIRD", "HALF", "TWO_THIRDS", "THREE_QUARTERS", "FULL");
  private static final Set<String> FIELD_TYPES = Set.of(
    "TEXT", "NTEXT", "PHONE", "EMAIL", "URL", "TAG", "SELECT", "NUMBER",
    "DECIMAL", "DATE", "DATETIME", "TIME", "BOOL", "MULTISELECT", "REFERENCE",
    "MULTIREFERENCE");
  private static final Map<String, Set<String>> CORE_FIELDS = Map.of(
    USER_FORM, Set.of("name", "email", "avatar", "password", "status", "manager_user_id", "roleIds"),
    DEPARTMENT_FORM, Set.of("name", "parent_id"),
    ROLE_FORM, Set.of("name", "description", "resourceIds"));
  private static final Map<String, Set<String>> ACTUAL_CORE_FIELDS = Map.of(
    USER_FORM, Set.of("name", "email", "avatar", "password_hash", "status", "manager_user_id"),
    DEPARTMENT_FORM, Set.of("name", "parent_id"),
    ROLE_FORM, Set.of("name", "description"));
  private static final Map<String, Set<String>> COMPONENT_TYPES = Map.of(
    USER_FORM, Set.of("USER_DEPARTMENTS", "USER_ROLES"),
    DEPARTMENT_FORM, Set.of(),
    ROLE_FORM, Set.of("ROLE_PERMISSIONS"));
  private static final Map<String, Set<String>> COMPONENT_NAMES = Map.of(
    USER_FORM, Set.of("departments", "roles"),
    DEPARTMENT_FORM, Set.of(),
    ROLE_FORM, Set.of("resources"));
  private static final Map<String, List<SystemFormItemRecord>> SUBFORMS = Map.of(
    USER_FORM, List.of(new SystemFormItemRecord(
      "SUBFORM", "departments", "任职", null, "FULL", false, false,
      ReflectionUtils.getModelNameFromClass(UserDepartment.class), "user_id")));

  @Inject
  ModelService modelService;

  @Inject
  ResourceService resourceService;

  @Inject
  EventBus eventBus;

  public List<SystemFormResponse> findForms() {
    return ORDERED_SYSTEM_FORM_NAMES.stream().map(this::findForm).toList();
  }

  public SystemFormResponse findForm(String formName) {
    EntityDefinition entity = requireEntity(formName);
    SystemFormMetadataRecord metadata = metadata(entity);
    return toResponse(entity, metadata, resources(formName));
  }

  public SystemFormResponse saveForm(String formName, SystemFormSaveRequest request) {
    EntityDefinition entity = requireEntity(formName);
    SystemFormMetadataRecord current = metadata(entity);
    validateRequest(formName, entity, request);

    syncExtensionFields(formName, entity, request.fields());

    EntityDefinition updated = requireEntity(formName).clone();

    SystemFormMetadataRecord saved = new SystemFormMetadataRecord(
      normalizeText(request.label()),
      normalizeText(request.description()),
      normalizeText(request.displayField()),
      normalizeText(request.searchField()),
      current.createdAt() == null ? LocalDateTime.now() : current.createdAt(),
      LocalDateTime.now(),
      normalizeFields(formName, entity, request.fields()),
      normalizeLayout(formName, request.layout()),
      normalizeColumns(request.listColumns()),
      normalizePage(request.page()),
      normalizeComponents(formName, request.components()),
      true);
    updated.addAdditionalProperty("metadata", JsonUtils.convertValue(saved, Map.class));
    modelService.saveModel(SYSTEM_SCHEMA, SYSTEM_SCHEMA, updated);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return findForm(formName);
  }

  public SystemFormResponse saveLayout(String formName, SystemFormLayoutRecord layout) {
    EntityDefinition entity = requireEntity(formName);
    SystemFormMetadataRecord current = metadata(entity);
    SystemFormMetadataRecord saved = new SystemFormMetadataRecord(
      current.label(),
      current.description(),
      current.displayField(),
      current.searchField(),
      current.createdAt() == null ? LocalDateTime.now() : current.createdAt(),
      LocalDateTime.now(),
      current.fields(),
      normalizeLayout(formName, layout),
      current.listColumns(),
      current.page(),
      current.components(),
      current.designable());
    EntityDefinition updated = entity.clone();
    updated.addAdditionalProperty("metadata", JsonUtils.convertValue(saved, Map.class));
    modelService.saveModel(SYSTEM_SCHEMA, SYSTEM_SCHEMA, updated);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return findForm(formName);
  }

  public AppFormFieldResponse createField(
    String formName,
    AppFormFieldCreateRequest request
  ) {
    SystemFormResponse current = findForm(formName);
    String fieldName = resolveFieldName(current, request.name(), request.label());
    SystemFormFieldRecord field = new SystemFormFieldRecord(
      fieldName,
      normalizeText(request.label()),
      request.type(),
      request.required(),
      false,
      true,
      false,
      false,
      request.options(),
      request.referenceForm());
    List<SystemFormFieldRecord> fields = new ArrayList<>(current.fields());
    fields.add(field);
    SystemFormResponse saved = saveForm(formName, withFields(current, fields));
    return fieldResponse(saved, fieldName);
  }

  public AppFormFieldResponse updateField(
    String formName,
    String fieldName,
    AppFormFieldUpdateRequest request
  ) {
    SystemFormResponse current = findForm(formName);
    SystemFormFieldRecord existing = requireExtensionField(current, fieldName);
    SystemFormFieldRecord updated = new SystemFormFieldRecord(
      existing.name(),
      normalizeText(request.label()),
      existing.type(),
      request.required(),
      existing.locked(),
      existing.extension(),
      existing.virtual(),
      existing.readonly(),
      request.options(),
      request.referenceForm());
    List<SystemFormFieldRecord> fields = current.fields().stream()
      .map(item -> item.name().equals(existing.name()) ? updated : item)
      .toList();
    SystemFormResponse saved = saveForm(formName, withFields(current, fields));
    return fieldResponse(saved, existing.name());
  }

  public void deleteField(String formName, String fieldName) {
    SystemFormResponse current = findForm(formName);
    SystemFormFieldRecord existing = requireExtensionField(current, fieldName);
    List<SystemFormFieldRecord> fields = current.fields().stream()
      .filter(item -> !item.name().equals(existing.name()))
      .toList();
    SystemFormSaveRequest request = new SystemFormSaveRequest(
      current.label(),
      current.description(),
      normalizeRemovedDisplayField(current, existing.name()),
      normalizeRemovedSearchField(current, existing.name()),
      fields,
      withoutLayoutField(current.layout(), existing.name()),
      withoutColumn(current.listColumns(), existing.name()),
      current.page(),
      current.components());
    saveForm(formName, request);
  }

  private SystemFormSaveRequest withFields(
    SystemFormResponse form,
    List<SystemFormFieldRecord> fields
  ) {
    return new SystemFormSaveRequest(
      form.label(),
      form.description(),
      form.displayField(),
      form.searchField(),
      fields,
      form.layout(),
      form.listColumns(),
      form.page(),
      form.components());
  }

  private AppFormFieldResponse fieldResponse(SystemFormResponse form, String fieldName) {
    return form.fields().stream()
      .filter(item -> item.name().equalsIgnoreCase(fieldName))
      .findFirst()
      .map(item -> new AppFormFieldResponse(
        item.name(),
        item.label(),
        item.type(),
        item.required(),
        !item.extension(),
        item.options(),
        item.referenceForm(),
        item.locked(),
        item.extension()))
      .orElseThrow(() -> new NotFoundException("字段不存在: " + fieldName));
  }

  private SystemFormFieldRecord requireExtensionField(SystemFormResponse form, String fieldName) {
    SystemFormFieldRecord field = form.fields().stream()
      .filter(item -> item.name().equalsIgnoreCase(fieldName))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("字段不存在: " + fieldName));
    if (!field.extension()) {
      throw new ValidationException("核心字段不允许修改");
    }
    return field;
  }

  private String resolveFieldName(SystemFormResponse form, String requestedName, String label) {
    if (!isBlank(requestedName)) {
      String fieldName = requestedName.trim();
      if (!FIELD_NAME_PATTERN.matcher(fieldName).matches()) {
        throw new ValidationException("字段标识只能以小写字母开头，并可包含小写字母、数字或下划线");
      }
      if (!isExtensionFieldNameAvailable(form, fieldName)) {
        throw new ValidationException("字段标识已存在: " + fieldName);
      }
      return fieldName;
    }
    String candidate = friendlyFieldNameFromLabel(label);
    if (candidate != null && isExtensionFieldNameAvailable(form, candidate)) {
      return candidate;
    }
    String generatedName;
    do {
      generatedName = "field_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        .toLowerCase(Locale.ROOT);
    } while (!isExtensionFieldNameAvailable(form, generatedName));
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

  private boolean isExtensionFieldNameAvailable(SystemFormResponse form, String fieldName) {
    return !AUDIT_FIELDS.contains(fieldName)
      && CORE_FIELDS.get(form.name()).stream().noneMatch(name -> name.equalsIgnoreCase(fieldName))
      && form.fields().stream().noneMatch(item -> item.name().equalsIgnoreCase(fieldName));
  }

  private String normalizeRemovedDisplayField(SystemFormResponse form, String fieldName) {
    return form.displayField() != null && form.displayField().equalsIgnoreCase(fieldName)
      ? "name"
      : form.displayField();
  }

  private String normalizeRemovedSearchField(SystemFormResponse form, String fieldName) {
    return form.searchField() != null && form.searchField().equalsIgnoreCase(fieldName)
      ? "name"
      : form.searchField();
  }

  private SystemFormLayoutRecord withoutLayoutField(SystemFormLayoutRecord layout, String fieldName) {
    return new SystemFormLayoutRecord(layout.items().stream()
      .filter(item -> !("FIELD".equals(item.type()) && item.name() != null
        && item.name().equalsIgnoreCase(fieldName)))
      .toList());
  }

  private List<SystemFormColumnRecord> withoutColumn(
    List<SystemFormColumnRecord> columns,
    String fieldName
  ) {
    return columns.stream()
      .filter(item -> item.name() == null || !item.name().equalsIgnoreCase(fieldName))
      .toList();
  }

  public AppFormResponse toAppFormResponse(SystemFormResponse form) {
    return new AppFormResponse(
      form.name(),
      form.label(),
      form.description(),
      form.displayField(),
      form.searchField(),
      true,
      true,
      form.createdAt(),
      form.updatedAt(),
      form.fields().stream().map(field -> new AppFormFieldResponse(
        field.name(),
        field.label(),
        field.type(),
        field.required(),
        !field.extension(),
        field.options(),
        field.referenceForm(),
        field.locked(),
        field.extension())).toList(),
      new AppFormLayoutRecord(form.layout().items().stream().map(item -> new AppFormItemRecord(
        item.type(),
        item.name(),
        item.label(),
        item.tip(),
        item.width(),
        item.hidden(),
        item.readonly(),
        item.formName(),
        item.referenceField())).toList()),
      List.of());
  }

  private EntityDefinition requireEntity(String formName) {
    if (!SYSTEM_FORM_NAMES.contains(formName)) {
      throw new NotFoundException("系统表单不存在: " + formName);
    }
    SchemaObject model = modelService.findModel(SYSTEM_SCHEMA, SYSTEM_SCHEMA, formName)
      .orElseThrow(() -> new NotFoundException("系统表单不存在: " + formName));
    if (!(model instanceof EntityDefinition entity)) {
      throw new NotFoundException("系统表单不存在: " + formName);
    }
    return entity;
  }

  @SuppressWarnings("unchecked")
  private SystemFormMetadataRecord metadata(EntityDefinition entity) {
    Object value = entity.getAdditionalProperties().get("metadata");
    if (value instanceof Map<?, ?> metadataMap
      && metadataMap.size() == 1
      && metadataMap.containsKey("value")) {
      value = metadataMap.get("value");
    }
    if (value == null) {
      return defaultMetadata(entity);
    }
    if (value instanceof SystemFormMetadataRecord metadata) {
      return metadata;
    }
    return JsonUtils.convertValue(value, SystemFormMetadataRecord.class);
  }

  private SystemFormMetadataRecord defaultMetadata(EntityDefinition entity) {
    String formName = entity.getName();
    return new SystemFormMetadataRecord(
      switch (formName) {
        case USER_FORM -> "用户";
        case DEPARTMENT_FORM -> "部门";
        case ROLE_FORM -> "角色";
        default -> throw new ValidationException("系统表单不合法: " + formName);
      },
      switch (formName) {
        case USER_FORM -> "系统用户表单";
        case DEPARTMENT_FORM -> "系统部门表单";
        case ROLE_FORM -> "系统角色表单";
        default -> throw new ValidationException("系统表单不合法: " + formName);
      },
      "name",
      "name",
      null,
      null,
      CORE_FIELDS.get(formName).stream().map(name -> defaultCoreField(formName, name)).toList(),
      new SystemFormLayoutRecord(List.of()),
      List.of(),
      new SystemFormPageRecord(null, null, 30, null),
      Map.of(),
      true);
  }

  private SystemFormResponse toResponse(
    EntityDefinition entity,
    SystemFormMetadataRecord metadata,
    List<ResourceTreeResponse> resources
  ) {
    return new SystemFormResponse(
      entity.getName(),
      normalizeText(metadata.label()),
      normalizeText(metadata.description()),
      normalizeText(metadata.displayField()),
      normalizeText(metadata.searchField()),
      metadata.createdAt(),
      metadata.updatedAt(),
      responseFields(entity, metadata),
      metadata.layout() == null ? new SystemFormLayoutRecord(List.of()) : metadata.layout(),
      metadata.listColumns() == null ? List.of() : metadata.listColumns(),
      metadata.page(),
      metadata.components() == null ? Map.of() : metadata.components(),
      metadata.designable(),
      resources);
  }

  private List<SystemFormFieldRecord> responseFields(
    EntityDefinition entity,
    SystemFormMetadataRecord metadata
  ) {
    String formName = entity.getName();
    Set<String> actualCore = ACTUAL_CORE_FIELDS.get(formName);
    Map<String, SystemFormFieldRecord> configured = (metadata.fields() == null ? List.<SystemFormFieldRecord>of()
      : metadata.fields()).stream()
      .collect(Collectors.toMap(SystemFormFieldRecord::name, field -> field, (left, right) -> right,
        LinkedHashMap::new));

    List<SystemFormFieldRecord> result = new ArrayList<>();
    CORE_FIELDS.get(formName).forEach(name -> {
      SystemFormFieldRecord field = configured.get(name);
      if (field == null) {
        field = defaultCoreField(formName, name);
      }
      result.add(new SystemFormFieldRecord(
        name,
        normalizeText(field.label()),
        field.type(),
        field.required(),
        true,
        false,
        !actualCore.contains(name),
        false,
        field.options(),
        field.referenceForm()));
    });

    entity.getFields().stream()
      .filter(field -> !AUDIT_FIELDS.contains(field.getName()))
      .filter(field -> !actualCore.contains(field.getName()))
      .forEach(field -> {
        SystemFormFieldRecord configuredField = configured.get(field.getName());
        result.add(new SystemFormFieldRecord(
          field.getName(),
          configuredField == null
            ? normalizeText(field.getComment(), field.getName())
            : normalizeText(configuredField.label()),
          configuredField == null ? inferFieldType(field) : configuredField.type(),
          configuredField != null && configuredField.required(),
          false,
          true,
          false,
          false,
          configuredField == null ? null : configuredField.options(),
          configuredField == null ? null : configuredField.referenceForm()));
      });
    return result;
  }

  private SystemFormFieldRecord defaultCoreField(String formName, String name) {
    return switch (formName) {
      case USER_FORM -> switch (name) {
        case "name" -> new SystemFormFieldRecord(name, "姓名", "TEXT", true, true, false, false, false, null, null);
        case "email" -> new SystemFormFieldRecord(name, "邮箱", "EMAIL", false, true, false, false, false, null, null);
        case "avatar" -> new SystemFormFieldRecord(name, "头像", "URL", false, true, false, false, false, null, null);
        case "password" -> new SystemFormFieldRecord(name, "密码", "TEXT", false, true, false, true, false, null, null);
        case "roleIds" ->
          new SystemFormFieldRecord(name, "角色", "MULTIREFERENCE", false, true, false, true, false, null, ROLE_FORM);
        case "status" -> new SystemFormFieldRecord(name, "状态", "SELECT", true, true, false, false, false,
          List.of(
            new AppFormFieldOptionResponse("ACTIVE", "启用", null, false),
            new AppFormFieldOptionResponse("DISABLED", "停用", null, false),
            new AppFormFieldOptionResponse("INVITED", "待激活", null, false)),
          null);
        case "manager_user_id" ->
          new SystemFormFieldRecord(name, "直属主管", "TEXT", false, true, false, false, false, null, null);
        default -> throw new ValidationException("核心字段不合法: " + name);
      };
      case DEPARTMENT_FORM -> switch (name) {
        case "name" -> new SystemFormFieldRecord(name, "部门名称", "TEXT", true, true, false, false, false, null, null);
        case "parent_id" ->
          new SystemFormFieldRecord(name, "上级部门", "TEXT", false, true, false, false, false, null, null);
        default -> throw new ValidationException("核心字段不合法: " + name);
      };
      case ROLE_FORM -> switch (name) {
        case "name" -> new SystemFormFieldRecord(name, "角色名称", "TEXT", true, true, false, false, false, null, null);
        case "description" ->
          new SystemFormFieldRecord(name, "角色说明", "TEXT", false, true, false, false, false, null, null);
        case "resourceIds" ->
          new SystemFormFieldRecord(name, "可访问权限", "MULTIREFERENCE", false, true, false, true, false, null, null);
        default -> throw new ValidationException("核心字段不合法: " + name);
      };
      default -> throw new ValidationException("系统表单不合法: " + formName);
    };
  }

  private void validateRequest(
    String formName,
    EntityDefinition entity,
    SystemFormSaveRequest request
  ) {
    if (request == null || isBlank(request.label())) {
      throw new ValidationException("表单名称不能为空");
    }
    if (request.fields() == null) {
      throw new ValidationException("表单字段不能为空");
    }
    Set<String> requestedNames = new LinkedHashSet<>();
    for (SystemFormFieldRecord field : request.fields()) {
      if (field == null || isBlank(field.name())) {
        throw new ValidationException("字段标识不能为空");
      }
      String name = field.name().trim().toLowerCase(Locale.ROOT);
      if (!FIELD_NAME_PATTERN.matcher(name).matches()) {
        throw new ValidationException("字段标识只能以小写字母开头，并可包含小写字母、数字或下划线");
      }
      if (!requestedNames.add(name)) {
        throw new ValidationException("字段标识重复: " + name);
      }
      if (!FIELD_TYPES.contains(field.type())) {
        throw new ValidationException("字段类型不合法: " + field.type());
      }
      if (isBlank(field.label())) {
        throw new ValidationException("字段展示名不能为空: " + name);
      }
    }
    CORE_FIELDS.get(formName).forEach(name -> {
      if (!requestedNames.stream().anyMatch(requestedName -> requestedName.equalsIgnoreCase(name))) {
        throw new ValidationException("核心字段不允许删除: " + name);
      }
    });
    request.fields().stream()
      .filter(field -> CORE_FIELDS.get(formName).stream()
        .anyMatch(name -> name.equalsIgnoreCase(field.name())))
      .forEach(field -> {
        if (field.extension()) {
          throw new ValidationException("核心字段不能标记为扩展字段: " + field.name());
        }
      });
    if (isBlank(request.displayField())
      || requestedNames.stream().noneMatch(name -> name.equalsIgnoreCase(request.displayField().trim()))) {
      throw new ValidationException("主显字段必须是表单字段");
    }
  }

  private void syncExtensionFields(
    String formName,
    EntityDefinition entity,
    List<SystemFormFieldRecord> requestedFields
  ) {
    Set<String> actualCore = ACTUAL_CORE_FIELDS.get(formName);
    Set<String> requestedExtensions = requestedFields.stream()
      .filter(SystemFormFieldRecord::extension)
      .map(SystemFormFieldRecord::name)
      .collect(Collectors.toSet());

    entity.getFields().stream()
      .filter(field -> !AUDIT_FIELDS.contains(field.getName()))
      .filter(field -> !actualCore.contains(field.getName()))
      .map(TypedField::getName)
      .filter(name -> !requestedExtensions.contains(name))
      .toList()
      .forEach(name -> modelService.dropField(SYSTEM_SCHEMA, SYSTEM_SCHEMA, formName, name));

    requestedFields.stream()
      .filter(SystemFormFieldRecord::extension)
      .forEach(field -> {
        TypedField<?, ?> existing = requireEntity(formName).getField(field.name());
        TypedField<?, ?> replacement = buildField(formName, field.name(), field.type(), field.label(),
          field.required());
        if (existing == null) {
          modelService.createField(SYSTEM_SCHEMA, SYSTEM_SCHEMA, replacement);
        } else if (existing.getClass() != replacement.getClass()
          || existing.isNullable() != replacement.isNullable()) {
          modelService.modifyField(SYSTEM_SCHEMA, SYSTEM_SCHEMA, replacement);
        }
      });
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
      case "PHONE", "EMAIL", "URL", "TAG" -> new StringField(fieldName).setLength(512);
      case "SELECT" -> new StringField(fieldName).setLength(255);
      case "TEXT" -> new StringField(fieldName).setLength(255);
      case "NUMBER" -> new IntField(fieldName);
      case "DECIMAL" -> new FloatField(fieldName);
      case "DATE" -> new DateField(fieldName);
      case "DATETIME" -> new DateTimeField(fieldName);
      case "TIME" -> new TimeField(fieldName);
      case "BOOL" -> new BooleanField(fieldName);
      case "MULTISELECT" -> new JSONField(fieldName);
      default -> throw new ValidationException("字段类型不合法: " + type);
    };
    field.setModelName(entityName);
    field.setComment(label);
    field.setNullable(!required);
    return field;
  }

  private List<SystemFormFieldRecord> normalizeFields(
    String formName,
    EntityDefinition entity,
    List<SystemFormFieldRecord> fields
  ) {
    Set<String> actualCore = ACTUAL_CORE_FIELDS.get(formName);
    return fields.stream()
      .map(field -> {
        String trimmedName = field.name().trim();
        String normalizedName = CORE_FIELDS.get(formName).stream()
          .filter(coreName -> coreName.equalsIgnoreCase(trimmedName))
          .findFirst()
          .orElseGet(() -> trimmedName.toLowerCase(Locale.ROOT));
        boolean coreField = CORE_FIELDS.get(formName).stream()
          .anyMatch(coreName -> coreName.equalsIgnoreCase(trimmedName));
        boolean actualCoreField = actualCore.stream()
          .anyMatch(actualName -> actualName.equalsIgnoreCase(trimmedName));
        return new SystemFormFieldRecord(
          normalizedName,
          normalizeText(field.label()),
          field.type(),
          field.required(),
          coreField,
          !coreField && !actualCoreField,
          !actualCoreField && coreField,
          false,
          field.options(),
          field.referenceForm());
      })
      .toList();
  }

  private SystemFormLayoutRecord normalizeLayout(String formName, SystemFormLayoutRecord layout) {
    if (layout == null || layout.items() == null) {
      return new SystemFormLayoutRecord(List.of());
    }
    Set<String> fieldNames = new LinkedHashSet<>(CORE_FIELDS.get(formName));
    requireEntity(formName).getFields().stream()
      .map(TypedField::getName)
      .filter(name -> !AUDIT_FIELDS.contains(name))
      .forEach(fieldNames::add);
    Set<String> componentNames = COMPONENT_NAMES.get(formName);
    Set<String> subformKeys = new LinkedHashSet<>();
    List<SystemFormItemRecord> items = layout.items().stream()
      .map(item -> {
        if (item == null || !ITEM_TYPES.contains(item.type())) {
          throw new ValidationException("布局节点类型不合法");
        }
        if (!isBlank(item.width()) && !WIDTHS.contains(item.width())) {
          throw new ValidationException("布局宽度不合法: " + item.width());
        }
        if ("FIELD".equals(item.type())
          && (isBlank(item.name()) || !fieldNames.contains(item.name()))) {
          throw new ValidationException("布局字段不存在: " + item.name());
        }
        if ("COMPONENT".equals(item.type())
          && (isBlank(item.name()) || !componentNames.contains(item.name()))) {
          throw new ValidationException("布局组件不存在: " + item.name());
        }
        if ("SUBFORM".equals(item.type())) {
          List<SystemFormItemRecord> allowed = SUBFORMS.getOrDefault(formName, List.of());
          if (allowed.stream().noneMatch(candidate ->
            Objects.equals(candidate.formName(), item.formName())
              && Objects.equals(candidate.referenceField(), item.referenceField()))) {
            throw new ValidationException("内置表单子表单配置不合法: " + item.formName());
          }
        }
        return new SystemFormItemRecord(
          item.type(),
          item.name(),
          normalizeText(item.label()),
          normalizeText(item.tip()),
          item.width(),
          item.hidden(),
          item.readonly(),
          item.formName(),
          item.referenceField());
      })
      .toList();
    return new SystemFormLayoutRecord(items);
  }

  private List<SystemFormColumnRecord> normalizeColumns(List<SystemFormColumnRecord> columns) {
    if (columns == null) {
      return List.of();
    }
    return columns.stream()
      .map(column -> {
        if (column == null || isBlank(column.name()) || isBlank(column.label())) {
          throw new ValidationException("列表列名称和展示名不能为空");
        }
        return new SystemFormColumnRecord(
          column.name().trim(),
          normalizeText(column.label()),
          column.width(),
          Boolean.TRUE.equals(column.sortable()),
          Boolean.TRUE.equals(column.hidden()));
      })
      .toList();
  }

  private SystemFormPageRecord normalizePage(SystemFormPageRecord page) {
    if (page == null) {
      return new SystemFormPageRecord(null, null, 30, null);
    }
    Integer size = page.pageSize() == null ? 30 : Math.clamp(page.pageSize(), 10, 200);
    return new SystemFormPageRecord(
      normalizeText(page.title()),
      normalizeText(page.description()),
      size,
      normalizeText(page.leftComponent()));
  }

  private Map<String, SystemComponentRecord> normalizeComponents(
    String formName,
    Map<String, SystemComponentRecord> components
  ) {
    if (components == null) {
      return Map.of();
    }
    Map<String, SystemComponentRecord> result = new LinkedHashMap<>();
    components.forEach((name, component) -> {
      if (isBlank(name) || component == null || !COMPONENT_TYPES.get(formName).contains(component.type())) {
        throw new ValidationException("系统表单组件不合法: " + name);
      }
      result.put(name.trim(), new SystemComponentRecord(
        component.type(),
        normalizeText(component.label()),
        Boolean.TRUE.equals(component.hidden())));
    });
    return result;
  }

  private List<ResourceTreeResponse> resources(String formName) {
    return ROLE_FORM.equals(formName) ? resourceService.findResourceTree() : List.of();
  }

  private String inferFieldType(TypedField<?, ?> field) {
    if (field instanceof JSONField) return "MULTISELECT";
    if (field instanceof IntField) return "NUMBER";
    if (field instanceof FloatField) return "DECIMAL";
    if (field instanceof BooleanField) return "BOOL";
    if (field instanceof DateField) return "DATE";
    if (field instanceof DateTimeField) return "DATETIME";
    if (field instanceof TimeField) return "TIME";
    return "TEXT";
  }

  private String normalizeText(String value) {
    return value == null ? null : value.trim();
  }

  private String normalizeText(String value, String defaultValue) {
    return isBlank(value) ? defaultValue : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
