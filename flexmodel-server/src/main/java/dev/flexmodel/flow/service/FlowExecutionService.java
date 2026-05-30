package dev.flexmodel.flow.service;

import dev.flexmodel.flow.dto.bo.ElementInstance;
import dev.flexmodel.flow.dto.bo.NodeInstance;
import dev.flexmodel.flow.dto.param.*;
import dev.flexmodel.flow.dto.result.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 流程执行服务
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FlowExecutionService {

  @Inject
  ProcessService processService;

  /**
   * 启动流程实例
   */
  public StartProcessResult startProcess(StartProcessParam startProcessParam) {
    log.info("启动流程实例，流程模块ID: {}, 流程部署ID: {}",
      startProcessParam.getFlowModuleId(), startProcessParam.getFlowDeployId());
    return processService.startProcess(startProcessParam);
  }

  /**
   * 提交任务
   */
  public CommitTaskResult commitTask(CommitTaskParam commitTaskParam) {
    log.info("提交任务，流程实例ID: {}, 任务实例ID: {}",
      commitTaskParam.getFlowInstanceId(), commitTaskParam.getTaskInstanceId());
    return processService.commitTask(commitTaskParam);
  }

  /**
   * 回滚任务
   */
  public RollbackTaskResult rollbackTask(RollbackTaskParam rollbackTaskParam) {
    log.info("回滚任务，流程实例ID: {}, 任务实例ID: {}",
      rollbackTaskParam.getFlowInstanceId(), rollbackTaskParam.getTaskInstanceId());
    return processService.rollbackTask(rollbackTaskParam);
  }

  /**
   * 获取流程实例历史用户任务列表
   */
  public List<NodeInstance> getHistoryUserTaskList(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    return processService.getHistoryUserTaskList(projectId, flowInstanceId, effectiveForSubFlowInstance).getNodeInstanceList();
  }

  /**
   * 获取流程实例历史元素列表
   */
  public List<ElementInstance> getHistoryElementList(String projectId, String flowInstanceId) {
    return processService.getHistoryElementList(projectId, flowInstanceId).getElementInstanceList();
  }

  /**
   * 获取流程实例元素实例数据
   */
  public InstanceDataListResult getInstanceData(String projectId, String flowInstanceId, String instanceDataId) {
    log.info("获取元素实例数据，流程实例ID: {}, 实例数据ID: {}", flowInstanceId, instanceDataId);
    return processService.getInstanceData(projectId, flowInstanceId, instanceDataId);
  }

}
