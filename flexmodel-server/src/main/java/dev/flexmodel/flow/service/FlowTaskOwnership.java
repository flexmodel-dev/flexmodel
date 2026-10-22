package dev.flexmodel.flow.service;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.flow.dto.model.FlowModel;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;

import java.util.Objects;

import static dev.flexmodel.codegen.System.*;

@ApplicationScoped
public class FlowTaskOwnership extends AbstractRepository {

  public void module(String projectId, String moduleId) {
    try (Session session = getProjectSession(projectId)) {
      var definition = session.dsl().selectFrom(dev.flexmodel.codegen.entity.FlowDefinition.class)
        .where(flowDefinition.flowModuleId.eq(moduleId)).executeOne();
      if (definition != null && definition.getFlowModel() != null && !definition.getFlowModel().isBlank())
        checkModel(definition.getFlowModel(), null);
    }
  }

  public void deployment(String projectId, String deploymentId, String moduleId) {
    try (Session session = getProjectSession(projectId)) {
      var query = session.dsl().selectFrom(FlowDeployment.class);
      FlowDeployment deployment = deploymentId != null && !deploymentId.isBlank()
        ? query.where(flowDeployment.flowDeployId.eq(deploymentId)).executeOne()
        : query.where(flowDeployment.flowModuleId.eq(moduleId)).orderByDesc(flowDeployment.id).limit(1).executeOne();
      check(deployment, null);
    }
  }

  public void instance(String projectId, String instanceId, String nodeKey) {
    try (Session session = getProjectSession(projectId)) {
      FlowInstance instance = session.dsl().selectFrom(FlowInstance.class).where(flowInstance.flowInstanceId.eq(instanceId)).executeOne();
      if (instance == null) return;
      FlowDeployment deployment = session.dsl().selectFrom(FlowDeployment.class)
        .where(flowDeployment.flowDeployId.eq(instance.getFlowDeployId())).executeOne();
      check(deployment, nodeKey);
    }
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
