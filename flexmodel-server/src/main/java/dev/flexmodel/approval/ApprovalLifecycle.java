package dev.flexmodel.approval;

import dev.flexmodel.codegen.entity.ApprovalInstance;
import dev.flexmodel.common.ProjectDeletionGuard;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class ApprovalLifecycle implements ProjectDeletionGuard {
  @Inject
  ApprovalInstanceRepository approvalInstanceRepository;
  @Inject
  ApprovalFlowDeploymentRepository approvalFlowDeploymentRepository;
  @Inject
  ApprovalFlowService approvalFlowService;

  private boolean active(Map<String, Object> run) {
    return !Set.of("APPROVED", "REJECTED", "TERMINATED").contains(text(run, "status"));
  }

  @Override
  public void beforeDelete(String appId) {
    if (approvalInstanceRepository.findAll(appId).stream().anyMatch(this::active))
      throw new ValidationException("应用仍有未结束的审批，请先处理或终止");
  }

  public void beforeDeleteForm(String appId, String form) {
    List<Map<String, Object>> definitions = new ArrayList<>();
    for (var flow : approvalFlowService.list(appId)) if ("enabled".equals(text(flow, "status"))) definitions.add(flow);
    for (var run : approvalInstanceRepository.findAll(appId))
      if (active(run))
        definitions.add(approvalFlowDeploymentRepository.deployedFlow(appId, text(run, "flow_deploy_id")));
    for (var flow : definitions)
      if (text(object(flow.get("form")), "name").equals(form) || flatten(objects(object(flow.get("definition")).get("nodes"))).stream().anyMatch(node -> text(object(config(node).get("form")), "name").equals(form)))
        throw new ValidationException("表单正在被已启用或运行中的流程使用，请先停用流程并结束相关审批");
  }
}
