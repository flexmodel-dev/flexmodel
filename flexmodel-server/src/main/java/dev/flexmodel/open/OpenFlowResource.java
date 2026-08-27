package dev.flexmodel.open;

import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.flow.dto.FlowInstanceListRequest;
import dev.flexmodel.flow.dto.FlowInstanceResponse;
import dev.flexmodel.flow.dto.bo.ElementInstance;
import dev.flexmodel.flow.dto.bo.NodeInstance;
import dev.flexmodel.flow.dto.param.CommitTaskParam;
import dev.flexmodel.flow.dto.param.RollbackTaskParam;
import dev.flexmodel.flow.dto.param.StartProcessParam;
import dev.flexmodel.flow.dto.result.CommitTaskResult;
import dev.flexmodel.flow.dto.result.RollbackTaskResult;
import dev.flexmodel.flow.dto.result.StartProcessResult;
import dev.flexmodel.flow.dto.result.TerminateResult;
import dev.flexmodel.flow.service.FlowExecutionService;
import dev.flexmodel.flow.service.FlowInstanceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Open API — 服务编排（流程运行态）。
 * <p>
 * 路径前缀 {@code /open/{projectId}/flows}，面向终端用户（IdP 用户 / open scope API Key）。
 * 仅暴露运行态接口（启动/提交/回滚/查询/终止），不暴露设计态接口
 * （流程定义的增删改查与部署，参见 admin {@code FlowDefinitionResource}）。
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/open/{projectId}/flows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenFlowResource {

  @Inject
  FlowInstanceService flowInstanceService;

  @Inject
  FlowExecutionService flowExecutionService;

  // ==================== 流程实例查询 ====================

  @GET
  @Path("/instances")
  public PageDTO<FlowInstanceResponse> findFlowInstanceList(
    @PathParam("projectId") String projectId,
    @QueryParam("flowInstanceId") String flowInstanceId,
    @QueryParam("flowModuleId") String flowModuleId,
    @QueryParam("flowDeployId") String flowDeployId,
    @QueryParam("status") Integer status,
    @QueryParam("initiator") String initiator,
    @QueryParam("page") @DefaultValue("1") Integer page,
    @QueryParam("size") @DefaultValue("20") Integer size) {
    FlowInstanceListRequest request = new FlowInstanceListRequest();
    request.setProjectId(projectId);
    request.setFlowInstanceId(flowInstanceId);
    request.setFlowModuleId(flowModuleId);
    request.setFlowDeployId(flowDeployId);
    request.setStatus(status);
    request.setInitiator(initiator);
    request.setPage(page);
    request.setSize(size);
    return flowInstanceService.findFlowInstanceList(request);
  }

  @GET
  @Path("/instances/{flowInstanceId}")
  public FlowInstance getFlowInstance(
    @PathParam("projectId") String projectId,
    @PathParam("flowInstanceId") String flowInstanceId) {
    return flowInstanceService.findFlowInstance(projectId, flowInstanceId);
  }

  @POST
  @Path("/instances/{flowInstanceId}/terminate")
  public TerminateResult terminateProcess(
    @PathParam("projectId") String projectId,
    @PathParam("flowInstanceId") String flowInstanceId,
    @QueryParam("effectiveForSubFlowInstance") @DefaultValue("true") boolean effectiveForSubFlowInstance) {
    return flowInstanceService.terminateProcess(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  // ==================== 流程执行 ====================

  @POST
  @Path("/instances/start")
  public StartProcessResult startProcess(@PathParam("projectId") String projectId, StartProcessParam startProcessParam) {
    startProcessParam.setProjectId(projectId);
    return flowExecutionService.startProcess(startProcessParam);
  }

  @POST
  @Path("/instances/{flowInstanceId}/commit")
  public CommitTaskResult commitTask(
    @PathParam("projectId") String projectId,
    @PathParam("flowInstanceId") String flowInstanceId,
    CommitTaskParam commitTaskParam) {
    commitTaskParam.setProjectId(projectId);
    commitTaskParam.setFlowInstanceId(flowInstanceId);
    return flowExecutionService.commitTask(commitTaskParam);
  }

  @POST
  @Path("/instances/{flowInstanceId}/rollback")
  public RollbackTaskResult rollbackTask(
    @PathParam("projectId") String projectId,
    @PathParam("flowInstanceId") String flowInstanceId,
    RollbackTaskParam rollbackTaskParam) {
    rollbackTaskParam.setProjectId(projectId);
    rollbackTaskParam.setFlowInstanceId(flowInstanceId);
    return flowExecutionService.rollbackTask(rollbackTaskParam);
  }

  @GET
  @Path("/instances/{flowInstanceId}/user-tasks")
  public List<NodeInstance> getHistoryUserTaskList(
    @PathParam("projectId") String projectId,
    @PathParam("flowInstanceId") String flowInstanceId,
    @QueryParam("effectiveForSubFlowInstance") @DefaultValue("true") boolean effectiveForSubFlowInstance) {
    return flowExecutionService.getHistoryUserTaskList(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  @GET
  @Path("/instances/{flowInstanceId}/elements")
  public List<ElementInstance> getHistoryElementList(
    @PathParam("projectId") String projectId,
    @PathParam("flowInstanceId") String flowInstanceId) {
    return flowExecutionService.getHistoryElementList(projectId, flowInstanceId);
  }
}
