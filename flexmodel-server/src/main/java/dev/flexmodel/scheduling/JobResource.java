package dev.flexmodel.scheduling;

import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.common.authz.RequiresPermissions;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
@ApplicationScoped
@Tag(name = "任务", description = "任务管理")
@Path("/projects/{projectId}/jobs")
public class JobResource {

  @Inject
  JobService jobService;

  @GET
  @RequiresPermissions("scheduling:view")
  @Path("/logs")
  public PageDTO<JobExecutionLog> findLogPage(@PathParam("projectId") String projectId,
                                              @QueryParam("triggerId") String triggerId,
                                              @QueryParam("jobId") String jobId,
                                              @QueryParam("status") String status,
                                              @QueryParam("startTime") LocalDateTime startTime,
                                              @QueryParam("endTime") LocalDateTime endTime,
                                              @QueryParam("isSuccess") Boolean isSuccess,
                                              @QueryParam("page") @DefaultValue("1") Integer page,
                                              @QueryParam("size") @DefaultValue("20") Integer size) {
    return jobService.findLogPage(projectId, triggerId, jobId, status, startTime, endTime, isSuccess, page, size);
  }

}
