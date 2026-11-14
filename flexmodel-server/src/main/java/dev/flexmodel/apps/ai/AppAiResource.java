package dev.flexmodel.apps.ai;

import dev.flexmodel.apps.ai.dto.AiDtos.*;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * 应用机器人配置；会话统一走 {@link AiConversationResource}。
 */
@Path("/apps/{appId}/ai/robots")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "应用 AI", description = "机器人配置与个人会话")
public class AppAiResource {
  @Inject
  AppAiService service;

  @GET
  public List<Robot> robots(@PathParam("appId") String appId) {
    return service.robots(appId);
  }

  @POST
  public Robot create(@PathParam("appId") String appId, @Valid RobotInput input) {
    return service.saveRobot(appId, null, input);
  }

  @PUT
  @Path("/{robotId}")
  public Robot update(@PathParam("appId") String appId, @PathParam("robotId") String robotId, @Valid RobotInput input) {
    return service.saveRobot(appId, robotId, input);
  }

  @DELETE
  @Path("/{robotId}")
  public Response delete(@PathParam("appId") String appId, @PathParam("robotId") String robotId) {
    service.deleteRobot(appId, robotId);
    return Response.noContent().build();
  }
}
