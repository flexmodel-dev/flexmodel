package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.approval.AppApprovalCapabilities;
import dev.flexmodel.apps.approval.AppApprovalFlowModel;
import dev.flexmodel.apps.approval.AppApprovalFlowService;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.dto.AppResponse;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.navigation.AppNavigationService;
import dev.flexmodel.apps.navigation.dto.AppNavSaveRequest;
import dev.flexmodel.apps.navigation.dto.NavDestinationRequest;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.apps.workbench.AppWorkbenchDataService;
import dev.flexmodel.apps.workbench.AppWorkbenchService;
import dev.flexmodel.apps.workbench.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.workbench.dto.WorkbenchLayoutRecord;
import dev.flexmodel.apps.workbench.dto.WorkbenchPatchRequest;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Flexmodel Apps 工具集：应用、表单、布局、视图、工作台、导航、审批流程与记录能力。
 * <p>
 * 使用 langchain4j {@link Tool} 注解定义，同时服务两条通道：
 * 页面 AI 助手（function calling）与 Quarkus MCP Server
 * （经 quarkus.mcp.server.support-langchain4j-annotations 自动注册）。
 * 访问控制由 {@link McpToolAccess} 统一处理：MCP 走 API Key 服务身份，
 * 页面 AI 走登录用户，写操作要求应用所有者或管理员。
 */
@Slf4j
@ApplicationScoped
public class AppTools {

  private static final int MAX_FIELDS = 40;
  private static final int MAX_RECORDS = 20;
  private static final int MAX_VALUE_LENGTH = 300;
  private static final int MAX_RESULT_LENGTH = 12_000;

  @Inject
  AppService appService;

  @Inject
  AppFormService appFormService;

  @Inject
  AppNavigationService appNavigationService;

  @Inject
  AppRecordService appRecordService;

  @Inject
  AppWorkbenchService appWorkbenchService;

  @Inject
  AppWorkbenchDataService appWorkbenchDataService;

  @Inject
  AppApprovalFlowService appApprovalFlowService;

  @Inject
  AppApprovalCapabilities appApprovalCapabilities;

  @Inject
  McpToolAccess access;

  @Tool(name = "list_apps", value = """
    List all Flexmodel Apps. Each app is an isolated top-level project \
    with forms, navigation, and records. Use this tool before creating an app \
    to check whether the desired appId already exists.\
    """)
  public String list_apps() {
    log.info("list_apps called");
    try {
      access.requireDirectory();
      return JsonUtils.toJsonString(appService.findApps());
    } catch (Exception exception) {
      log.error("list_apps failed", exception);
      return "Error: list_apps failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app", value = "Get one Flexmodel App by its appId.")
  public String get_app(
    @P("The app ID, also used as the project ID") String appId
  ) {
    log.info("get_app called, appId={}", appId);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appService.findApp(appId));
    } catch (Exception exception) {
      log.error("get_app failed, appId={}", appId, exception);
      return "Error: get_app failed - " + exception.getMessage();
    }
  }

  @Tool(name = "list_app_forms", value = """
    List all forms of a Flexmodel App. Use this tool to confirm form names \
    and generated field names before saving a layout or creating records.\
    The list includes the built-in forms f_user, f_role, and f_department.\
    """)
  public String list_app_forms(
    @P("The app ID") String appId
  ) {
    log.info("list_app_forms called, appId={}", appId);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appFormService.findForms(appId));
    } catch (Exception exception) {
      log.error("list_app_forms failed, appId={}", appId, exception);
      return "Error: list_app_forms failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app_form", value = """
    Get one form of a Flexmodel App by formName. The response contains \
    generated field names, the current flat layout.items, layout.controls \
    when field controls are configured, and the current viewTabs.\
    """)
  public String get_app_form(
    @P("The app ID") String appId,
    @P("The form name returned by create_app_form or list_app_forms") String formName
  ) {
    log.info("get_app_form called, appId={}, formName={}", appId, formName);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appFormService.findForm(appId, formName));
    } catch (Exception exception) {
      log.error("get_app_form failed, appId={}, formName={}", appId, formName, exception);
      return "Error: get_app_form failed - " + exception.getMessage();
    }
  }

  @Tool(name = "create_app", value = """
    Create a Flexmodel App. This is step 1 of the app creation flow. \
    iconName is a Lucide icon name in PascalCase, for example ShoppingCart, Package, \
    or Rocket. \
    primaryColor is a lower-case color slug such as blue, indigo, green, amber, rose, \
    or black; blue is the default.\
    """)
  public String create_app(
    @P("Human-readable app name, required") String name,
    @P(value = "App description, optional", required = false) String description,
    @P(value = """
      App and project ID, optional. When omitted, the server generates it. \
      If provided, it must match ^[a-z][a-z0-9_]{1,62}$.\
      """, required = false) String projectId,
    @P(value = "App icon name, optional", required = false) String iconName,
    @P(value = "App primary color slug, optional", required = false) String primaryColor
  ) {
    log.info("create_app called, name={}, projectId={}", name, projectId);
    try {
      access.requireDirectory();
      AppResponse app = appService.createApp(new AppCreateRequest(
        name, description, projectId, iconName, primaryColor));
      return JsonUtils.toJsonString(app);
    } catch (Exception exception) {
      log.error("create_app failed, name={}, projectId={}", name, projectId, exception);
      return "Error: create_app failed - " + exception.getMessage();
    }
  }

  @Tool(name = "create_app_form", value = """
    Create a form and its fields. This is step 2 of the app creation flow. \
    fieldsJson must be a JSON array of AppFormFieldCreateRequest objects. \
    Field types allow TEXT, NTEXT, PHONE, EMAIL, URL, SERIES, PICKLIST, SELECT, \
    NUMBER, DECIMAL, DATE, DATETIME, TIME, CLASSIFICATION, MULTISELECT, TAG, \
    REFERENCE, FILE, IMAGE, AVATAR, BARCODE, LOCATION, SIGN, BOOL, or STATE. \
    Each field item should include a name generated by the calling AI from its label, \
    using concise English snake_case matching ^[a-z][a-z0-9_]{0,63}$. The server \
    validates the name and returns the final form contract. If name is omitted, \
    the server derives it from an ASCII label or falls back to a random name. \
    The form contract also contains viewTabs, the record page tabs. A new form \
    starts with the default "all" table view; configure the complete view tab \
    list (TABLE, KANBAN, CALENDAR, GALLERY, DETAIL, HIERARCHY, GANTT) with \
    save_app_form_views after this step. \
    A REFERENCE field must set referenceForm to \
    a form name returned by list_app_forms. The available reference targets \
    include the built-in forms f_user, f_role, and f_department, plus any \
    user-created forms in the same app.\
    """)
  public String create_app_form(
    @P("The appId returned by create_app") String appId,
    @P("Human-readable form label") String label,
    @P("Form name matching ^[a-z][a-z0-9_]{0,63}$") String name,
    @P(value = "Form description", required = false) String description,
    @P("Whether to create the built-in name field") boolean createDisplayField,
    @P("""
      JSON array string of fields, for example: \
      [{"label":"Amount","name":"amount","type":"DECIMAL","required":true}]. \
      Each item may contain label, name, type, required, options, referenceForm, and config. \
      config supports defaultValue and creatable/updatable; TEXT/NTEXT also support \
      maxLength, commonValues, pattern (Java full-match regex), validationMessage; \
      NUMBER/DECIMAL support allowNegative, thousandsSeparator, prefix, suffix; \
      DECIMAL supports decimalPlaces (0-6). DATE/DATETIME/TIME support defaultNow \
      and dateFormat. PICKLIST/SELECT support displayStyle DROPDOWN or RADIO; \
      MULTISELECT supports CHECKBOX or DROPDOWN. Only use properties applicable to the type.\
      """) String fieldsJson
  ) {
    log.info("create_app_form called, appId={}, name={}", appId, name);
    try {
      access.requireApp(appId, true);
      List<AppFormFieldCreateRequest> fields = JsonUtils.parseToList(
        fieldsJson, AppFormFieldCreateRequest.class);
      return JsonUtils.toJsonString(appFormService.createFormWithFields(
        appId,
        new AppFormCreateRequest(label, name, description, createDisplayField),
        fields));
    } catch (Exception exception) {
      log.error("create_app_form failed, appId={}, name={}", appId, name, exception);
      return "Error: create_app_form failed - " + exception.getMessage();
    }
  }

  @Tool(name = "save_app_form_layout", value = """
    Save the flat form layout and optional field controls. This is step 3 of the app creation flow. \
    itemsJson must be a JSON array of flat layout nodes. type allows FIELD, \
    SECTION, or SUBFORM. width allows QUARTER, THIRD, HALF, TWO_THIRDS, \
    THREE_QUARTERS, or FULL. SECTION is a full-width separator, not a container; \
    children is not allowed. FIELD.name must come from create_app_form or \
    get_app_form. Every business field must appear exactly once. \
    controlsJson is an optional JSON object keyed by field name, for example: \
    {"title":{"hidden":{"scenes":["CREATE","UPDATE"],"condition":{"status":{"_eq":"REJECTED"}}},"required":{"scenes":["CREATE"]}}}. \
    Each value may contain hidden, required, and readonly controls. Each control must have a \
    non-empty scenes array using CREATE and/or UPDATE; condition is optional and uses the same \
    filter DSL as record queries (implicit AND, _eq/_ne/_gt/_gte/_lt/_lte/_contains/_not_contains/\
    _starts_with/_ends_with/_in/_nin/_between, _and/_or). Condition field names must exist in \
    the form returned by get_app_form. Without condition the control always \
    applies in the selected scenes. Omit controlsJson to keep the current controls; pass {} to \
    clear all controls. Control keys must exist in itemsJson. Builtin fields always stay visible \
    and readonly and ignore controls.\
    """)
  public String save_app_form_layout(
    @P("The app ID") String appId,
    @P("The form name") String formName,
    @P("""
      JSON array string of flat layout items, for example: \
      [{"type":"FIELD","name":"name","width":"HALF"}].\
      """) String itemsJson,
    @P(value = """
      Optional JSON object of field controls keyed by field name, for example: \
      {"title":{"hidden":{"scenes":["CREATE","UPDATE"],"condition":{"status":{"_eq":"REJECTED"}}}},\
      "amount":{"readonly":{"scenes":["UPDATE"]}}}. Omit to keep existing controls; pass {} to clear.\
      """, required = false) String controlsJson
  ) {
    log.info("save_app_form_layout called, appId={}, formName={}", appId, formName);
    try {
      access.requireApp(appId, true);
      List<AppFormItemRecord> items = List.of(JsonUtils.parseToObject(
        itemsJson, AppFormItemRecord[].class));
      Map<String, AppFormFieldControls> controls = controlsJson == null || controlsJson.isBlank()
        ? currentControls(appId, formName, items)
        : parseControls(controlsJson);
      AppFormResponse form = appFormService.saveFormLayout(
        appId, formName, new AppFormLayoutRequest(items, controls));
      return JsonUtils.toJsonString(form);
    } catch (Exception exception) {
      log.error("save_app_form_layout failed, appId={}, formName={}", appId, formName, exception);
      return "Error: save_app_form_layout failed - " + exception.getMessage();
    }
  }

  /**
   * 未传 controlsJson 时保留当前控制，并按新的布局字段裁剪，避免 AI 仅调整布局时清空控制。
   */
  private Map<String, AppFormFieldControls> currentControls(
    String appId,
    String formName,
    List<AppFormItemRecord> items
  ) {
    AppFormResponse current = appFormService.findForm(appId, formName);
    Map<String, AppFormFieldControls> stored = current.layout() == null
      ? null
      : current.layout().controls();
    if (stored == null || stored.isEmpty()) {
      return null;
    }
    Set<String> fieldNames = items.stream()
      .filter(item -> "FIELD".equals(item.type()))
      .map(AppFormItemRecord::name)
      .collect(Collectors.toSet());
    Map<String, AppFormFieldControls> preserved = new LinkedHashMap<>();
    stored.forEach((fieldName, control) -> {
      if (fieldNames.contains(fieldName)) {
        preserved.put(fieldName, control);
      }
    });
    return preserved.isEmpty() ? null : preserved;
  }

  static Map<String, AppFormFieldControls> parseControls(String controlsJson) {
    Map<String, Object> values = JsonUtils.parseToMap(controlsJson);
    if (values == null) {
      throw new IllegalArgumentException("controlsJson must be a JSON object");
    }
    Map<String, AppFormFieldControls> controls = new LinkedHashMap<>();
    values.forEach((fieldName, value) ->
      controls.put(fieldName, JsonUtils.convertValue(value, AppFormFieldControls.class)));
    return controls;
  }

  @Tool(name = "get_app_form_view_capabilities", value = """
    Get the AI-friendly view tab capability catalog for one form. It returns the \
    view type specs (TABLE, KANBAN, CALENDAR, GALLERY, DETAIL, HIERARCHY, GANTT) \
    with per-type config field contracts (required or optional, allowed field \
    types, enum values, defaults), the filter operators available for each field \
    type of this form, the builtin sort fields, size limits, the form's real \
    fields, and the current views. Call this before generating the view tab \
    list with save_app_form_views.\
    """)
  public String get_app_form_view_capabilities(
    @P("The app ID") String appId,
    @P("The form name returned by create_app_form or list_app_forms") String formName
  ) {
    log.info("get_app_form_view_capabilities called, appId={}, formName={}", appId, formName);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appFormService.findViewCapabilities(appId, formName));
    } catch (Exception exception) {
      log.error("get_app_form_view_capabilities failed, appId={}, formName={}", appId, formName, exception);
      return "Error: get_app_form_view_capabilities failed - " + exception.getMessage();
    }
  }

  @Tool(name = "save_app_form_views", value = """
    Replace the complete view tab list of one form. Views are the record page tabs \
    users switch between, such as a filtered table, a kanban board, or a calendar. \
    This is an optional step of the app creation flow, after save_app_form_layout. \
    Before calling this tool, use get_app_form_view_capabilities to obtain the view \
    type specs, per-type config contracts, the filter operators for this form's \
    fields, and the current views. Every new form starts with the default view \
    {"id":"all","label":"全部","type":"TABLE"}. viewsJson REPLACES the whole list: \
    include every view you want to keep and keep at least one view. Each view is \
    {id,label,type,filter,sort,displayFields,config}; type allows TABLE, KANBAN, \
    CALENDAR, GALLERY, DETAIL, HIERARCHY, or GANTT. Example viewsJson: \
    [{"id":"all","label":"全部","type":"TABLE","sort":{"field":"created_at","order":"DESC"}},\
    {"label":"High priority","type":"TABLE","filter":{"_and":[{"priority":{"_eq":"high"}}]}},\
    {"label":"Status board","type":"KANBAN","config":{"groupField":"status","titleField":"name"}}]\
    """)
  public String save_app_form_views(
    @P("The app ID") String appId,
    @P("The form name returned by create_app_form or list_app_forms") String formName,
    @P("""
      JSON array string of the complete view tab list, for example: \
      [{"id":"all","label":"全部","type":"TABLE"},{"label":"Board","type":"KANBAN","config":{"groupField":"status"}}].\
      Get the full contract from get_app_form_view_capabilities.\
      """) String viewsJson
  ) {
    log.info("save_app_form_views called, appId={}, formName={}", appId, formName);
    try {
      access.requireApp(appId, true);
      return JsonUtils.toJsonString(appFormService.patchForm(
        appId, formName, new AppFormPatchRequest(parseViews(viewsJson))));
    } catch (Exception exception) {
      log.error("save_app_form_views failed, appId={}, formName={}", appId, formName, exception);
      return "Error: save_app_form_views failed - " + exception.getMessage();
    }
  }

  static List<AppFormViewTabRecord> parseViews(String viewsJson) {
    return List.of(JsonUtils.parseToObject(viewsJson, AppFormViewTabRecord[].class));
  }

  @Tool(name = "list_app_workbenches", value = """
    List all workbenches of a Flexmodel App. Calling this tool also creates \
    the app's default workbench when none exists. Use it after creating an app \
    to obtain a workbenchId before saving a layout.\
    """)
  public String list_app_workbenches(
    @P("The app ID") String appId
  ) {
    log.info("list_app_workbenches called, appId={}", appId);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appWorkbenchService.findWorkbenches(appId));
    } catch (Exception exception) {
      log.error("list_app_workbenches failed, appId={}", appId, exception);
      return "Error: list_app_workbenches failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app_workbench_capabilities", value = """
    Get the AI-friendly workbench capability catalog for one app. It returns \
    schemaVersion, component specs, required fields, default configs, enum values, \
    forms, fields, and FORM navigation entries that shortcuts may reference. Call this tool \
    before generating a workbench layout.\
    """)
  public String get_app_workbench_capabilities(
    @P("The app ID") String appId
  ) {
    log.info("get_app_workbench_capabilities called, appId={}", appId);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appWorkbenchService.findCapabilities(appId));
    } catch (Exception exception) {
      log.error("get_app_workbench_capabilities failed, appId={}", appId, exception);
      return "Error: get_app_workbench_capabilities failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app_workbench", value = "Get one workbench by its workbenchId.")
  public String get_app_workbench(
    @P("The app ID") String appId,
    @P("The workbench ID returned by list_app_workbenches") String workbenchId
  ) {
    log.info("get_app_workbench called, appId={}, workbenchId={}", appId, workbenchId);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appWorkbenchService.findWorkbench(appId, workbenchId));
    } catch (Exception exception) {
      log.error("get_app_workbench failed, appId={}, workbenchId={}", appId, workbenchId, exception);
      return "Error: get_app_workbench failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app_workbench_component_data", value = """
    Get real data for one workbench component. Only metric, chart, and dataloader \
    components support this tool. Use get_app_workbench_capabilities first to obtain \
    the component contract, then use get_app_workbench to find componentId. page, \
    size, and search only affect dataloader components.\
    """)
  public String get_app_workbench_component_data(
    @P("The app ID") String appId,
    @P("The workbench ID returned by list_app_workbenches") String workbenchId,
    @P("The component ID from the saved workbench layout") String componentId,
    @P(value = "Page number for dataloader data, default 1", required = false) String page,
    @P(value = "Page size for dataloader data, default 20, maximum 50", required = false) String size,
    @P(value = "Search text for dataloader data, optional", required = false) String search
  ) {
    log.info(
      "get_app_workbench_component_data called, appId={}, workbenchId={}, componentId={}",
      appId, workbenchId, componentId);
    try {
      access.requireApp(appId, false);
      return JsonUtils.toJsonString(appWorkbenchDataService.findComponentData(
        appId, workbenchId, componentId, optionalInteger(page, 1),
        optionalInteger(size, 20), isBlank(search) ? null : search));
    } catch (Exception exception) {
      log.error(
        "get_app_workbench_component_data failed, appId={}, workbenchId={}, componentId={}",
        appId, workbenchId, componentId, exception);
      return "Error: get_app_workbench_component_data failed - " + exception.getMessage();
    }
  }

  @Tool(name = "create_app_workbench", value = """
    Create a workbench for a Flexmodel App. layoutJson is optional; when omitted, \
    an empty schemaVersion 1 layout is created. A valid layout contains schemaVersion, \
    and components with id, title, placement, and a type-discriminated config.\
    """)
  public String create_app_workbench(
    @P("The app ID") String appId,
    @P("Workbench display name, required") String name,
    @P(value = "Workbench description, optional", required = false) String description,
    @P(value = """
      JSON object string for the layout, optional. Get the exact contract from \
      get_app_workbench_capabilities.\
      """, required = false) String layoutJson
  ) {
    log.info("create_app_workbench called, appId={}, name={}", appId, name);
    try {
      access.requireApp(appId, true);
      WorkbenchLayoutRecord layout = isBlank(layoutJson)
        ? WorkbenchLayoutRecord.empty()
        : JsonUtils.parseToObject(layoutJson, WorkbenchLayoutRecord.class);
      return JsonUtils.toJsonString(appWorkbenchService.createWorkbench(
        appId, new WorkbenchCreateRequest(name, description, layout)));
    } catch (Exception exception) {
      log.error("create_app_workbench failed, appId={}, name={}", appId, name, exception);
      return "Error: create_app_workbench failed - " + exception.getMessage();
    }
  }

  @Tool(name = "save_app_workbench_layout", value = """
    Replace the complete layout of one workbench. Before calling this tool, use \
    get_app_workbench_capabilities to obtain component specs and valid references, \
    and get_app_workbench to preserve unchanged components.\
    """)
  public String save_app_workbench_layout(
    @P("The app ID") String appId,
    @P("The workbench ID") String workbenchId,
    @P("""
      JSON object string of the complete schemaVersion 1 layout. Each component \
      needs id, title, placement, and a type-discriminated config.\
      """) String layoutJson
  ) {
    log.info("save_app_workbench_layout called, appId={}, workbenchId={}", appId, workbenchId);
    try {
      access.requireApp(appId, true);
      WorkbenchLayoutRecord layout = JsonUtils.parseToObject(layoutJson, WorkbenchLayoutRecord.class);
      WorkbenchPatchRequest request = new WorkbenchPatchRequest();
      request.setLayout(layout);
      return JsonUtils.toJsonString(appWorkbenchService.patchWorkbench(appId, workbenchId, request));
    } catch (Exception exception) {
      log.error("save_app_workbench_layout failed, appId={}, workbenchId={}", appId, workbenchId, exception);
      return "Error: save_app_workbench_layout failed - " + exception.getMessage();
    }
  }

  @Tool(name = "create_app_workbench_navigation", value = """
    Create a navigation entry linked to the app workbench. This is the one-call way to \
    expose an app's workbench in navigation; it creates an ENTRY with BUILTIN \
    destinationId=workbench. Optionally pass workbenchId (from list_app_workbenches) to \
    bind one specific workbench; when omitted the entry opens the app's first workbench. \
    The other built-in pages (files, approval) are exposed the same way via \
    create_app_navigation with destinationType=BUILTIN.\
    """)
  public String create_app_workbench_navigation(
    @P("The app ID") String appId,
    @P("Navigation entry display name, required") String name,
    @P(value = "Navigation entry icon name, optional", required = false) String iconName,
    @P(value = "Parent navigation ID, only for a nested entry", required = false) String parentId,
    @P(value = """
      Specific workbench ID from list_app_workbenches, optional. \
      Omit to open the app's first workbench by default.\
      """, required = false) String workbenchId
  ) {
    log.info("create_app_workbench_navigation called, appId={}, name={}", appId, name);
    try {
      access.requireApp(appId, true);
      return JsonUtils.toJsonString(appNavigationService.createNav(
        appId,
        new AppNavSaveRequest(
          "ENTRY", name, iconName, parentId,
          new NavDestinationRequest("BUILTIN", "workbench", isBlank(workbenchId) ? null : workbenchId), null)));
    } catch (Exception exception) {
      log.error("create_app_workbench_navigation failed, appId={}, name={}", appId, name, exception);
      return "Error: create_app_workbench_navigation failed - " + exception.getMessage();
    }
  }

  @Tool(name = "create_app_navigation", value = """
    Create an app navigation entry. This is step 4 of the app creation flow. \
    navType allows GROUP or ENTRY. GROUP must not bind a destination. ENTRY must bind \
    destinationType BUILTIN, FORM, or LINK. Use destinationId=formName when binding a form, \
    or destinationId=http/https URL when binding a link. BUILTIN pages (workbench, files, \
    approval) are only accessible inside an app after a navigation ENTRY binds them; \
    bind the built-in pages the app needs, for example workbench and approval. \
    When destinationId=workbench, optionally set workbenchId (from list_app_workbenches) \
    to bind one specific workbench; when omitted the entry opens the app's first workbench. \
    iconName is a Lucide icon name in PascalCase, for example Home, Package, or Rocket.\
    """)
  public String create_app_navigation(
    @P("The app ID") String appId,
    @P("Navigation type: GROUP or ENTRY") String navType,
    @P("Navigation entry display name") String name,
    @P(value = "Navigation entry icon name, optional", required = false) String iconName,
    @P(value = "Parent navigation ID, only for a nested ENTRY", required = false) String parentId,
    @P(value = "Destination type: BUILTIN, FORM, or LINK, required for ENTRY", required = false) String destinationType,
    @P(value = """
      BUILTIN page ID (workbench, files, or approval), form name, or http/https URL, \
      required for ENTRY\
      """, required = false) String destinationId,
    @P(value = """
      Specific workbench ID from list_app_workbenches, only for destinationType=BUILTIN \
      and destinationId=workbench; omit to open the app's first workbench by default\
      """, required = false) String workbenchId
  ) {
    log.info("create_app_navigation called, appId={}, navType={}, name={}", appId, navType, name);
    try {
      access.requireApp(appId, true);
      NavDestinationRequest destination = isBlank(destinationType) && isBlank(destinationId)
        ? null
        : new NavDestinationRequest(destinationType, destinationId, isBlank(workbenchId) ? null : workbenchId);
      return JsonUtils.toJsonString(appNavigationService.createNav(
        appId, new AppNavSaveRequest(navType, name, iconName, parentId, destination, null)));
    } catch (Exception exception) {
      log.error("create_app_navigation failed, appId={}, navType={}, name={}", appId, navType, name, exception);
      return "Error: create_app_navigation failed - " + exception.getMessage();
    }
  }

  @Tool(name = "create_app_mock_records", value = """
    Create explicit mock records for one form. This is step 5 of the app creation flow. \
    recordsJson must be a JSON array of objects using server-generated field names. \
    Records are created in order. Optional mainFormName, mainRecordId, and \
    referenceField provide subform context. If a record fails, later records are \
    not created and the error contains the 1-based failing index.\
    """)
  public String create_app_mock_records(
    @P("The app ID") String appId,
    @P("The form name") String formName,
    @P("""
      JSON array string of records, for example: \
      [{"name":"First record"}]. Use field names returned by get_app_form.\
      """) String recordsJson,
    @P(value = "Main form name for subform context, optional", required = false) String mainFormName,
    @P(value = "Main record ID for subform context, optional", required = false) String mainRecordId,
    @P(value = "Child form reference field for subform context, optional", required = false) String referenceField
  ) {
    log.info("create_app_mock_records called, appId={}, formName={}", appId, formName);
    try {
      access.requireApp(appId, true);
      List<Map<String, Object>> records = JsonUtils.parseToMapList(recordsJson);
      List<Map<String, Object>> createdRecords = new ArrayList<>();
      for (int index = 0; index < records.size(); index++) {
        try {
          createdRecords.add(appRecordService.createRecord(
            appId, formName, records.get(index), mainFormName, mainRecordId, referenceField));
        } catch (Exception recordException) {
          log.error("create_app_mock_records failed, appId={}, formName={}, recordIndex={}",
            appId, formName, index + 1, recordException);
          return "Error: create_app_mock_records failed at record index " + (index + 1)
            + " - " + recordException.getMessage();
        }
      }
      return JsonUtils.toJsonString(createdRecords);
    } catch (Exception exception) {
      log.error("create_app_mock_records failed, appId={}, formName={}", appId, formName, exception);
      return "Error: create_app_mock_records failed - " + exception.getMessage();
    }
  }

  @Tool(name = "query_app_records", value = """
    Query business records of one form with optional filters (read-only, at most 20 \
    returned). filter is a JSON string using the record query DSL, for example \
    {"status":{"_eq":"open"}}. Use field names returned by get_app_form.\
    """)
  public String query_app_records(
    @P("The app ID") String appId,
    @P("The form name returned by list_app_forms") String form,
    @P(value = "Record query filter DSL as a JSON string, optional", required = false) String filter,
    @P(value = "Keyword search text, optional", required = false) String search,
    @P(value = "Number of records to return, 1-20, default 10", required = false) Integer limit
  ) {
    log.info("query_app_records called, appId={}, form={}", appId, form);
    try {
      access.requireApp(appId, false);
      String formName = requireForm(appId, form);
      AppFormResponse appForm = appFormService.findForm(appId, formName);
      List<String> fieldNames = businessFieldNames(appForm);
      String combinedFilter = appRecordService.buildFilter(
        appForm, isBlank(search) ? null : search, null, null, filter);
      var page = appRecordService.queryViewRecords(appId, appForm, 1,
        Math.clamp(limit == null ? 10 : limit, 1, MAX_RECORDS), combinedFilter, null, fieldNames);
      List<Map<String, Object>> list = new ArrayList<>();
      int resultChars = 0;
      for (Map<String, Object> record : page.list()) {
        Map<String, Object> compacted = compact(record);
        int size = JsonUtils.toJsonString(compacted).length();
        if (!list.isEmpty() && resultChars + size > MAX_RESULT_LENGTH) break;
        list.add(compacted);
        resultChars += size;
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("form", formName);
      result.put("total", page.total());
      result.put("returned", list.size());
      result.put("records", list);
      return JsonUtils.toJsonString(result);
    } catch (Exception exception) {
      log.error("query_app_records failed, appId={}, form={}", appId, form, exception);
      return "Error: query_app_records failed - " + exception.getMessage();
    }
  }

  @Tool(name = "count_app_records", value =
    "Count records of one form matching an optional filter. filter uses the same JSON DSL as query_app_records.")
  public String count_app_records(
    @P("The app ID") String appId,
    @P("The form name returned by list_app_forms") String form,
    @P(value = "Record query filter DSL as a JSON string, optional", required = false) String filter
  ) {
    log.info("count_app_records called, appId={}, form={}", appId, form);
    try {
      access.requireApp(appId, false);
      String formName = requireForm(appId, form);
      AppFormResponse appForm = appFormService.findForm(appId, formName);
      long total = appRecordService.countViewRecords(appId, appForm, filter);
      return JsonUtils.toJsonString(Map.of("form", formName, "total", total));
    } catch (Exception exception) {
      log.error("count_app_records failed, appId={}, form={}", appId, form, exception);
      return "Error: count_app_records failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app_record", value = "Get one business record by its record ID (read-only).")
  public String get_app_record(
    @P("The app ID") String appId,
    @P("The form name returned by list_app_forms") String form,
    @P("The record ID") String id
  ) {
    log.info("get_app_record called, appId={}, form={}, id={}", appId, form, id);
    try {
      access.requireApp(appId, false);
      String formName = requireForm(appId, form);
      if (isBlank(id)) throw new IllegalArgumentException("record id 不能为空");
      Map<String, Object> record = appRecordService.findRecord(appId, formName, id, null, null, null);
      return JsonUtils.toJsonString(Map.of("form", formName, "record", compact(record)));
    } catch (Exception exception) {
      log.error("get_app_record failed, appId={}, form={}, id={}", appId, form, id, exception);
      return "Error: get_app_record failed - " + exception.getMessage();
    }
  }

  @Tool(name = "get_app_approval_capabilities", value = """
    Get the approval authoring contract for this app: node configs, Flow element types, \
    conditions, member rules, real forms/fields, subflows, limits and complete simple/gateway \
    examples. Call before generating flowJson. Approval definitions use the native Flow \
    contract, including flow_model as a JSON string. Use the separate get_directory and \
    resolve_members tools to select real people, roles or organizations.\
    """)
  public String get_app_approval_capabilities(
    @P("The app ID") String appId
  ) {
    return approvalTool("get_app_approval_capabilities", appId, false, () -> appApprovalCapabilities.describe(appId));
  }

  @Tool(name = "list_app_approval_flows", value = """
    List approval flows belonging to an app, using flow_module_id, flow_name, flow_key, \
    flow_model (JSON string), status and revision. status: 1 draft, 2 enabled, 3 disabled. \
    Use this before creating a flow to avoid duplicates or find existing subflows.\
    """)
  public String list_app_approval_flows(
    @P("The app ID") String appId
  ) {
    return approvalTool("list_app_approval_flows", appId, false,
            () -> appApprovalFlowService.list(appId).stream().map(AppApprovalFlowModel::encode).toList());
  }

  @Tool(name = "get_app_approval_flow", value = "Get an app approval flow and its latest revision before editing or changing status.")
  public String get_app_approval_flow(
    @P("The app ID") String appId,
    @P("flow_module_id returned by list_app_approval_flows or save_app_approval_flow") String flowId
  ) {
    return approvalTool("get_app_approval_flow", appId, false,
            () -> AppApprovalFlowModel.encode(appApprovalFlowService.get(appId, flowId)));
  }

  @Tool(name = "save_app_approval_flow", value = """
    Create or replace an app approval draft. Call get_app_approval_capabilities first. \
    For creation omit flowId; for updates pass flowId and the latest revision in flowJson. \
    flowJson uses the same Flow contract as get_app_approval_flow; preserve unchanged nodes. \
    Returns the saved flow_module_id and revision. Saving an enabled flow returns it to draft. \
    Incomplete drafts are allowed: validate and explicitly enable before running.\
    """)
  public String save_app_approval_flow(
    @P("The app ID") String appId,
    @P(value = "Existing flow_module_id for updates; omit to create", required = false) String flowId,
    @P("JSON object string with flow_name, flow_model (a nested JSON STRING), optional flow_key/remark, and revision for updates") String flowJson
  ) {
    return approvalTool("save_app_approval_flow", appId, true, () -> {
      var body = JsonUtils.parseToMap(flowJson);
      String bodyId = java.util.Objects.toString(body.get("flow_module_id"), "");
      if (!bodyId.isBlank() && !bodyId.equals(flowId)) {
        throw new IllegalArgumentException("flow_module_id must match flowId; omit both to create a new flow");
      }
      return AppApprovalFlowModel.encode(appApprovalFlowService.save(appId, isBlank(flowId) ? null : flowId, AppApprovalFlowModel.decode(body)));
    });
  }

  @Tool(name = "validate_app_approval_flow", value = """
    Validate an approval definition without saving or publishing it. Accepts the full Flow \
    JSON contract returned by get_app_approval_flow or a generated draft. Returns an array \
    of {nodeId,message} issues; an empty array means the definition passes validation. \
    Fix all issues before enabling. Malformed Flow JSON returns an Error message.\
    """)
  public String validate_app_approval_flow(
    @P("The app ID") String appId,
    @P("JSON object string of the complete approval Flow contract, including flow_module_id when validating an existing flow") String flowJson
  ) {
    return approvalTool("validate_app_approval_flow", appId, false,
            () -> appApprovalFlowService.validate(appId, AppApprovalFlowModel.decode(JsonUtils.parseToMap(flowJson))));
  }

  @Tool(name = "set_app_approval_flow_status", value = """
    Publish/enable (status=2) or disable (status=3) an app approval flow using its latest \
    revision. Enabling validates again and creates an immutable Flow deployment; referenced \
    subflows must already be enabled. Returns the updated flow and revision. A version \
    conflict requires re-reading the flow. Existing runs keep their published version. \
    This does not submit records or make approval decisions.\
    """)
  public String set_app_approval_flow_status(
    @P("The app ID") String appId,
    @P("The flow_module_id") String flowId,
    @P("2 = publish and enable; 3 = disable") int status,
    @P("Latest revision returned by get/save/status") int revision
  ) {
    return approvalTool("set_app_approval_flow_status", appId, true, () -> {
      if (status != 2 && status != 3) throw new IllegalArgumentException("status must be 2 (enabled) or 3 (disabled)");
      return AppApprovalFlowModel.encode(appApprovalFlowService.status(appId, flowId, status == 2 ? "enabled" : "disabled", revision));
    });
  }

  private String approvalTool(String tool, String appId, boolean write, Supplier<?> action) {
    log.info("{} called, appId={}", tool, appId);
    try {
      access.requireApp(appId, write);
      return JsonUtils.toJsonString(action.get());
    } catch (Exception exception) {
      log.error("{} failed, appId={}", tool, appId, exception);
      return "Error: " + tool + " failed - " + exception.getMessage();
    }
  }

  private List<String> businessFieldNames(AppFormResponse appForm) {
    List<String> fieldNames = new ArrayList<>();
    for (AppFormFieldResponse field : appForm.fields() == null ? List.<AppFormFieldResponse>of() : appForm.fields()) {
      if (fieldNames.size() >= MAX_FIELDS) break;
      if (!field.builtin()) fieldNames.add(field.name());
    }
    return fieldNames;
  }

  private String requireForm(String appId, String formName) {
    if (isBlank(formName)) throw new IllegalArgumentException("form 不能为空");
    boolean exists = appFormService.findForms(appId).stream()
      .filter(form -> !form.builtin())
      .anyMatch(form -> form.name().equals(formName));
    if (!exists) throw new IllegalArgumentException("表单不存在: " + formName);
    return formName;
  }

  private Map<String, Object> compact(Map<String, Object> record) {
    if (record == null) return Map.of();
    Map<String, Object> result = new LinkedHashMap<>();
    int[] budget = {MAX_RESULT_LENGTH};
    record.forEach((key, value) -> {
      if (budget[0] <= 0) return;
      Object compacted = compactValue(value, budget);
      if (compacted != null) result.put(key, compacted);
    });
    return result;
  }

  private Object compactValue(Object value, int[] budget) {
    if (value == null) return null;
    if (value instanceof String text) {
      String compacted = text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH) + "…";
      budget[0] -= compacted.length();
      return compacted;
    }
    if (value instanceof Number || value instanceof Boolean) {
      budget[0] -= 20;
      return value;
    }
    String json;
    try {
      json = JsonUtils.toJsonString(value);
    } catch (RuntimeException e) {
      json = String.valueOf(value);
    }
    if (json.length() > 500) json = json.substring(0, 500) + "…";
    budget[0] -= json.length();
    return json;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private Integer optionalInteger(String value, int fallback) {
    if (isBlank(value)) {
      return fallback;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("数字参数不合法: " + value);
    }
  }
}
