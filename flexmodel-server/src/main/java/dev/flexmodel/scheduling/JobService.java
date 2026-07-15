package dev.flexmodel.scheduling;

import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业执行日志应用服务
 * 提供作业执行日志的查询和管理功能
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class JobService {

  @Inject
  JobExecutionLogService jobExecutionLogService;

  /**
   * 分页查询作业执行日志
   *
   * @param projectId
   * @param triggerId 触发器ID（可选）
   * @param jobId     作业ID（可选）
   * @param status    执行状态（可选）
   * @param startTime 开始时间（可选）
   * @param endTime   结束时间（可选）
   * @param isSuccess 是否成功（可选）
   * @param page      页码（从0开始）
   * @param size      每页大小
   * @return 分页结果
   */
  public PageDTO<JobExecutionLog> findLogPage(String projectId, String triggerId, String jobId, String status,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              Boolean isSuccess, Integer page, Integer size) {
    List<JobExecutionLog> logs = jobExecutionLogService.findWithConditions(
      triggerId, jobId, status, startTime, endTime, isSuccess, page, size);
    long total = jobExecutionLogService.countWithConditions(
      triggerId, jobId, status, startTime, endTime, isSuccess);

    return new PageDTO<>(logs, total);
  }

  /**
   * 根据ID查询作业执行日志
   *
   * @param id 日志ID
   * @return 作业执行日志
   */
  public JobExecutionLog findLogById(String id) {
    return jobExecutionLogService.findById(id);
  }

}
