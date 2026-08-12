package dev.flexmodel.open;

import dev.flexmodel.api.GraphQLManager;
import graphql.ExecutionResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

/**
 * Open API — GraphQL 查询。
 * <p>
 * 路径前缀 {@code /open/{projectId}/graphql}，
 * 面向终端用户（IdP 用户 / open scope API Key）。
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/open/{projectId}/graphql")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenGraphQLResource {

  @Inject
  GraphQLManager graphQLManager;

  @POST
  public ExecutionResult execute(@PathParam("projectId") String projectId, GraphQLRequest request) {
    return graphQLManager.execute(projectId, request.operationName(), request.query(), request.variables());
  }

  public record GraphQLRequest(
    @Schema(description = "操作名称") String operationName,
    @Schema(description = "查询") String query,
    @Schema(description = "变量") Map<String, Object> variables
  ) {
  }
}
