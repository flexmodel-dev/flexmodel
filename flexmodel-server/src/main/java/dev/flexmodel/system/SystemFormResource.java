package dev.flexmodel.system;

import dev.flexmodel.system.dto.SystemFormResponse;
import dev.flexmodel.system.dto.SystemFormSaveRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "系统表单", description = "系统内置表单配置")
@Path("/system/forms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SystemFormResource {

  @Inject
  SystemFormService systemFormService;

  @Operation(summary = "获取系统表单列表")
  @GET
  public List<SystemFormResponse> list() {
    return systemFormService.findForms();
  }

  @Operation(summary = "获取系统表单定义")
  @GET
  @Path("/{formName}")
  public SystemFormResponse get(@PathParam("formName") String formName) {
    return systemFormService.findForm(formName);
  }

  @Operation(summary = "保存系统表单定义")
  @PUT
  @Path("/{formName}")
  public SystemFormResponse save(
    @PathParam("formName") String formName,
    @Valid SystemFormSaveRequest request
  ) {
    return systemFormService.saveForm(formName, request);
  }
}
