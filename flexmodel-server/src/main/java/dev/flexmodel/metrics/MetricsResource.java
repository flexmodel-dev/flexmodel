package dev.flexmodel.metrics;

import dev.flexmodel.metrics.dto.FmMetricsResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 系统监控资源类
 * 提供JVM、CPU、内存、线程、磁盘（含I/O）、网络等监控信息
 *
 * @author cjbi
 */
@Path("/v1/projects/{projectId}/metrics")
@Tag(name = "系统监控", description = "系统监控相关接口，包括JVM、CPU、内存、线程、磁盘（含I/O）、网络等监控信息")
@SecurityRequirement(name = "BearerAuth")
public class MetricsResource {
  @Inject
  MetricsService metricsService;


  @GET
  @Path("/fm")
  public FmMetricsResponse getFmMetrics(@PathParam("projectId") String projectId) {
    return metricsService.getFmMetrics(projectId);
  }

}
