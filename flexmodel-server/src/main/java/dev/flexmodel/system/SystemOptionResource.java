package dev.flexmodel.system;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Tag(name = "系统表单", description = "系统表单组件选项")
@ApplicationScoped
@Path("/system/options")
@Produces(MediaType.APPLICATION_JSON)
public class SystemOptionResource {

  @Inject
  SystemOptionService systemOptionService;

  @Operation(summary = "获取部门选项")
  @GET
  @Path("/departments")
  public List<Map<String, Object>> departments() {
    return systemOptionService.departments();
  }

  @Operation(summary = "获取角色选项")
  @GET
  @Path("/roles")
  public List<Map<String, Object>> roles() {
    return systemOptionService.roles();
  }

  @Operation(summary = "获取用户选项")
  @GET
  @Path("/users")
  public List<Map<String, Object>> users() {
    return systemOptionService.users();
  }

}
