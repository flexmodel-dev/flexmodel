package dev.flexmodel.apps.form;

import dev.flexmodel.apps.form.dto.*;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
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

  @Parameter(name = "formName", description = "表单名称", in = ParameterIn.PATH, required = true)
  @Operation(summary = "局部更新应用表单")
  @PATCH
  @Path("/{formName}")
  public AppFormResponse patch(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @Valid AppFormPatchRequest request
  ) {
    return appFormService.patchForm(appId, formName, request);
  }

  @Parameter(name = "formName", description = "表单名", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除应用表单")
  @DELETE
  @Path("/{formName}")
  public Response delete(@PathParam("appId") String appId, @PathParam("formName") String formName) {
    appFormService.deleteForm(appId, formName);
    return Response.noContent().build();
  }

  @Parameter(name = "formName", description = "表单名称", in = ParameterIn.PATH, required = true)
  @Operation(summary = "保存表单设计布局")
  @PUT
  @Path("/{formName}/layout")
  public AppFormResponse saveLayout(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @Valid AppFormLayoutRequest request
  ) {
    return appFormService.saveFormLayout(appId, formName, request);
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
