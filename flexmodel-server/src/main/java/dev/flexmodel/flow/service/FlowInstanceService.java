package dev.flexmodel.flow.service;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.flow.dto.FlowInstanceListRequest;
import dev.flexmodel.flow.dto.FlowInstanceResponse;
import dev.flexmodel.flow.dto.result.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.codegen.entity.FlowInstanceMapping;
import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.flow.dto.model.FlowElement;
import dev.flexmodel.flow.repository.FlowDeploymentRepository;
import dev.flexmodel.flow.repository.FlowInstanceMappingRepository;
import dev.flexmodel.flow.repository.FlowInstanceRepository;
import dev.flexmodel.flow.repository.NodeInstanceRepository;
import dev.flexmodel.flow.common.FlowElementType;
import dev.flexmodel.flow.common.util.FlowModelUtil;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.common.utils.CollectionUtils;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.utils.StringUtils;

import java.util.*;

@ApplicationScoped
public class FlowInstanceService {

  protected static final Logger LOGGER = LoggerFactory.getLogger(FlowInstanceService.class);

  @Inject
  ProcessService processService;

  @Inject
  FlowDeploymentService flowDeploymentService;

  @Inject
  NodeInstanceRepository nodeInstanceRepository;

  @Inject
  FlowInstanceMappingRepository flowInstanceMappingRepository;

  @Inject
  FlowInstanceRepository flowInstanceRepository;

  @Inject
  FlowDeploymentRepository flowDeploymentRepository;

  /**
   * According to rootFlowInstanceId and commitNodeInstanceId, build and return NodeInstance stack.
   * When the subProcessInstance of each layer is executed, stack needs to pop up.
   * <p>
   * e.g.
   * <p>
   * rootNodeInstanceId
   * ^
   * ..................
   * ^
   * commitNodeInstanceId
   *
   * @param rootFlowInstanceId
   * @param commitNodeInstanceId
   * @return
   */
  public Stack<String> getNodeInstanceIdStack(String projectId, String rootFlowInstanceId, String commitNodeInstanceId) {
    if (StringUtils.isBlank(commitNodeInstanceId)) {
      LOGGER.info("getNodeInstanceId2RootStack result is empty.||rootFlowInstanceId={}||commitNodeInstanceId={}", rootFlowInstanceId, commitNodeInstanceId);
      return new Stack<>();
    }
    FlowInstanceTreeResult flowInstanceTreeResult = buildFlowInstanceTree(projectId, rootFlowInstanceId,
      nodeInstancePO -> nodeInstancePO.getNodeInstanceId().equals(commitNodeInstanceId));
    NodeInstancePOJO rightNodeInstance = flowInstanceTreeResult.getInterruptNodeInstancePOJO();
    Stack<String> stack = new Stack<>();
    while (rightNodeInstance != null) {
      stack.push(rightNodeInstance.getId());
      rightNodeInstance = rightNodeInstance.getFlowInstance().getBelongNodeInstance();
    }
    LOGGER.info("getNodeInstanceId2RootStack result.||rootFlowInstanceId={}||commitNodeInstanceId={}||result={}", rootFlowInstanceId, commitNodeInstanceId, stack);
    return stack;
  }

  /**
   * According to rootFlowInstanceId, get all subFlowInstanceIds from db.
   *
   * @param rootFlowInstanceId
   * @return
   */
  public Set<String> getAllSubFlowInstanceIds(String projectId, String rootFlowInstanceId) {
    FlowInstanceTreeResult flowInstanceTreeResult = buildFlowInstanceTree(projectId, rootFlowInstanceId, null);
    FlowInstancePOJO flowInstancePOJO = flowInstanceTreeResult.getRootFlowInstancePOJO();
    Set<String> result = getAllSubFlowInstanceIdsInternal(flowInstancePOJO);
    result.remove(rootFlowInstanceId);
    LOGGER.info("getAllSubFlowInstanceIds result.||rootFlowInstanceId={}||result={}", rootFlowInstanceId, result);
    return result;
  }

  private Set<String> getAllSubFlowInstanceIdsInternal(FlowInstancePOJO flowInstancePOJO) {
    Set<String> result = new TreeSet<>();
    if (flowInstancePOJO == null) {
      return result;
    }
    result.add(flowInstancePOJO.getId());
    List<NodeInstancePOJO> nodeInstanceList = flowInstancePOJO.getNodeInstanceList();
    for (NodeInstancePOJO nodeInstancePOJO : nodeInstanceList) {
      if (CollectionUtils.isEmpty(nodeInstancePOJO.getSubFlowInstanceList())) {
        continue;
      }
      FlowInstancePOJO subFlowInstancePOJO = nodeInstancePOJO.getSubFlowInstanceList().get(0);
      Set<String> subFlowInstanceResult = getAllSubFlowInstanceIdsInternal(subFlowInstancePOJO);
      result.addAll(subFlowInstanceResult);
    }
    return result;
  }


  /**
   * According to rootFlowInstanceId and nodeInstanceId,
   * Return the FlowInstanceId where the nodeInstanceId is located.
   *
   * @param rootFlowInstanceId
   * @param nodeInstanceId
   * @return
   */
  public String getFlowInstanceIdByRootFlowInstanceIdAndNodeInstanceId(String projectId, String rootFlowInstanceId, String nodeInstanceId) {
    if (StringUtils.isBlank(nodeInstanceId)) {
      return "";
    }
    FlowInstanceTreeResult flowInstanceTreeResult = buildFlowInstanceTree(projectId, rootFlowInstanceId,
      nodeInstancePO -> nodeInstancePO.getNodeInstanceId().equals(nodeInstanceId));
    NodeInstancePOJO rightNodeInstance = flowInstanceTreeResult.getInterruptNodeInstancePOJO();
    if (rightNodeInstance == null) {
      return "";
    }
    return rightNodeInstance.getFlowInstance().getId();
  }

  /**
   * According to rootFlowInstanceId and instanceDataId,
   * Return the FlowInstanceId where the instanceDataId is located.
   *
   * @param rootFlowInstanceId
   * @param instanceDataId
   * @return
   */
  public String getFlowInstanceIdByRootFlowInstanceIdAndInstanceDataId(String projectId, String rootFlowInstanceId, String instanceDataId) {
    if (StringUtils.isBlank(instanceDataId)) {
      return "";
    }
    FlowInstanceTreeResult flowInstanceTreeResult = buildFlowInstanceTree(projectId, rootFlowInstanceId,
      nodeInstancePO -> nodeInstancePO.getInstanceDataId().equals(instanceDataId));
    NodeInstancePOJO rightNodeInstance = flowInstanceTreeResult.getInterruptNodeInstancePOJO();
    if (rightNodeInstance == null) {
      return "";
    }
    return rightNodeInstance.getFlowInstance().getId();
  }

  // common : build a flowInstanceAndNodeInstance tree
  private FlowInstanceTreeResult buildFlowInstanceTree(String projectId, String rootFlowInstanceId, InterruptCondition interruptCondition) {
    FlowInstanceTreeResult flowInstanceTreeResult = new FlowInstanceTreeResult();
    FlowInstancePOJO flowInstance = new FlowInstancePOJO();
    flowInstance.setId(rootFlowInstanceId);
    flowInstanceTreeResult.setRootFlowInstancePOJO(flowInstance);

    FlowInstance rootFlowInstance = flowInstanceRepository.selectByFlowInstanceId(projectId, rootFlowInstanceId);
    FlowDeployment rootFlowDeployment = flowDeploymentRepository.findByDeployId(projectId, rootFlowInstance.getFlowDeployId());
    Map<String, FlowElement> rootFlowElementMap = FlowModelUtil.getFlowElementMap(rootFlowDeployment.getFlowModel());

    List<NodeInstance> nodeInstancePOList = nodeInstanceRepository.selectDescByFlowInstanceId(projectId, rootFlowInstanceId);
    for (NodeInstance nodeInstancePO : nodeInstancePOList) {
      NodeInstancePOJO nodeInstance = new NodeInstancePOJO();
      nodeInstance.setId(nodeInstancePO.getNodeInstanceId());
      nodeInstance.setFlowInstance(flowInstance);
      flowInstance.getNodeInstanceList().add(nodeInstance);

      if (interruptCondition != null && interruptCondition.match(nodeInstancePO)) {
        flowInstanceTreeResult.setInterruptNodeInstancePOJO(nodeInstance);
        return flowInstanceTreeResult;
      }

      int elementType = FlowModelUtil.getElementType(nodeInstancePO.getNodeKey(), rootFlowElementMap);
      if (elementType != FlowElementType.CALL_ACTIVITY) {
        continue;
      }
      List<FlowInstanceMapping> flowInstanceMappingPOS = flowInstanceMappingRepository.selectFlowInstanceMappingList(projectId, nodeInstancePO.getFlowInstanceId(), nodeInstancePO.getNodeInstanceId());
      for (FlowInstanceMapping flowInstanceMappingPO : flowInstanceMappingPOS) {
        FlowInstanceTreeResult subFlowInstanceTreeResult = buildFlowInstanceTree(projectId, flowInstanceMappingPO.getSubFlowInstanceId(), interruptCondition);
        FlowInstancePOJO subFlowInstance = subFlowInstanceTreeResult.getRootFlowInstancePOJO();
        subFlowInstance.setBelongNodeInstance(nodeInstance);
        nodeInstance.getSubFlowInstanceList().add(subFlowInstance);
        if (subFlowInstanceTreeResult.needInterrupt()) {
          flowInstanceTreeResult.setInterruptNodeInstancePOJO(subFlowInstanceTreeResult.getInterruptNodeInstancePOJO());
          return flowInstanceTreeResult;
        }
      }
    }
    return flowInstanceTreeResult;
  }

  public long count(String projectId, Predicate predicate) {
    return flowInstanceRepository.count(projectId, predicate);
  }

  public List<FlowInstance> find(String projectId, Predicate predicate, Integer page, Integer size) {
    return flowInstanceRepository.find(projectId, predicate, page, size);
  }

  public FlowInstance findById(String projectId, String flowInstanceId) {
    return flowInstanceRepository.selectByFlowInstanceId(projectId, flowInstanceId);
  }

  /**
   * 获取流程实例列表
   */
  public PageDTO<FlowInstanceResponse> findFlowInstanceList(FlowInstanceListRequest request) {
    LOGGER.info("获取流程实例列表，参数: {}", request);
    Predicate predicate = Expressions.TRUE;
    if (StringUtils.isNotBlank(request.getFlowInstanceId())) {
      predicate = predicate.and(Expressions.field(FlowInstance::getFlowInstanceId).eq(request.getFlowInstanceId()));
    }
    if (StringUtils.isNotBlank(request.getFlowModuleId())) {
      predicate = predicate.and(Expressions.field(FlowInstance::getFlowModuleId).eq(request.getFlowModuleId()));
    }
    if (StringUtils.isNotBlank(request.getFlowDeployId())) {
      predicate = predicate.and(Expressions.field(FlowInstance::getFlowDeployId).eq(request.getFlowDeployId()));
    }
    if (request.getStatus() != null) {
      predicate = predicate.and(Expressions.field(FlowInstance::getStatus).eq(request.getStatus()));
    }
    long count = count(request.getProjectId(), predicate);
    if (count == 0) {
      return PageDTO.empty();
    }
    List<FlowInstanceResponse> list = find(request.getProjectId(), predicate, request.getPage(), request.getSize()).stream()
      .map(entity -> {
        FlowInstanceResponse response = JsonUtils.convertValue(entity, FlowInstanceResponse.class);
        FlowDeployment flowDeployment = flowDeploymentService.findByFlowDeployId(request.getProjectId(), entity.getFlowDeployId());
        if (flowDeployment != null) {
          response.setFlowName(flowDeployment.getFlowName());
          response.setFlowKey(flowDeployment.getFlowKey());
        }
        return response;
      }).toList();
    return new PageDTO<>(list, count);
  }

  /**
   * 获取流程实例信息
   */
  public FlowInstance findFlowInstance(String projectId, String flowInstanceId) {
    LOGGER.info("获取流程实例信息: {}", flowInstanceId);
    if (StringUtils.isBlank(flowInstanceId)) {
      throw new IllegalArgumentException("流程实例ID不能为空");
    }
    return findById(projectId, flowInstanceId);
  }

  /**
   * 终止流程实例
   */
  public TerminateResult terminateProcess(String projectId, String flowInstanceId, boolean effectiveForSubFlowInstance) {
    LOGGER.info("终止流程实例: {}, 对子流程实例生效: {}", flowInstanceId, effectiveForSubFlowInstance);
    if (StringUtils.isBlank(flowInstanceId)) {
      throw new IllegalArgumentException("流程实例ID不能为空");
    }
    return processService.terminateProcess(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  private static class FlowInstancePOJO {
    private String id;
    private NodeInstancePOJO belongNodeInstance;
    private List<NodeInstancePOJO> nodeInstanceList = new ArrayList<>();

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public NodeInstancePOJO getBelongNodeInstance() {
      return belongNodeInstance;
    }

    public void setBelongNodeInstance(NodeInstancePOJO belongNodeInstance) {
      this.belongNodeInstance = belongNodeInstance;
    }

    public List<NodeInstancePOJO> getNodeInstanceList() {
      return nodeInstanceList;
    }

    public void setNodeInstanceList(List<NodeInstancePOJO> nodeInstanceList) {
      this.nodeInstanceList = nodeInstanceList;
    }
  }

  private static class NodeInstancePOJO {

    private String id;
    private FlowInstancePOJO flowInstance;
    private List<FlowInstancePOJO> subFlowInstanceList = new ArrayList<>();

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public FlowInstancePOJO getFlowInstance() {
      return flowInstance;
    }

    public void setFlowInstance(FlowInstancePOJO flowInstance) {
      this.flowInstance = flowInstance;
    }

    public List<FlowInstancePOJO> getSubFlowInstanceList() {
      return subFlowInstanceList;
    }

    public void setSubFlowInstanceList(List<FlowInstancePOJO> subFlowInstanceList) {
      this.subFlowInstanceList = subFlowInstanceList;
    }
  }

  private static class FlowInstanceTreeResult {
    private FlowInstancePOJO rootFlowInstancePOJO;
    private NodeInstancePOJO interruptNodeInstancePOJO;

    public FlowInstancePOJO getRootFlowInstancePOJO() {
      return rootFlowInstancePOJO;
    }

    public void setRootFlowInstancePOJO(FlowInstancePOJO rootFlowInstancePOJO) {
      this.rootFlowInstancePOJO = rootFlowInstancePOJO;
    }

    public NodeInstancePOJO getInterruptNodeInstancePOJO() {
      return interruptNodeInstancePOJO;
    }

    public void setInterruptNodeInstancePOJO(NodeInstancePOJO interruptNodeInstancePOJO) {
      this.interruptNodeInstancePOJO = interruptNodeInstancePOJO;
    }

    public boolean needInterrupt() {
      return interruptNodeInstancePOJO != null;
    }
  }

  /**
   * When build a flowInstanceAndNodeInstance tree,
   * we allow timely interruption to improve response.
   */
  private interface InterruptCondition {

    /**
     * Returns true when the condition is match
     *
     * @param nodeInstancePO
     * @return
     */
    boolean match(NodeInstance nodeInstancePO);
  }
}
