package dev.flexmodel.flow.service;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.flow.dto.*;
import dev.flexmodel.flow.dto.param.*;
import dev.flexmodel.flow.dto.result.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.flow.repository.FlowDefinitionRepository;
import dev.flexmodel.flow.common.FlowDeploymentStatus;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义服务
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FlowDefinitionService {

  @Inject
  FlowDefinitionRepository flowDefinitionRepository;

  @Inject
  ProcessService processService;

  @Inject
  FlowDeploymentService flowDeploymentService;

  public List<FlowDefinition> find(String projectId, Predicate filter, Integer page, Integer size) {
    return flowDefinitionRepository.find(projectId, filter, page, size);
  }

  public long count(String projectId, Predicate filter) {
    return flowDefinitionRepository.count(projectId, filter);
  }

  /**
   * 获取流程模块列表
   */
  public PageDTO<FlowModuleResponse> findFlowModuleList(FlowModuleListRequest request) {
    log.info("获取流程模块列表，参数: {}", request);
    Predicate predicate = Expressions.field(FlowDefinition::getIsDeleted).eq(false);
    if (StringUtils.isNotBlank(request.getFlowModuleId())) {
      predicate = predicate.and(Expressions.field(FlowDefinition::getFlowModuleId).eq(request.getFlowModuleId()));
    }
    if (StringUtils.isNotBlank(request.getFlowKey())) {
      predicate = predicate.and(Expressions.field(FlowDefinition::getFlowKey).eq(request.getFlowKey()));
    }
    if (StringUtils.isNotBlank(request.getFlowName())) {
      predicate = predicate.and(Expressions.field(FlowDefinition::getFlowName).contains(request.getFlowName()));
    }
    long count = count(request.getProjectId(), predicate);
    if (count == 0) {
      return PageDTO.empty();
    }

    List<FlowDefinition> list = find(request.getProjectId(), predicate, request.getPage(), request.getSize());
    List<FlowModuleResponse> flowModuleList = new ArrayList<>();
    for (FlowDefinition entity : list) {
      FlowModuleResponse response = new FlowModuleResponse(entity);
      long deploymentCount = flowDeploymentService.count(request.getProjectId(), Expressions.field(
          FlowDeployment::getFlowModuleId).eq(entity.getFlowModuleId())
        .and(Expressions.field(FlowDeployment::getStatus).eq(FlowDeploymentStatus.DEPLOYED))
      );
      if (deploymentCount >= 1) {
        //4 已发布
        response.setStatus(FlowModuleStatusEnum.PUBLISHED.getValue());
      }
      flowModuleList.add(response);
    }
    return new PageDTO<>(flowModuleList, count);
  }

  /**
   * 创建流程
   */
  public CreateFlowResult createFlow(CreateFlowParam createFlowParam) {
    log.info("创建流程: {}", createFlowParam.getFlowName());
    return processService.createFlow(createFlowParam);
  }

  /**
   * 部署流程
   */
  public DeployFlowResult deployFlow(DeployFlowParam deployFlowParam) {
    log.info("部署流程，流程模块ID: {}", deployFlowParam.getFlowModuleId());
    return processService.deployFlow(deployFlowParam);
  }

  /**
   * 更新流程
   */
  public UpdateFlowResult updateFlow(UpdateFlowParam updateFlowParam) {
    log.info("更新流程，流程模块ID: {}", updateFlowParam.getFlowModuleId());
    return processService.updateFlow(updateFlowParam);
  }

  /**
   * 删除流程
   */
  public void deleteFlow(String projectId, String flowModuleId) {
    log.info("删除流程，流程模块ID: {}", flowModuleId);
    processService.deleteFlow(projectId, flowModuleId);
  }

  /**
   * 获取流程模块信息
   */
  public FlowModuleResult getFlowModule(GetFlowModuleParam getFlowModuleParam) {
    log.info("获取流程模块信息，流程模块ID: {}, 流程部署ID: {}",
      getFlowModuleParam.getFlowModuleId(), getFlowModuleParam.getFlowDeployId());
    return processService.getFlowModule(getFlowModuleParam);
  }

}
