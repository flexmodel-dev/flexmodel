package dev.flexmodel.notification;

import dev.flexmodel.approval.ApprovalService;
import dev.flexmodel.common.SessionContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.sse.*;

import java.util.*;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {
  @Inject
  SessionContext current;
  @Inject
  NotificationRepository repository;
  @Inject
  NotificationStream streams;
  @Inject
  ApprovalService approvals;
  @Inject
  dev.flexmodel.project.ProjectRepository projects;

  private String user() {
    if (!current.isAuthenticatedUser() || !repository.activeUser(current.getUserId()))
      throw new ForbiddenException("请使用有效个人账号查看通知");
    return current.getUserId();
  }

  @GET
  public Object list(@QueryParam("status") @DefaultValue("unread") String status, @QueryParam("category") String category,
                     @QueryParam("appId") String app, @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    if (!Set.of("unread", "all").contains(status) || category != null && !category.isBlank() && !Set.of("approval", "workflow").contains(category))
      throw new BadRequestException("通知筛选参数无效");
    return repository.list(user(), status, category, app, page, size);
  }

  @GET
  @Path("summary")
  public Object summary() {
    return repository.summary(user());
  }

  @GET
  @Path("{id}")
  public Object detail(@PathParam("id") String id) {
    var result = repository.detail(user(), id);
    result.put("canNavigate", false);
    if (result.get("instanceId") != null && !result.get("instanceId").toString().isBlank() && projects.findProject(result.get("appId").toString()) != null) {
      try {
        result.put("approval", approvals.detail(result.get("appId").toString(), result.get("instanceId").toString()));
        result.put("canNavigate", true);
      } catch (dev.flexmodel.common.NotFoundException | ForbiddenException | IllegalArgumentException ignored) {
      }
    }
    return result;
  }

  @PUT
  @Path("{id}/read")
  public Object read(@PathParam("id") String id) {
    return Map.of("updated", repository.read(user(), id, null, null));
  }

  @POST
  @Path("read-all")
  @Consumes(MediaType.APPLICATION_JSON)
  public Object readAll(Map<String, String> body) {
    var filters = body == null ? Map.<String, String>of() : body;
    String category = filters.get("category");
    if (category != null && !category.isBlank() && !Set.of("approval", "workflow").contains(category))
      throw new BadRequestException("通知分类无效");
    return Map.of("updated", repository.read(user(), null, category, filters.get("appId")));
  }

  @GET
  @Path("stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void stream(@HeaderParam("Last-Event-ID") String last, @HeaderParam("Authorization") String authorization,
                     @Context SseEventSink sink, @Context Sse sse, @Context io.vertx.core.http.HttpServerResponse response) {
    String user = user();
    var expires = com.auth0.jwt.JWT.decode(authorization.substring(7)).getExpiresAt();
    if (expires == null) throw new NotAuthorizedException("通知连接需要有有效期的个人会话");
    response.putHeader("Cache-Control", "no-cache, no-transform").putHeader("X-Accel-Buffering", "no");
    streams.open(user, last, expires.toInstant(), sink, sse);
  }
}
