package dev.flexmodel.api;

import dev.flexmodel.common.authz.RequiresPermissions;
import graphql.ExecutionResult;
import jakarta.inject.Inject;
import jakarta.websocket.server.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * @author cjbi
 */
@Tag(name = "GraphQL", description = "GraphQL接口信息")
@Path("/projects/{projectId}/graphql")
public class GraphQLResource {

  @Inject
  GraphQLManager graphQLApplicationService;

  @Operation(summary = "执行GraphQL查询")
  @POST
  @RequiresPermissions("graphql:execute")
  public ExecutionResult execute(@PathParam("projectId") String projectId, GraphQLRequest request) {
    return graphQLApplicationService.execute(projectId, request.operationName(), request.query(), request.variables());
  }

  public record GraphQLRequest(@Schema(description = "操作名称") String operationName,
                               @Schema(description = "查询") String query,
                               @Schema(description = "变量") Map<String, Object> variables) {
  }

}
