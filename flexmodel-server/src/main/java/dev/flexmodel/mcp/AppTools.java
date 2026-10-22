package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.approval.ApprovalCapabilities;
import dev.flexmodel.approval.ApprovalFlowModel;
import dev.flexmodel.approval.ApprovalFlowService;
import dev.flexmodel.apps.AppFormService;
import dev.flexmodel.apps.AppMenuService;
import dev.flexmodel.apps.AppRecordService;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.WorkbenchDataService;
import dev.flexmodel.apps.WorkbenchService;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.dto.AppFormCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRequest;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppMenuSaveRequest;
import dev.flexmodel.apps.dto.AppMenuTargetRequest;
import dev.flexmodel.apps.dto.AppResponse;
import dev.flexmodel.apps.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.apps.dto.WorkbenchPatchRequest;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MCP 工具：Flexmodel Apps
 * 将应用、表单、布局、菜单、工作台、审批流程和记录能力开放给 AI。
 */
@Slf4j
public class AppTools {

  @Inject
  AppService appService;

  @Inject
  AppFormService appFormService;

  @Inject
  AppMenuService appMenuService;

  @Inject
  AppRecordService appRecordService;

  @Inject
  WorkbenchService workbenchService;

  @Inject
  WorkbenchDataService workbenchDataService;

  @Inject
  ApprovalFlowService approvalFlows;

  @Inject
  ApprovalCapabilities approvalCapabilities;

  @Inject
  McpToolAccess access;

  @Tool(description = """
    List all Flexmodel Apps. Each app is an isolated top-level project \
    with forms, menus, and records. Use this tool before creating an app \
    to check whether the desired appId already exists.\
    """)
  public String list_apps() {
    log.info("list_apps called");
    try {
      return JsonUtils.toJsonString(appService.findApps());
    } catch (Exception exception) {
      log.error("list_apps failed", exception);
      return "Error: list_apps failed - " + exception.getMessage();
    }
  }

  @Tool(description = "Get one Flexmodel App by its appId.")
  public String get_app(
    @ToolArg(description = "The app ID, also used as the project ID") String appId
  ) {
    log.info("get_app called, appId={}", appId);
    try {
      return JsonUtils.toJsonString(appService.findApp(appId));
    } catch (Exception exception) {
      log.error("get_app failed, appId={}", appId, exception);
      return "Error: get_app failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    List all forms of a Flexmodel App. Use this tool to confirm form names \
    and generated field names before saving a layout or creating records.\
    The list includes the built-in forms f_user, f_role, and f_department.\
    """)
  public String list_app_forms(
    @ToolArg(description = "The app ID") String appId
  ) {
    log.info("list_app_forms called, appId={}", appId);
    try {
      return JsonUtils.toJsonString(appFormService.findForms(appId));
    } catch (Exception exception) {
      log.error("list_app_forms failed, appId={}", appId, exception);
      return "Error: list_app_forms failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Get one form of a Flexmodel App by formName. The response contains \
    generated field names and the current flat layout.items contract.\
    """)
  public String get_app_form(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The form name returned by create_app_form or list_app_forms") String formName
  ) {
    log.info("get_app_form called, appId={}, formName={}", appId, formName);
    try {
      return JsonUtils.toJsonString(appFormService.findForm(appId, formName));
    } catch (Exception exception) {
      log.error("get_app_form failed, appId={}, formName={}", appId, formName, exception);
      return "Error: get_app_form failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Create a Flexmodel App. This is step 1 of the app creation flow. \
    iconName allows ShoppingCart, Banknote, Boxes, Folder, Tags, or TrendingUp. \
    primaryColor allows blue, indigo, green, amber, rose, or black; blue is the default.\
    """)
  public String create_app(
    @ToolArg(description = "Human-readable app name, required") String name,
    @ToolArg(description = "App description, optional") String description,
    @ToolArg(description = """
      App and project ID, optional. When omitted, the server generates it. \
      If provided, it must match ^[a-z][a-z0-9_]{1,62}$.\
      """) String projectId,
    @ToolArg(description = "App icon name, optional") String iconName,
    @ToolArg(description = "App primary color, optional") String primaryColor
  ) {
    log.info("create_app called, name={}, projectId={}", name, projectId);
    try {
      AppResponse app = appService.createApp(new AppCreateRequest(
        name, description, projectId, iconName, primaryColor));
      return JsonUtils.toJsonString(app);
    } catch (Exception exception) {
      log.error("create_app failed, name={}, projectId={}", name, projectId, exception);
      return "Error: create_app failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Create a form and its fields. This is step 2 of the app creation flow. \
    fieldsJson must be a JSON array of AppFormFieldCreateRequest objects. \
    Field types allow TEXT, NTEXT, PHONE, EMAIL, URL, SERIES, PICKLIST, SELECT, \
    NUMBER, DECIMAL, DATE, DATETIME, TIME, CLASSIFICATION, MULTISELECT, TAG, \
    REFERENCE, FILE, IMAGE, AVATAR, BARCODE, LOCATION, SIGN, BOOL, or STATE. \
    Each field item should include a name generated by the calling AI from its label, \
    using concise English snake_case matching ^[a-z][a-z0-9_]{0,63}$. The server \
    validates the name and returns the final form contract. If name is omitted, \
    the server derives it from an ASCII label or falls back to a random name. \
    The form contract also contains viewTabs, the record-list tab configuration. \
    Each view tab is {id,label,filter}. Configure tabs by PATCHing the form with \
    {"viewTabs":[...]} only. A tab id is optional, must be unique, at most 64 \
    characters, and cannot be "all"; a label must be non-empty, unique, and at \
    most 32 characters. filter must be {"_and":[{field:{operator:value}}, ...]} \
    with 1-10 conditions. Each condition may reference one existing field and \
    one operator. Numeric and date/time fields support _eq, _ne, _gt, _gte, \
    _lt, and _lte. BOOL and choice/reference-like fields support _eq and _ne. \
    Other text-like fields also support _contains, _not_contains, \
    _starts_with, and _ends_with. A REFERENCE field must set referenceForm to \
    a form name returned by list_app_forms. The available reference targets \
    include the built-in forms f_user, f_role, and f_department, plus any \
    user-created forms in the same app.\
    """)
  public String create_app_form(
    @ToolArg(description = "The appId returned by create_app") String appId,
    @ToolArg(description = "Human-readable form label") String label,
    @ToolArg(description = "Form name matching ^[a-z][a-z0-9_]{0,63}$") String name,
    @ToolArg(description = "Form description") String description,
    @ToolArg(description = "Whether to create the built-in name field") boolean createDisplayField,
    @ToolArg(description = """
      JSON array string of fields, for example: \
      [{"label":"Amount","name":"amount","type":"DECIMAL","required":true}]. \
      Each item may contain label, name, type, required, options, and referenceForm.\
      """) String fieldsJson
  ) {
    log.info("create_app_form called, appId={}, name={}", appId, name);
    try {
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

  @Tool(description = """
    Save the flat form layout. This is step 3 of the app creation flow. \
    itemsJson must be a JSON array of flat layout nodes. type allows FIELD, \
    SECTION, or SUBFORM. width allows QUARTER, THIRD, HALF, TWO_THIRDS, \
    THREE_QUARTERS, or FULL. SECTION is a full-width separator, not a container; \
    children is not allowed. FIELD.name must come from create_app_form or \
    get_app_form. Every business field must appear exactly once.\
    """)
  public String save_app_form_layout(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The form name") String formName,
    @ToolArg(description = """
      JSON array string of flat layout items, for example: \
      [{"type":"FIELD","name":"name","width":"HALF"}].\
      """) String itemsJson
  ) {
    log.info("save_app_form_layout called, appId={}, formName={}", appId, formName);
    try {
      List<AppFormItemRecord> items = List.of(JsonUtils.parseToObject(
        itemsJson, AppFormItemRecord[].class));
      AppFormResponse form = appFormService.saveFormLayout(
        appId, formName, new AppFormLayoutRequest(items));
      return JsonUtils.toJsonString(form);
    } catch (Exception exception) {
      log.error("save_app_form_layout failed, appId={}, formName={}", appId, formName, exception);
      return "Error: save_app_form_layout failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    List all workbenches of a Flexmodel App. Calling this tool also creates \
    the app's default workbench when none exists. Use it after creating an app \
    to obtain a workbenchId before saving a layout.\
    """)
  public String list_app_workbenches(
    @ToolArg(description = "The app ID") String appId
  ) {
    log.info("list_app_workbenches called, appId={}", appId);
    try {
      return JsonUtils.toJsonString(workbenchService.findWorkbenches(appId));
    } catch (Exception exception) {
      log.error("list_app_workbenches failed, appId={}", appId, exception);
      return "Error: list_app_workbenches failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Get the AI-friendly workbench capability catalog for one app. It returns \
    schemaVersion, component specs, required fields, default configs, enum values, \
    forms, fields, and FORM menus that shortcuts may reference. Call this tool \
    before generating a workbench layout.\
    """)
  public String get_app_workbench_capabilities(
    @ToolArg(description = "The app ID") String appId
  ) {
    log.info("get_app_workbench_capabilities called, appId={}", appId);
    try {
      return JsonUtils.toJsonString(workbenchService.findCapabilities(appId));
    } catch (Exception exception) {
      log.error("get_app_workbench_capabilities failed, appId={}", appId, exception);
      return "Error: get_app_workbench_capabilities failed - " + exception.getMessage();
    }
  }

  @Tool(description = "Get one workbench by its workbenchId.")
  public String get_app_workbench(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The workbench ID returned by list_app_workbenches") String workbenchId
  ) {
    log.info("get_app_workbench called, appId={}, workbenchId={}", appId, workbenchId);
    try {
      return JsonUtils.toJsonString(workbenchService.findWorkbench(appId, workbenchId));
    } catch (Exception exception) {
      log.error("get_app_workbench failed, appId={}, workbenchId={}", appId, workbenchId, exception);
      return "Error: get_app_workbench failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Get real data for one workbench component. Only metric, chart, and dataloader \
    components support this tool. Use get_app_workbench_capabilities first to obtain \
    the component contract, then use get_app_workbench to find componentId. page, \
    size, and search only affect dataloader components.\
    """)
  public String get_app_workbench_component_data(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The workbench ID returned by list_app_workbenches") String workbenchId,
    @ToolArg(description = "The component ID from the saved workbench layout") String componentId,
    @ToolArg(description = "Page number for dataloader data, default 1") String page,
    @ToolArg(description = "Page size for dataloader data, default 20, maximum 50") String size,
    @ToolArg(description = "Search text for dataloader data, optional") String search
  ) {
    log.info(
      "get_app_workbench_component_data called, appId={}, workbenchId={}, componentId={}",
      appId, workbenchId, componentId);
    try {
      return JsonUtils.toJsonString(workbenchDataService.findComponentData(
        appId, workbenchId, componentId, optionalInteger(page, 1),
        optionalInteger(size, 20), isBlank(search) ? null : search));
    } catch (Exception exception) {
      log.error(
        "get_app_workbench_component_data failed, appId={}, workbenchId={}, componentId={}",
        appId, workbenchId, componentId, exception);
      return "Error: get_app_workbench_component_data failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Create a workbench for a Flexmodel App. layoutJson is optional; when omitted, \
    an empty schemaVersion 1 layout is created. A valid layout contains schemaVersion, \
    and components with id, title, placement, and a type-discriminated config.\
    """)
  public String create_app_workbench(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "Workbench display name, required") String name,
    @ToolArg(description = "Workbench description, optional") String description,
    @ToolArg(description = """
      JSON object string for the layout, optional. Get the exact contract from \
      get_app_workbench_capabilities.\
      """) String layoutJson
  ) {
    log.info("create_app_workbench called, appId={}, name={}", appId, name);
    try {
      WorkbenchLayoutRecord layout = isBlank(layoutJson)
        ? WorkbenchLayoutRecord.empty()
        : JsonUtils.parseToObject(layoutJson, WorkbenchLayoutRecord.class);
      return JsonUtils.toJsonString(workbenchService.createWorkbench(
        appId, new WorkbenchCreateRequest(name, description, layout)));
    } catch (Exception exception) {
      log.error("create_app_workbench failed, appId={}, name={}", appId, name, exception);
      return "Error: create_app_workbench failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Replace the complete layout of one workbench. Before calling this tool, use \
    get_app_workbench_capabilities to obtain component specs and valid references, \
    and get_app_workbench to preserve unchanged components.\
    """)
  public String save_app_workbench_layout(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The workbench ID") String workbenchId,
    @ToolArg(description = """
      JSON object string of the complete schemaVersion 1 layout. Each component \
      needs id, title, placement, and a type-discriminated config.\
      """) String layoutJson
  ) {
    log.info("save_app_workbench_layout called, appId={}, workbenchId={}", appId, workbenchId);
    try {
      WorkbenchLayoutRecord layout = JsonUtils.parseToObject(layoutJson, WorkbenchLayoutRecord.class);
      WorkbenchPatchRequest request = new WorkbenchPatchRequest();
      request.setLayout(layout);
      return JsonUtils.toJsonString(workbenchService.patchWorkbench(appId, workbenchId, request));
    } catch (Exception exception) {
      log.error("save_app_workbench_layout failed, appId={}, workbenchId={}", appId, workbenchId, exception);
      return "Error: save_app_workbench_layout failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Create a menu entry linked to the app workbench. This is the one-call way to \
    expose an app's workbench in navigation; it creates an ITEM with BUILTIN \
    targetId=workbench.\
    """)
  public String create_app_workbench_menu(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "Menu display name, required") String name,
    @ToolArg(description = "Menu icon name, optional") String iconName,
    @ToolArg(description = "Parent menu ID, only for a nested menu item") String parentId
  ) {
    log.info("create_app_workbench_menu called, appId={}, name={}", appId, name);
    try {
      return JsonUtils.toJsonString(appMenuService.createMenu(
        appId,
        new dev.flexmodel.apps.dto.AppMenuSaveRequest(
          "ITEM", name, iconName, parentId,
          new dev.flexmodel.apps.dto.AppMenuTargetRequest("BUILTIN", "workbench"))));
    } catch (Exception exception) {
      log.error("create_app_workbench_menu failed, appId={}, name={}", appId, name, exception);
      return "Error: create_app_workbench_menu failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Create an app menu entry. This is step 4 of the app creation flow. \
    menuType allows GROUP or ITEM. GROUP must not bind a target. ITEM must bind \
    targetType BUILTIN, FORM, or LINK. Use targetId=formName when binding a form, \
    or targetId=http/https URL when binding a link. \
    iconName allows Home, User, TrendingUp, ShoppingCart, PieChart, Folder, \
    BookUser, ClipboardCheck, Shapes, Tags, Boxes, or Banknote.\
    """)
  public String create_app_menu(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "Menu type: GROUP or ITEM") String menuType,
    @ToolArg(description = "Menu display name") String name,
    @ToolArg(description = "Menu icon name") String iconName,
    @ToolArg(description = "Parent menu ID, only for a nested ITEM") String parentId,
    @ToolArg(description = "Target type: BUILTIN, FORM, or LINK, required for ITEM") String targetType,
    @ToolArg(description = "BUILTIN page ID, form name, or http/https URL, required for ITEM") String targetId
  ) {
    log.info("create_app_menu called, appId={}, menuType={}, name={}", appId, menuType, name);
    try {
      AppMenuTargetRequest target = isBlank(targetType) && isBlank(targetId)
        ? null
        : new AppMenuTargetRequest(targetType, targetId);
      return JsonUtils.toJsonString(appMenuService.createMenu(
        appId, new AppMenuSaveRequest(menuType, name, iconName, parentId, target)));
    } catch (Exception exception) {
      log.error("create_app_menu failed, appId={}, menuType={}, name={}", appId, menuType, name, exception);
      return "Error: create_app_menu failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Create explicit mock records for one form. This is step 5 of the app creation flow. \
    recordsJson must be a JSON array of objects using server-generated field names. \
    Records are created in order. Optional mainFormName, mainRecordId, and \
    referenceField provide subform context. If a record fails, later records are \
    not created and the error contains the 1-based failing index.\
    """)
  public String create_app_mock_records(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The form name") String formName,
    @ToolArg(description = """
      JSON array string of records, for example: \
      [{"name":"First record"}]. Use field names returned by get_app_form.\
      """) String recordsJson,
    @ToolArg(description = "Main form name for subform context, optional") String mainFormName,
    @ToolArg(description = "Main record ID for subform context, optional") String mainRecordId,
    @ToolArg(description = "Child form reference field for subform context, optional") String referenceField
  ) {
    log.info("create_app_mock_records called, appId={}, formName={}", appId, formName);
    try {
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

  @Tool(description = """
    Get the approval authoring contract for this app: node configs, Flow element types, \
    conditions, member rules, real forms/fields, subflows, limits and complete simple/gateway \
    examples. Call before generating flowJson. Approval definitions use the native Flow \
    contract, including flow_model as a JSON string. Use the separate get_directory and \
    resolve_members tools to select real people, roles or organizations.\
    """)
  public String get_app_approval_capabilities(
    @ToolArg(description = "The app ID") String appId
  ) {
    return approvalTool("get_app_approval_capabilities", appId, false, () -> approvalCapabilities.describe(appId));
  }

  @Tool(description = """
    List approval flows belonging to an app, using flow_module_id, flow_name, flow_key, \
    flow_model (JSON string), status and revision. status: 1 draft, 2 enabled, 3 disabled. \
    Use this before creating a flow to avoid duplicates or find existing subflows.\
    """)
  public String list_app_approval_flows(
    @ToolArg(description = "The app ID") String appId
  ) {
    return approvalTool("list_app_approval_flows", appId, false,
      () -> approvalFlows.list(appId).stream().map(ApprovalFlowModel::encode).toList());
  }

  @Tool(description = "Get an app approval flow and its latest revision before editing or changing status.")
  public String get_app_approval_flow(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "flow_module_id returned by list_app_approval_flows or save_app_approval_flow") String flowId
  ) {
    return approvalTool("get_app_approval_flow", appId, false,
      () -> ApprovalFlowModel.encode(approvalFlows.get(appId, flowId)));
  }

  @Tool(description = """
    Create or replace an app approval draft. Call get_app_approval_capabilities first. \
    For creation omit flowId; for updates pass flowId and the latest revision in flowJson. \
    flowJson uses the same Flow contract as get_app_approval_flow; preserve unchanged nodes. \
    Returns the saved flow_module_id and revision. Saving an enabled flow returns it to draft. \
    Incomplete drafts are allowed: validate and explicitly enable before running.\
    """)
  public String save_app_approval_flow(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "Existing flow_module_id for updates; omit to create", required = false) String flowId,
    @ToolArg(description = "JSON object string with flow_name, flow_model (a nested JSON STRING), optional flow_key/remark, and revision for updates") String flowJson
  ) {
    return approvalTool("save_app_approval_flow", appId, true, () -> {
      var body = JsonUtils.parseToMap(flowJson);
      String bodyId = java.util.Objects.toString(body.get("flow_module_id"), "");
      if (!bodyId.isBlank() && !bodyId.equals(flowId)) {
        throw new IllegalArgumentException("flow_module_id must match flowId; omit both to create a new flow");
      }
      return ApprovalFlowModel.encode(approvalFlows.save(appId, isBlank(flowId) ? null : flowId, ApprovalFlowModel.decode(body)));
    });
  }

  @Tool(description = """
    Validate an approval definition without saving or publishing it. Accepts the full Flow \
    JSON contract returned by get_app_approval_flow or a generated draft. Returns an array \
    of {nodeId,message} issues; an empty array means the definition passes validation. \
    Fix all issues before enabling. Malformed Flow JSON returns an Error message.\
    """)
  public String validate_app_approval_flow(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "JSON object string of the complete approval Flow contract, including flow_module_id when validating an existing flow") String flowJson
  ) {
    return approvalTool("validate_app_approval_flow", appId, false,
      () -> approvalFlows.validate(appId, ApprovalFlowModel.decode(JsonUtils.parseToMap(flowJson))));
  }

  @Tool(description = """
    Publish/enable (status=2) or disable (status=3) an app approval flow using its latest \
    revision. Enabling validates again and creates an immutable Flow deployment; referenced \
    subflows must already be enabled. Returns the updated flow and revision. A version \
    conflict requires re-reading the flow. Existing runs keep their published version. \
    This does not submit records or make approval decisions.\
    """)
  public String set_app_approval_flow_status(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "The flow_module_id") String flowId,
    @ToolArg(description = "2 = publish and enable; 3 = disable") int status,
    @ToolArg(description = "Latest revision returned by get/save/status") int revision
  ) {
    return approvalTool("set_app_approval_flow_status", appId, true, () -> {
      if (status != 2 && status != 3) throw new IllegalArgumentException("status must be 2 (enabled) or 3 (disabled)");
      return ApprovalFlowModel.encode(approvalFlows.status(appId, flowId, status == 2 ? "enabled" : "disabled", revision));
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
