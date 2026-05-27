package dev.flexmodel.flow;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.flow.dto.FlowInstanceListRequest;
import dev.flexmodel.flow.dto.FlowInstanceResponse;
import dev.flexmodel.flow.dto.result.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import dev.flexmodel.flow.service.FlowInstanceService;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.flow.common.ErrorEnum;

/**
 * 流程实例管理
 *
 * @author cjbi
 */
@Tag(name = "服务编排-流程实例", description = "流程实例的查询与生命周期管理")
@Path("/v1/projects/{projectId}/flows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlowInstanceResource {

  @Inject
  FlowInstanceService flowInstanceService;

  @Operation(summary = "获取流程实例列表")
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = FlowInstanceListResponseSchema.class
      )
    )})
  @GET
  @Path("/instances")
  public PageDTO<FlowInstanceResponse> findFlowInstanceList(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowInstanceId", description = "流程实例ID", in = ParameterIn.QUERY)
    @QueryParam("flowInstanceId") String flowInstanceId,
    @Parameter(name = "flowModuleId", description = "流程模块ID", in = ParameterIn.QUERY)
    @QueryParam("flowModuleId") String flowModuleId,
    @Parameter(name = "flowDeployId", description = "流程部署ID", in = ParameterIn.QUERY)
    @QueryParam("flowDeployId") String flowDeployId,
    @Parameter(name = "status", description = "流程实例状态", in = ParameterIn.QUERY)
    @QueryParam("status") Integer status,
    @Parameter(name = "caller", description = "调用者", in = ParameterIn.QUERY)
    @QueryParam("caller") String caller,
    @Parameter(name = "page", description = "页码", in = ParameterIn.QUERY)
    @QueryParam("page") @DefaultValue("1") Integer page,
    @Parameter(name = "size", description = "每页大小", in = ParameterIn.QUERY)
    @QueryParam("size") @DefaultValue("20") Integer size) {
    FlowInstanceListRequest request = new FlowInstanceListRequest();
    request.setProjectId(projectId);
    request.setFlowInstanceId(flowInstanceId);
    request.setFlowModuleId(flowModuleId);
    request.setFlowDeployId(flowDeployId);
    request.setStatus(status);
    request.setCaller(caller);
    request.setPage(page);
    request.setSize(size);
    return flowInstanceService.findFlowInstanceList(request);
  }

  @Operation(summary = "获取流程实例信息")
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = FlowInstanceSchema.class
      )
    )})
  @GET
  @Path("/instances/{flowInstanceId}")
  public FlowInstance getFlowInstance(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowInstanceId", description = "流程实例ID", in = ParameterIn.PATH)
    @PathParam("flowInstanceId") String flowInstanceId) {
    return flowInstanceService.findFlowInstance(projectId, flowInstanceId);
  }

  @Operation(summary = "终止流程实例")
  @POST
  @Path("/instances/{flowInstanceId}/terminate")
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = TerminateResultSchema.class
      )
    )})
  public TerminateResult terminateProcess(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowInstanceId", description = "流程实例ID", in = ParameterIn.PATH)
    @PathParam("flowInstanceId") String flowInstanceId,
    @Parameter(name = "effectiveForSubFlowInstance", description = "是否对子流程实例生效", in = ParameterIn.QUERY)
    @QueryParam("effectiveForSubFlowInstance") @DefaultValue("true") boolean effectiveForSubFlowInstance) {
    return flowInstanceService.terminateProcess(projectId, flowInstanceId, effectiveForSubFlowInstance);
  }

  // ==================== OpenAPI Schema 定义 ====================

  @Schema(
    properties = {
      @SchemaProperty(name = "list", description = "流程实例列表", type = SchemaType.ARRAY),
      @SchemaProperty(name = "total", examples = {"100"}, description = "总记录数")
    }
  )
  @Getter
  @Setter
  public static class FlowInstanceListResponseSchema {
    private java.util.List<FlowInstanceSchema> list;
    private Long total;
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "flowInstanceId", examples = {"flow_inst_001"}, description = "流程实例ID"),
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID"),
      @SchemaProperty(name = "flowDeployId", examples = {"flow_deploy_001"}, description = "流程部署ID"),
      @SchemaProperty(name = "status", examples = {"1"}, description = "流程实例状态：1-运行中，2-已完成，3-已终止，4-已暂停"),
      @SchemaProperty(name = "parentFlowInstanceId", examples = {"parent_inst_001"}, description = "父流程实例ID"),
      @SchemaProperty(name = "projectId", examples = {"default"}, description = "项目ID"),
      @SchemaProperty(name = "caller", examples = {"admin"}, description = "调用者"),
      @SchemaProperty(name = "operator", examples = {"admin"}, description = "操作者"),
      @SchemaProperty(name = "createTime", description = "创建时间", readOnly = true),
      @SchemaProperty(name = "modifyTime", description = "修改时间", readOnly = true)
    }
  )
  @Getter
  @Setter
  public static class FlowInstanceSchema {
    private String flowInstanceId;
    private String flowModuleId;
    private String flowDeployId;
    private Integer status;
    private String parentFlowInstanceId;
    private String tenant;
    private String caller;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime modifyTime;
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "errCode", examples = {"0"}, description = "错误码"),
      @SchemaProperty(name = "errMsg", examples = {"success"}, description = "错误信息"),
      @SchemaProperty(name = "flowInstanceId", examples = {"flow_inst_001"}, description = "流程实例ID"),
      @SchemaProperty(name = "status", examples = {"1"}, description = "状态")
    }
  )
  public static class TerminateResultSchema extends TerminateResult {
    public TerminateResultSchema() {
      super(ErrorEnum.SUCCESS);
    }
  }

}
