package dev.flexmodel.flow.service;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.flow.dto.model.FlowModel;
import dev.flexmodel.flow.repository.FlowDefinitionRepository;
import dev.flexmodel.flow.repository.FlowDeploymentRepository;
import dev.flexmodel.flow.repository.FlowInstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.Objects;

@ApplicationScoped
public class FlowTaskOwnership {

  @Inject
  FlowDefinitionRepository flowDefinitionRepository;
  @Inject
  FlowDeploymentRepository flowDeploymentRepository;
  @Inject
  FlowInstanceRepository flowInstanceRepository;

  public void module(String projectId, String moduleId) {
    var definition = flowDefinitionRepository.selectByModuleId(projectId, moduleId);
    if (definition != null && definition.getFlowModel() != null && !definition.getFlowModel().isBlank())
      checkModel(definition.getFlowModel(), null);
  }

  public void deployment(String projectId, String deploymentId, String moduleId) {
    FlowDeployment deployment = deploymentId != null && !deploymentId.isBlank()
      ? flowDeploymentRepository.findByDeployId(projectId, deploymentId)
      : flowDeploymentRepository.findRecentByFlowModuleId(projectId, moduleId);
    check(deployment, null);
  }

  public void instance(String projectId, String instanceId, String nodeKey) {
    FlowInstance instance = flowInstanceRepository.selectByFlowInstanceId(projectId, instanceId);
    if (instance == null) return;
    check(flowDeploymentRepository.findByDeployId(projectId, instance.getFlowDeployId()), nodeKey);
  }

  private void check(FlowDeployment deployment, String nodeKey) {
    if (deployment == null) return;
    checkModel(deployment.getFlowModel(), nodeKey);
  }

  private void checkModel(String json, String nodeKey) {
    FlowModel model = JsonUtils.parseToObject(json, FlowModel.class);
    String owner = model.getFlowElementList().stream()
      .map(element -> Objects.toString(element.getProperties().get("handlerOwner"), ""))
      .filter(value -> !value.isEmpty()).findFirst().orElse("");
    String caller = ExternalTaskAccess.owner();
    if (!owner.isEmpty() && !owner.equals(caller)) {
      String nodeOwner = model.getFlowElementList().stream().filter(element -> Objects.equals(element.getKey(), nodeKey))
        .map(element -> Objects.toString(element.getProperties().get("handler"), "")).findFirst().orElse("");
      if (nodeOwner.isEmpty() || !nodeOwner.equals(caller))
        throw new ForbiddenException("该流程由外部处理器托管，请从业务入口操作");
    }
  }
}
