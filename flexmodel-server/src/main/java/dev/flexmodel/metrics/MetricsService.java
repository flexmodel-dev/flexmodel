package dev.flexmodel.metrics;

import dev.flexmodel.metrics.dto.FmMetricsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.api.ApiRequestLogService;
import dev.flexmodel.flow.service.FlowDefinitionService;
import dev.flexmodel.flow.service.FlowInstanceService;
import dev.flexmodel.modeling.ModelService;
import dev.flexmodel.project.BranchService;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.scheduling.TriggerService;
import dev.flexmodel.query.Expressions;

import static dev.flexmodel.query.Expressions.TRUE;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class MetricsService {

  @Inject
  ApiRequestLogService apiLogService;
  @Inject
  ModelService modelService;
  @Inject
  FlowInstanceService flowInstanceService;
  @Inject
  FlowDefinitionService flowDefService;
  @Inject
  TriggerService triggerService;
  @Inject
  JobExecutionLogService jobExecutionLogService;
  @Inject
  BranchService branchService;

  public FmMetricsResponse getFmMetrics(String projectId) {
    try {
      Integer modelCount = modelService.count(projectId);
      int branchCount = branchService.listBranches(projectId).size();
      long reqLogCount = apiLogService.count(projectId, TRUE);
      long flowDefCount = flowDefService.count(projectId, Expressions.field(FlowDefinition::getIsDeleted).eq(false));
      long flowInsCount = flowInstanceService.count(projectId, TRUE);
      long triggerCount = triggerService.count(projectId, TRUE);
      long jobSuccessCount = jobExecutionLogService.count(Expressions.field(JobExecutionLog::getExecutionStatus).eq("SUCCESS"));
      long jobFailureCount = jobExecutionLogService.count(Expressions.field(JobExecutionLog::getExecutionStatus).eq("FAILED"));

      return FmMetricsResponse.builder()
        .requestCount((int) reqLogCount)
        .modelCount(modelCount)
        .branchCount(branchCount)
        .flowDefCount((int) flowDefCount)
        .flowExecCount((int) flowInsCount)
        .triggerTotalCount((int) triggerCount)
        .jobSuccessCount((int) jobSuccessCount)
        .jobFailureCount((int) jobFailureCount)
        .build();

    } catch (Exception e) {
      log.error("get api fm metrics error", e);
      throw new RuntimeException(e);
    }
  }

}
