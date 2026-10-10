package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormFieldUpdateRequest;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppFormUpdateRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * 应用表单 REST API。
 *
 * @author cjbi
 */
@Tag(name = "应用", description = "应用表单管理")
@Path("/apps/{appId}/forms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppFormResource {

  @Inject
  AppFormService appFormService;

  @Operation(summary = "获取应用表单列表")
  @GET
  public List<AppFormResponse> list(@PathParam("appId") String appId) {
    return appFormService.findForms(appId);
  }

  @Operation(summary = "获取应用表单详情")
  @GET
  @Path("/{formName}")
  public AppFormResponse get(@PathParam("appId") String appId, @PathParam("formName") String formName) {
    return appFormService.findForm(appId, formName);
  }

  @Operation(summary = "创建应用表单")
  @POST
  public Response create(@PathParam("appId") String appId, @Valid AppFormCreateRequest request) {
    AppFormResponse response = appFormService.createForm(appId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Parameter(name = "formName", description = "表单名", in = ParameterIn.PATH, required = true)
  @Operation(summary = "更新应用表单")
  @PUT
  @Path("/{formName}")
  public AppFormResponse update(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @Valid AppFormUpdateRequest request
  ) {
    return appFormService.updateForm(appId, formName, request);
  }

  @Parameter(name = "formName", description = "表单名", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除应用表单")
  @DELETE
  @Path("/{formName}")
  public Response delete(@PathParam("appId") String appId, @PathParam("formName") String formName) {
    appFormService.deleteForm(appId, formName);
    return Response.noContent().build();
  }

  @Operation(summary = "创建表单字段")
  @POST
  @Path("/{formName}/fields")
  public Response createField(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @Valid AppFormFieldCreateRequest request
  ) {
    return Response.status(Response.Status.CREATED)
      .entity(appFormService.createField(appId, formName, request))
      .build();
  }

  @Operation(summary = "更新表单字段")
  @PUT
  @Path("/{formName}/fields/{fieldName}")
  public AppFormFieldResponse updateField(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("fieldName") String fieldName,
    @Valid AppFormFieldUpdateRequest request
  ) {
    return appFormService.updateField(appId, formName, fieldName, request);
  }

  @Operation(summary = "删除表单字段")
  @DELETE
  @Path("/{formName}/fields/{fieldName}")
  public Response deleteField(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("fieldName") String fieldName
  ) {
    appFormService.deleteField(appId, formName, fieldName);
    return Response.noContent().build();
  }
}
