package dev.flexmodel.apps.approval;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.app.AppService;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.flow.common.FlowDefinitionStatus;
import dev.flexmodel.flow.dto.param.CreateFlowParam;
import dev.flexmodel.flow.dto.param.DeployFlowParam;
import dev.flexmodel.flow.dto.param.UpdateFlowParam;
import dev.flexmodel.flow.dto.result.CommonResult;
import dev.flexmodel.flow.service.ExternalTaskAccess;
import dev.flexmodel.flow.service.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.apps.approval.AppApprovalJson.config;
import static dev.flexmodel.apps.approval.AppApprovalJson.flatten;
import static dev.flexmodel.common.Values.*;

/**
 * Approval adds business validation and metadata to the existing Flow lifecycle.
 */
@ApplicationScoped
public class AppApprovalFlowService {
  @Inject
  AppApprovalFlowDefinitionRepository appApprovalFlowDefinitionRepository;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  AppApprovalCompiler compiler;
  @Inject
  ProcessService processService;
  @Inject
  SessionContext current;
  @Inject
  AppService appService;

  public List<Map<String, Object>> list(String appId) {
    appService.findApp(appId);
    return appApprovalFlowDefinitionRepository.findAllActive(appId).stream()
      .filter(AppApprovalFlowModel::managed).map(row -> response(appId, row))
      .sorted(Comparator.comparing((Map<String, Object> value) -> text(value, "updatedAt")).reversed()).toList();
  }

  public Map<String, Object> get(String appId, String id) {
    return response(appId, managedDefinition(appId, id));
  }

  private Map<String, Object> managedDefinition(String appId, String id) {
    var row = appApprovalFlowDefinitionRepository.get(appId, id);
    if (!AppApprovalFlowModel.managed(row)) throw new NotFoundException("审批流程不存在");
    return row;
  }

  public Map<String, Object> save(String appId, String id, Map<String, Object> body) {
    appService.findApp(appId);
    return transactions.executePlatform(appId, () -> {
      if (id != null) appApprovalFlowDefinitionRepository.lock(appId, id);
      var old = id == null ? null : managedDefinition(appId, id);
      int revision = old == null ? 0 : revision(old);
      if (old != null && revision != ((Number) body.getOrDefault("revision", -1)).intValue())
        throw new ClientErrorException("流程已被其他人修改，请刷新后重试", 409);
      if (text(body, "name").isBlank() || text(body, "name").length() > 120)
        throw new ValidationException("流程名称不能为空且不超过 120 字符");
      if (text(body, "description").length() > 512) throw new ValidationException("流程备注不能超过 512 字符");
      if (!(object(body.get("definition")).get("nodes") instanceof List<?>))
        throw new ValidationException("流程节点树无效");
      var draft = new LinkedHashMap<>(body);
      String flowKey = old == null ? text(body, "flowKey") : text(old, "flow_key");
      if (flowKey.isBlank()) flowKey = "approval_" + UUID.randomUUID();
      if (!flowKey.matches("[A-Za-z0-9_-]{1,120}")) throw new ValidationException("流程标识无效");
      final String key = flowKey;
      if (list(appId).stream().anyMatch(flow -> !Objects.equals(id, text(flow, "id")) && key.equals(text(flow, "flowKey"))))
        throw new ValidationException("流程标识已存在");
      draft.put("flowKey", flowKey);
      draft.put("status", "draft");
      if (text(AppApprovalFlowModel.encode(draft), "flow_model").length() > 900000 || flatten(objects(object(body.get("definition")).get("nodes"))).size() > 200)
        throw new ValidationException("流程配置过大，最多 200 个节点");
      return ExternalTaskAccess.as("approval-flow", () -> {
        String moduleId = id;
        if (old == null) {
          var create = new CreateFlowParam(appId, current.getUserId());
          create.setFlowKey(key);
          create.setFlowName(text(draft, "name"));
          create.setRemark(text(draft, "description"));
          var created = processService.createFlow(create);
          success(created);
          moduleId = created.getFlowModuleId();
        }
        draft.put("id", moduleId);
        update(appId, moduleId, draft, Map.of("revision", revision + 1));
        if (old != null) preserveCreated(appId, moduleId, old);
        return get(appId, moduleId);
      });
    });
  }

  public List<Map<String, String>> validate(String appId, Map<String, Object> flow) {
    List<Map<String, Object>> all = new ArrayList<>(list(appId));
    all.removeIf(item -> text(item, "id").equals(text(flow, "id")));
    all.add(flow);
    return compiler.validate(appId, flow, all);
  }

  public Map<String, Object> status(String appId, String id, String state, int revision) {
    return transactions.executePlatform(appId, () -> {
      appApprovalFlowDefinitionRepository.lock(appId, id);
      var row = managedDefinition(appId, id);
      var flow = response(appId, row);
      if (revision(row) != revision) throw new ClientErrorException("流程版本已变化，请刷新", 409);
      if (!Set.of("enabled", "disabled").contains(state)) throw new ValidationException("流程状态无效");
      Map<String, Object> metadata = new LinkedHashMap<>(AppApprovalFlowModel.metadata(row));
      metadata.put("revision", revision + 1);
      if (state.equals("enabled")) {
        var issues = validate(appId, flow);
        if (!issues.isEmpty()) throw new ValidationException(issues.getFirst().get("message"));
        Map<String, Object> children = new LinkedHashMap<>();
        for (var node : flatten(objects(object(flow.get("definition")).get("nodes"))))
          if (text(node, "type").equals("subflow")) {
            var child = managedDefinition(appId, text(config(node), "flowId"));
            String deploymentId = text(AppApprovalFlowModel.metadata(child), "deploymentId");
            if (status(child) != 2 || deploymentId.isEmpty()) throw new ValidationException("请先发布并启用子流程");
            children.put(text(node, "id"), deploymentId);
          }
        String deploymentId = ExternalTaskAccess.as("approval-flow", () -> {
          update(appId, id, flow, Map.of("revision", revision + 1, "children", children));
          var deploy = new DeployFlowParam(appId, current.getUserId());
          deploy.setFlowModuleId(id);
          var deployed = processService.deployFlow(deploy);
          success(deployed);
          return deployed.getFlowDeployId();
        });
        metadata = new LinkedHashMap<>(Map.of("revision", revision + 1, "deploymentId", deploymentId));
      }
      String model = JsonUtils.toJsonString(compiler.compile(flow, metadata));
      appApprovalFlowDefinitionRepository.update(appId, id, Map.of("flow_model", model, "status", state.equals("disabled") ? FlowDefinitionStatus.DISABLED : FlowDefinitionStatus.EDITING,
        "updated_by", current.getUserId(), "updated_at", LocalDateTime.now()));
      preserveCreated(appId, id, row);
      return get(appId, id);
    });
  }

  public void delete(String appId, String id) {
    transactions.executePlatform(appId, () -> {
      appApprovalFlowDefinitionRepository.lock(appId, id);
      managedDefinition(appId, id);
      boolean referenced = list(appId).stream().filter(flow -> !id.equals(text(flow, "id"))).anyMatch(flow -> flatten(objects(object(flow.get("definition")).get("nodes"))).stream().anyMatch(node -> "subflow".equals(text(node, "type")) && id.equals(text(config(node), "flowId"))));
      if (referenced) throw new ValidationException("该流程被其他流程引用，请先解除关联");
      ExternalTaskAccess.as("approval-flow", () -> {
        processService.deleteFlow(appId, id);
        return null;
      });
      return null;
    });
  }

  private void update(String appId, String id, Map<String, Object> flow, Map<String, Object> metadata) {
    var update = new UpdateFlowParam(appId, current.getUserId());
    update.setFlowModuleId(id);
    update.setFlowKey(text(flow, "flowKey"));
    update.setFlowName(text(flow, "name"));
    update.setRemark(text(flow, "description"));
    update.setFlowModel(JsonUtils.toJsonString(compiler.compile(flow, metadata)));
    success(processService.updateFlow(update));
  }

  private void preserveCreated(String appId, String id, Map<String, Object> old) {
    Map<String, Object> created = new LinkedHashMap<>();
    for (String key : List.of("created_by", "created_at")) if (old.get(key) != null) created.put(key, old.get(key));
    if (!created.isEmpty()) appApprovalFlowDefinitionRepository.update(appId, id, created);
  }

  private int revision(Map<String, Object> row) {
    return ((Number) AppApprovalFlowModel.metadata(row).getOrDefault("revision", 1)).intValue();
  }

  private int status(Map<String, Object> row) {
    if (row.get("status") instanceof Number state && state.intValue() == FlowDefinitionStatus.DISABLED) return 3;
    return text(AppApprovalFlowModel.metadata(row), "deploymentId").isEmpty() ? 1 : 2;
  }

  private Map<String, Object> response(String appId, Map<String, Object> row) {
    var view = new LinkedHashMap<>(row);
    view.put("status", status(row));
    view.put("revision", revision(row));
    var flow = AppApprovalFlowModel.decode(view);
    flow.put("appId", appId);
    flow.put("is_deleted", Boolean.TRUE.equals(row.get("is_deleted")) ? 1 : 0);
    return flow;
  }

  public static void success(CommonResult result) {
    if (result == null || !ErrorEnum.isSuccess(result.getErrCode()))
      throw new ValidationException(result == null ? "流程引擎未返回结果" : result.getErrMsg());
  }
}
