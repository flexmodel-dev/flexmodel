package dev.flexmodel.observability.function;

import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.codegen.System.functionLog;

/**
 * 函数执行日志查询服务。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
@ActivateRequestContext
public class FunctionLogService {

  @Inject
  FunctionLogRepository functionLogRepository;

  public PageDTO<FunctionLog> findFunctionLogs(String projectId, int current, int pageSize,
                                               String functionName, String level,
                                               LocalDateTime startDate, LocalDateTime endDate,
                                               String traceId, String keyword) {
    Predicate filter = getCondition(functionName, level, startDate, endDate, traceId, keyword);
    List<FunctionLog> list = functionLogRepository.find(projectId, filter, current, pageSize);
    long total = functionLogRepository.count(projectId, filter);
    return new PageDTO<>(list, total);
  }

  private static Predicate getCondition(String functionName, String level,
                                        LocalDateTime startDate, LocalDateTime endDate,
                                        String traceId, String keyword) {
    Predicate condition = Expressions.TRUE;
    if (functionName != null && !functionName.isBlank()) {
      condition = condition.and(functionLog.functionName.eq(functionName));
    }
    if (level != null && !level.isBlank()) {
      condition = condition.and(functionLog.level.eq(level));
    }
    if (startDate != null && endDate != null) {
      condition = condition.and(functionLog.createdAt.between(startDate, endDate));
    }
    if (traceId != null && !traceId.isBlank()) {
      condition = condition.and(functionLog.traceId.eq(traceId));
    }
    if (keyword != null && !keyword.isBlank()) {
      condition = condition.and(functionLog.message.contains(keyword));
    }
    return condition;
  }

  public void purgeOldLogs(String projectId, int maxDays) {
    log.info("Purging old function logs older than {} days for project {}", maxDays, projectId);
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(maxDays);
    functionLogRepository.delete(projectId, functionLog.createdAt.lte(purgeDate));
  }
}
