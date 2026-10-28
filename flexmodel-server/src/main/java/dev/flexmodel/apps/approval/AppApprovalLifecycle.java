package dev.flexmodel.apps.approval;

import dev.flexmodel.common.ProjectDeletionGuard;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.flexmodel.apps.approval.AppApprovalJson.config;
import static dev.flexmodel.apps.approval.AppApprovalJson.flatten;
import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class AppApprovalLifecycle implements ProjectDeletionGuard {
  @Inject
  AppApprovalInstanceRepository appApprovalInstanceRepository;
  @Inject
  AppApprovalFlowDeploymentRepository appApprovalFlowDeploymentRepository;
  @Inject
  AppApprovalFlowService appApprovalFlowService;

  private boolean active(Map<String, Object> run) {
    return !Set.of("APPROVED", "REJECTED", "TERMINATED").contains(text(run, "status"));
  }

  @Override
  public void beforeDelete(String appId) {
    if (appApprovalInstanceRepository.findAll(appId).stream().anyMatch(this::active))
      throw new ValidationException("应用仍有未结束的审批，请先处理或终止");
  }

  public void beforeDeleteForm(String appId, String form) {
    List<Map<String, Object>> definitions = new ArrayList<>();
    for (var flow : appApprovalFlowService.list(appId))
      if ("enabled".equals(text(flow, "status"))) definitions.add(flow);
    for (var run : appApprovalInstanceRepository.findAll(appId))
      if (active(run))
        definitions.add(appApprovalFlowDeploymentRepository.deployedFlow(appId, text(run, "flow_deploy_id")));
    for (var flow : definitions)
      if (text(object(flow.get("form")), "name").equals(form) || flatten(objects(object(flow.get("definition")).get("nodes"))).stream().anyMatch(node -> text(object(config(node).get("form")), "name").equals(form)))
        throw new ValidationException("表单正在被已启用或运行中的流程使用，请先停用流程并结束相关审批");
  }
}
