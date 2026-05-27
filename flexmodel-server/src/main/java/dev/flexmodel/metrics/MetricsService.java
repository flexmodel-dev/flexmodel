package dev.flexmodel.metrics;

import dev.flexmodel.metrics.dto.FmMetricsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.api.ApiDefinitionService;
import dev.flexmodel.api.ApiRequestLogService;
import dev.flexmodel.flow.service.FlowDefinitionService;
import dev.flexmodel.flow.service.FlowInstanceService;
import dev.flexmodel.modeling.ModelService;
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
  ApiDefinitionService apiDefinitionService;
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

  public FmMetricsResponse getFmMetrics(String projectId) {
    try {
      Integer modelCount = modelService.count(projectId);
      Integer customApiCount = apiDefinitionService.count(projectId);
      long reqLogCount = apiLogService.count(projectId, TRUE);
      long flowDefCount = flowDefService.count(projectId, Expressions.field(FlowDefinition::getIsDeleted).eq(false));
      long flowInsCount = flowInstanceService.count(projectId, TRUE);
      long triggerCount = triggerService.count(projectId, TRUE);
      long jobSuccessCount = jobExecutionLogService.count(Expressions.field(JobExecutionLog::getExecutionStatus).eq("SUCCESS"));
      long jobFailureCount = jobExecutionLogService.count(Expressions.field(JobExecutionLog::getExecutionStatus).eq("FAILED"));

      return FmMetricsResponse.builder()
        .dataSourceCount(-1)
        .customApiCount(customApiCount)
        .requestCount((int) reqLogCount)
        .flowDefCount((int) flowDefCount)
        .flowExecCount((int) flowInsCount)
        .modelCount(modelCount)
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
