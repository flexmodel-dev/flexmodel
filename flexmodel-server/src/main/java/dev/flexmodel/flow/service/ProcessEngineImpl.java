package dev.flexmodel.flow.service;

import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.utils.StringUtils;
import dev.flexmodel.flow.dto.param.*;
import dev.flexmodel.flow.dto.result.*;
import dev.flexmodel.flow.processor.DefinitionProcessor;
import dev.flexmodel.flow.processor.RuntimeProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProcessEngineImpl implements ProcessService {

  @Inject
  DefinitionProcessor definitionProcessor;

  @Inject
  RuntimeProcessor runtimeProcessor;

  @Inject
  SessionContext sessionContext;

  @Inject
  ProjectUnitOfWork unitOfWork;
  @Inject
  FlowTaskOwnership taskOwnership;

  @Override
  public CreateFlowResult createFlow(CreateFlowParam createFlowParam) {
    createFlowParam.setCreatedBy(sessionContext.getUserId());
    createFlowParam.setUpdatedBy(sessionContext.getUserId());
    return unitOfWork.execute(createFlowParam.getProjectId(), () -> definitionProcessor.create(createFlowParam));
  }

  @Override
  public UpdateFlowResult updateFlow(UpdateFlowParam updateFlowParam) {
    updateFlowParam.setCreatedBy(sessionContext.getUserId());
    updateFlowParam.setUpdatedBy(sessionContext.getUserId());
    return unitOfWork.execute(updateFlowParam.getProjectId(), () -> {
      dev.flexmodel.common.ProjectUnitOfWork.lock(updateFlowParam.getProjectId(), "f_em_flow_definition", "flow_module_id", updateFlowParam.getFlowModuleId());
      taskOwnership.module(updateFlowParam.getProjectId(), updateFlowParam.getFlowModuleId());
      return definitionProcessor.update(updateFlowParam);
    });
  }

  @Override
  public DeployFlowResult deployFlow(DeployFlowParam deployFlowParam) {
    deployFlowParam.setCreatedBy(sessionContext.getUserId());
    deployFlowParam.setUpdatedBy(sessionContext.getUserId());
    return unitOfWork.execute(deployFlowParam.getProjectId(), () -> {
      dev.flexmodel.common.ProjectUnitOfWork.lock(deployFlowParam.getProjectId(), "f_em_flow_definition", "flow_module_id", deployFlowParam.getFlowModuleId());
      taskOwnership.module(deployFlowParam.getProjectId(), deployFlowParam.getFlowModuleId());
      return definitionProcessor.deploy(deployFlowParam);
    });
  }

  @Override
  public FlowModuleResult getFlowModule(GetFlowModuleParam getFlowModuleParam) {
    return definitionProcessor.getFlowModule(getFlowModuleParam);
  }

  @Override
  public StartProcessResult startProcess(StartProcessParam startProcessParam) {
    if (StringUtils.isBlank(startProcessParam.getInitiator())) {
      startProcessParam.setInitiator(sessionContext.getUserId());
    }
    return unitOfWork.execute(startProcessParam.getProjectId(), () -> {
      taskOwnership.deployment(startProcessParam.getProjectId(), startProcessParam.getFlowDeployId(), startProcessParam.getFlowModuleId());
      return runtimeProcessor.startProcess(startProcessParam);
    });
  }

  @Override
  public CommitTaskResult commitTask(CommitTaskParam commitTaskParam) {
    return unitOfWork.execute(commitTaskParam.getProjectId(), () -> {
      dev.flexmodel.common.ProjectUnitOfWork.lock(commitTaskParam.getProjectId(), "f_ei_flow_instance", "flow_instance_id", commitTaskParam.getFlowInstanceId());
      taskOwnership.instance(commitTaskParam.getProjectId(), commitTaskParam.getFlowInstanceId(), null);
      runtimeProcessor.checkIsSubFlowInstance(commitTaskParam.getProjectId(), commitTaskParam.getFlowInstanceId());
      return runtimeProcessor.commit(commitTaskParam);
    });
  }

  @Override
  public RollbackTaskResult rollbackTask(RollbackTaskParam rollbackTaskParam) {
    return unitOfWork.execute(rollbackTaskParam.getProjectId(), () -> {
      dev.flexmodel.common.ProjectUnitOfWork.lock(rollbackTaskParam.getProjectId(), "f_ei_flow_instance", "flow_instance_id", rollbackTaskParam.getFlowInstanceId());
      taskOwnership.instance(rollbackTaskParam.getProjectId(), rollbackTaskParam.getFlowInstanceId(), null);
      runtimeProcessor.checkIsSubFlowInstance(rollbackTaskParam.getProjectId(), rollbackTaskParam.getFlowInstanceId());
      return runtimeProcessor.rollback(rollbackTaskParam);
    });
  }

  @Override
  public TerminateResult terminateProcess(String projectId, String flowInstanceId) {
    return terminateProcess(projectId, flowInstanceId, true);
  }

  @Override
  public TerminateResult terminateProcess(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    return unitOfWork.execute(projectId, () -> {
      dev.flexmodel.common.ProjectUnitOfWork.lock(projectId, "f_ei_flow_instance", "flow_instance_id", flowInstanceId);
      taskOwnership.instance(projectId, flowInstanceId, null);
      runtimeProcessor.checkIsSubFlowInstance(projectId, flowInstanceId);
      return runtimeProcessor.terminateProcess(projectId, flowInstanceId, effectiveForSubFlowInstance);
    });
  }

  @Override
  public NodeInstanceListResult getHistoryUserTaskList(String projectId, String flowInstanceId) {
    return runtimeProcessor.getHistoryUserTaskList(projectId, flowInstanceId, true);
  }

  @Override
  public NodeInstanceListResult getHistoryUserTaskList(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    return runtimeProcessor.getHistoryUserTaskList(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  @Override
  public ElementInstanceListResult getHistoryElementList(String projectId, String flowInstanceId) {
    return runtimeProcessor.getHistoryElementList(projectId, flowInstanceId, true);
  }

  @Override
  public ElementInstanceListResult getHistoryElementList(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    return runtimeProcessor.getHistoryElementList(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  @Override
  public InstanceDataListResult getInstanceData(String projectId, String flowInstanceId) {
    return runtimeProcessor.getInstanceData(projectId, flowInstanceId, true);
  }

  @Override
  public InstanceDataListResult getInstanceData(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    return runtimeProcessor.getInstanceData(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  @Override
  public NodeInstanceResult getNodeInstance(String projectId, String flowInstanceId, String nodeInstanceId) {
    return runtimeProcessor.getNodeInstance(projectId, flowInstanceId, nodeInstanceId, true);
  }

  @Override
  public NodeInstanceResult getNodeInstance(String projectId, String flowInstanceId, String nodeInstanceId, boolean effectiveForSubFlowInstance) {
    return runtimeProcessor.getNodeInstance(projectId, flowInstanceId, nodeInstanceId, effectiveForSubFlowInstance);
  }

  @Override
  public FlowInstanceResult getFlowInstance(String projectId, String flowInstanceId) {
    return runtimeProcessor.getFlowInstance(projectId, flowInstanceId);
  }

  @Override
  public void deleteFlow(String projectId, String flowModuleId) {
    unitOfWork.execute(projectId, () -> {
      dev.flexmodel.common.ProjectUnitOfWork.lock(projectId, "f_em_flow_definition", "flow_module_id", flowModuleId);
      taskOwnership.module(projectId, flowModuleId);
      definitionProcessor.delete(projectId, flowModuleId);
      return null;
    });
  }

  @Override
  public InstanceDataListResult getInstanceData(String projectId, String flowInstanceId, String instanceDataId) {
    return runtimeProcessor.getInstanceData(projectId, flowInstanceId, instanceDataId, true);
  }

  @Override
  public InstanceDataListResult getInstanceData(String projectId, String flowInstanceId, String instanceDataId, boolean effectiveForSubFlowInstance) {
    return runtimeProcessor.getInstanceData(projectId, flowInstanceId, instanceDataId, effectiveForSubFlowInstance);
  }
}
