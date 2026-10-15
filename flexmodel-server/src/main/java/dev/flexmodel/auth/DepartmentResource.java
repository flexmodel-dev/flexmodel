package dev.flexmodel.auth;

import dev.flexmodel.auth.dto.DepartmentRequest;
import dev.flexmodel.auth.dto.DepartmentResponse;
import dev.flexmodel.auth.service.DepartmentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "部门", description = "部门架构管理")
@Path("/departments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DepartmentResource {

  @Inject
  DepartmentService departmentService;

  @GET
  public List<DepartmentResponse> findAll() {
    return departmentService.findAllDepartments();
  }

  @POST
  public DepartmentResponse create(DepartmentRequest request) {
    return departmentService.createDepartment(request);
  }

  @PUT
  @Path("/{departmentId}")
  public DepartmentResponse update(
    @PathParam("departmentId") String departmentId,
    DepartmentRequest request
  ) {
    return departmentService.updateDepartment(departmentId, request);
  }

  @DELETE
  @Path("/{departmentId}")
  public void delete(@PathParam("departmentId") String departmentId) {
    departmentService.deleteDepartment(departmentId);
  }
}
