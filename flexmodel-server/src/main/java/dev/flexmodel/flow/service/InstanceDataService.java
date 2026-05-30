package dev.flexmodel.flow.service;

import dev.flexmodel.flow.repository.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import dev.flexmodel.codegen.entity.*;
import dev.flexmodel.flow.dto.model.FlowElement;
import dev.flexmodel.flow.common.FlowElementType;
import dev.flexmodel.flow.common.util.FlowModelUtil;

import java.util.Map;

@Singleton
public class InstanceDataService {

  @Inject
  InstanceDataRepository instanceDataRepository;

  @Inject
  FlowInstanceRepository flowInstanceRepository;

  @Inject
  FlowDeploymentRepository flowDeploymentRepository;

  @Inject
  NodeInstanceRepository nodeInstanceRepository;

  @Inject
  FlowInstanceMappingRepository flowInstanceMappingRepository;

  @Inject
  FlowInstanceService flowInstanceService;

  public InstanceData select(String projectId, String flowInstanceId, String instanceDataId, boolean effectiveForSubFlowInstance) {
    InstanceData instanceData = instanceDataRepository.select(projectId, flowInstanceId, instanceDataId);
    if (!effectiveForSubFlowInstance) {
      return instanceData;
    }
    if (instanceData != null) {
      return instanceData;
    }
    String subFlowInstanceId = flowInstanceService.getFlowInstanceIdByRootFlowInstanceIdAndInstanceDataId(projectId, flowInstanceId, instanceDataId);
    return instanceDataRepository.select(projectId, subFlowInstanceId, instanceDataId);
  }

  public InstanceData select(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    InstanceData instanceData = instanceDataRepository.selectRecentOne(projectId, flowInstanceId);
    if (!effectiveForSubFlowInstance) {
      return instanceData;
    }
    FlowInstance flowInstance = flowInstanceRepository.selectByFlowInstanceId(projectId, flowInstanceId);
    FlowDeployment flowDeployment = flowDeploymentRepository.findByDeployId(projectId, flowInstance.getFlowDeployId());
    Map<String, FlowElement> flowElementMap = FlowModelUtil.getFlowElementMap(flowDeployment.getFlowModel());

    NodeInstance nodeInstance = nodeInstanceRepository.selectRecentOne(projectId, flowInstanceId);
    int elementType = FlowModelUtil.getElementType(nodeInstance.getNodeKey(), flowElementMap);
    if (elementType != FlowElementType.CALL_ACTIVITY) {
      return instanceDataRepository.selectRecentOne(projectId, flowInstanceId);
    } else {
      FlowInstanceMapping flowInstanceMapping = flowInstanceMappingRepository.selectFlowInstanceMapping(projectId, flowInstanceId, nodeInstance.getNodeInstanceId());
      return select(projectId, flowInstanceMapping.getSubFlowInstanceId(), true);
    }
  }
}
