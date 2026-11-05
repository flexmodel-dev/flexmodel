package dev.flexmodel.apps.ai;

import dev.flexmodel.apps.ai.dto.AiDtos.*;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestSseElementType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

/**
 * 统一 AI 会话入口：会话按机器人 + 用户隔离，不按应用拆分。
 * robotId 省略时使用内置助手（assistant）；当前应用上下文在发送消息时以隐藏参数
 * appId 传递，消息中「@应用名」显式指定的应用优先（可多个）。
 */
@Path("/ai/conversations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "应用 AI", description = "AI 会话：内置助手与应用机器人共用")
public class AiConversationResource {
  @Inject
  AppAiService service;

  @GET
  public List<Conversation> conversations(@QueryParam("robotId") String robotId) {
    return service.conversations(robotId);
  }

  @POST
  public Conversation create(@Valid ConversationRequest input) {
    return service.createConversation(input == null ? null : input.robotId());
  }

  /**
   * SSE 流式返回：token/reasoning/tool/done/error 事件，done 携带完成后的完整会话。
   */
  @POST
  @Path("/{id}/messages")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<StreamEvent> send(@PathParam("id") String id, @Valid SendRequest input) {
    return service.streamSend(id, input);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") String id) {
    service.deleteConversation(id);
    return Response.noContent().build();
  }
}
