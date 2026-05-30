package dev.flexmodel.flow;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.flow.dto.FlowModuleListRequest;
import dev.flexmodel.flow.dto.FlowModuleResponse;
import dev.flexmodel.flow.dto.param.*;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import dev.flexmodel.flow.service.FlowDefinitionService;

/**
 * 流程定义管理
 *
 * @author cjbi
 */
@Tag(name = "服务编排-流程定义", description = "流程模块的增删改查与部署管理")
@Path("/v1/projects/{projectId}/flows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlowDefinitionResource {

  @Inject
  FlowDefinitionService flowDefinitionService;

  @Operation(summary = "获取流程列表")
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = FlowModuleListResponseSchema.class
      )
    )})
  @GET
  public PageDTO<FlowModuleResponse> findFlowList(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowKey", description = "流程模块ID", in = ParameterIn.QUERY)
    @QueryParam("flowModuleId") String flowModuleId,
    @QueryParam("flowKey") String flowKey,
    @Parameter(name = "flowName", description = "流程名称", in = ParameterIn.QUERY)
    @QueryParam("flowName") String flowName,
    @Parameter(name = "page", description = "页码", in = ParameterIn.QUERY)
    @QueryParam("page") @DefaultValue("1") Integer page,
    @Parameter(name = "size", description = "每页大小", in = ParameterIn.QUERY)
    @QueryParam("size") @DefaultValue("20") Integer size) {
    FlowModuleListRequest request = new FlowModuleListRequest();
    request.setProjectId(projectId);
    request.setFlowModuleId(flowModuleId);
    request.setFlowKey(flowKey);
    request.setFlowName(flowName);
    request.setPage(page);
    request.setSize(size);
    return flowDefinitionService.findFlowModuleList(request);
  }

  @Operation(summary = "创建流程")
  @POST
  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = CreateFlowParamSchema.class
      )
    )}
  )
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = CreateFlowResultSchema.class
      )
    )})
  public CreateFlowResult createFlow(@PathParam("projectId") String projectId, CreateFlowParam createFlowParam) {
    createFlowParam.setProjectId(projectId);
    createFlowParam.setCaller("admin");
    createFlowParam.setOperator("admin");
    return flowDefinitionService.createFlow(createFlowParam);
  }

  @Operation(summary = "更新流程")
  @PUT
  @Path("/{flowModuleId}")
  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = UpdateFlowParamSchema.class
      )
    )}
  )
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = UpdateFlowResultSchema.class
      )
    )})
  public UpdateFlowResult updateFlow(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowModuleId", description = "流程模块ID", in = ParameterIn.PATH)
    @PathParam("flowModuleId") String flowModuleId,
    UpdateFlowParam updateFlowParam) {
    updateFlowParam.setFlowModuleId(flowModuleId);
    updateFlowParam.setProjectId(projectId);
    updateFlowParam.setCaller(updateFlowParam.getCaller());
    updateFlowParam.setOperator(updateFlowParam.getOperator());
    return flowDefinitionService.updateFlow(updateFlowParam);
  }

  @DELETE
  @Path("/{flowModuleId}")
  public void deleteFlow(@PathParam("projectId") String projectId, @Parameter(name = "flowModuleId", description = "流程模块ID", in = ParameterIn.PATH)
  @PathParam("flowModuleId") String flowModuleId) {
    flowDefinitionService.deleteFlow(projectId, flowModuleId);
  }

  @Operation(summary = "获取流程模块信息")
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = FlowModuleResultSchema.class
      )
    )})
  @GET
  @Path("/{flowModuleId}")
  public FlowModuleResult getFlowModule(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowModuleId", description = "流程模块ID", in = ParameterIn.PATH)
    @PathParam("flowModuleId") String flowModuleId,
    @Parameter(name = "flowDeployId", description = "流程部署ID", in = ParameterIn.QUERY)
    @QueryParam("flowDeployId") String flowDeployId) {
    GetFlowModuleParam param = new GetFlowModuleParam();
    param.setProjectId(projectId);
    param.setFlowModuleId(flowModuleId);
    param.setFlowDeployId(flowDeployId);
    return flowDefinitionService.getFlowModule(param);
  }

  @Operation(summary = "部署流程")
  @POST
  @Path("/{flowModuleId}/deploy")
  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = DeployFlowParamSchema.class
      )
    )}
  )
  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        implementation = DeployFlowResultSchema.class
      )
    )})
  public DeployFlowResult deployFlow(
    @PathParam("projectId") String projectId,
    @Parameter(name = "flowModuleId", description = "流程模块ID", in = ParameterIn.PATH)
    @PathParam("flowModuleId") String flowModuleId,
    DeployFlowParam deployFlowParam) {
    deployFlowParam.setProjectId(projectId);
    deployFlowParam.setFlowModuleId(flowModuleId);
    deployFlowParam.setCaller("admin");
    deployFlowParam.setOperator("admin");
    return flowDefinitionService.deployFlow(deployFlowParam);
  }

  // ==================== OpenAPI Schema 定义 ====================

  @Schema(
    properties = {
      @SchemaProperty(name = "flowKey", examples = {"order_process"}, description = "流程键"),
      @SchemaProperty(name = "flowName", examples = {"订单处理流程"}, description = "流程名称"),
      @SchemaProperty(name = "remark", examples = {"处理订单的完整业务流程"}, description = "备注"),
      @SchemaProperty(name = "projectId", examples = {"default"}, description = "项目ID"),
      @SchemaProperty(name = "caller", examples = {"admin"}, description = "调用者"),
      @SchemaProperty(name = "operator", examples = {"admin"}, description = "操作者")
    }
  )
  public static class CreateFlowParamSchema extends CreateFlowParam {
    public CreateFlowParamSchema() {
      super("default", "admin");
    }
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "errCode", examples = {"0"}, description = "错误码"),
      @SchemaProperty(name = "errMsg", examples = {"success"}, description = "错误信息"),
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID")
    }
  )
  public static class CreateFlowResultSchema extends CreateFlowResult {
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID"),
      @SchemaProperty(name = "projectId", examples = {"default"}, description = "项目ID"),
      @SchemaProperty(name = "caller", examples = {"admin"}, description = "调用者"),
      @SchemaProperty(name = "operator", examples = {"admin"}, description = "操作者")
    }
  )
  public static class DeployFlowParamSchema extends DeployFlowParam {
    public DeployFlowParamSchema() {
      super("default", "admin");
    }
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID"),
      @SchemaProperty(name = "projectId", examples = {"default"}, description = "项目ID"),
      @SchemaProperty(name = "caller", examples = {"admin"}, description = "调用者"),
      @SchemaProperty(name = "operator", examples = {"admin"}, description = "操作者")
    }
  )
  public static class UpdateFlowParamSchema extends DeployFlowParam {
    public UpdateFlowParamSchema() {
      super("default", "admin");
    }
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "errCode", examples = {"0"}, description = "错误码"),
      @SchemaProperty(name = "errMsg", examples = {"success"}, description = "错误信息"),
      @SchemaProperty(name = "flowDeployId", examples = {"flow_deploy_001"}, description = "流程部署ID"),
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID")
    }
  )
  public static class DeployFlowResultSchema extends DeployFlowResult {
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "errCode", examples = {"0"}, description = "错误码"),
      @SchemaProperty(name = "errMsg", examples = {"success"}, description = "错误信息"),
    }
  )
  public static class UpdateFlowResultSchema extends UpdateFlowResult {
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "errCode", examples = {"0"}, description = "错误码"),
      @SchemaProperty(name = "errMsg", examples = {"success"}, description = "错误信息"),
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID"),
      @SchemaProperty(name = "flowDeployId", examples = {"flow_deploy_001"}, description = "流程部署ID")
    }
  )
  public static class FlowModuleResultSchema extends FlowModuleResult {
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "list", description = "流程模块列表", type = SchemaType.ARRAY),
      @SchemaProperty(name = "total", examples = {"100"}, description = "总记录数")
    }
  )
  @Getter
  @Setter
  public static class FlowModuleListResponseSchema {
    private java.util.List<FlowModuleResponseSchema> list;
    private Long total;
  }

  @Schema(
    properties = {
      @SchemaProperty(name = "flowModuleId", examples = {"flow_module_001"}, description = "流程模块ID"),
      @SchemaProperty(name = "flowName", examples = {"订单处理流程"}, description = "流程名称"),
      @SchemaProperty(name = "flowKey", examples = {"order_process"}, description = "流程键"),
      @SchemaProperty(name = "status", examples = {"4"}, description = "状态：1-草稿，2-设计，3-测试，4-已发布"),
      @SchemaProperty(name = "remark", examples = {"处理订单的完整业务流程"}, description = "备注"),
      @SchemaProperty(name = "projectId", examples = {"default"}, description = "项目ID"),
      @SchemaProperty(name = "caller", examples = {"admin"}, description = "调用者"),
      @SchemaProperty(name = "operator", examples = {"admin"}, description = "操作者"),
      @SchemaProperty(name = "modifyTime", description = "修改时间", readOnly = true)
    }
  )
  public static class FlowModuleResponseSchema extends FlowModuleResponse {
  }

}
