package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.AppFormService;
import dev.flexmodel.apps.AppMenuService;
import dev.flexmodel.apps.AppRecordService;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.dto.AppFormCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRequest;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppMenuSaveRequest;
import dev.flexmodel.apps.dto.AppMenuTargetRequest;
import dev.flexmodel.apps.dto.AppResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：Flexmodel Apps
 * 将应用、表单、布局、菜单和记录能力按 AI 可编排的固定顺序开放。
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
    primaryColor allows indigo, blue, green, amber, rose, or black.\
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
    The response returns the final form contract and server-generated field names.\
    """)
  public String create_app_form(
    @ToolArg(description = "The appId returned by create_app") String appId,
    @ToolArg(description = "Human-readable form label") String label,
    @ToolArg(description = "Form name matching ^[a-z][a-z0-9_]{0,63}$") String name,
    @ToolArg(description = "Form description") String description,
    @ToolArg(description = "Whether to create the built-in name field") boolean createDisplayField,
    @ToolArg(description = """
      JSON array string of fields, for example: \
      [{"label":"Amount","type":"DECIMAL","required":true}]. \
      Each item may contain label, type, required, options, and referenceForm.\
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
    Create an app menu entry. This is step 4 of the app creation flow. \
    menuType allows GROUP or ITEM. GROUP must not bind a target. ITEM must bind \
    targetType BUILTIN or FORM. Use targetId=formName when binding a form. \
    iconName allows Home, User, TrendingUp, ShoppingCart, PieChart, Folder, \
    BookUser, ClipboardCheck, Shapes, Tags, Boxes, or Banknote.\
    """)
  public String create_app_menu(
    @ToolArg(description = "The app ID") String appId,
    @ToolArg(description = "Menu type: GROUP or ITEM") String menuType,
    @ToolArg(description = "Menu display name") String name,
    @ToolArg(description = "Menu icon name") String iconName,
    @ToolArg(description = "Parent menu ID, only for a nested ITEM") String parentId,
    @ToolArg(description = "Target type: BUILTIN or FORM, required for ITEM") String targetType,
    @ToolArg(description = "BUILTIN page ID or form name, required for ITEM") String targetId
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

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
