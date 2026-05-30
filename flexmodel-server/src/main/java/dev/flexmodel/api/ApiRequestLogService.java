package dev.flexmodel.api;

import dev.flexmodel.api.dto.LogStatResponse;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.query.Expressions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.query.Predicate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.flexmodel.query.Expressions.field;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
@ActivateRequestContext
public class ApiRequestLogService {

  @Inject
  ApiRequestLogRepository apiLogRepository;

  public PageDTO<ApiRequestLog> findApiLogs(String projectId, int current, int pageSize, String keyword, LocalDateTime startDate, LocalDateTime endDate, Boolean isSuccess) {
    List<ApiRequestLog> list = find(projectId, getCondition(keyword, startDate, endDate, isSuccess), current, pageSize);
    long total = count(projectId, getCondition(keyword, startDate, endDate, isSuccess));
    return new PageDTO<>(list, total);
  }

  public LogStatResponse stat(String projectId, String keyword, LocalDateTime startDate, LocalDateTime endDate, Boolean isSuccess) {

    LogStatResponse.ApiChart statDTO = null;
    List<String> dateList = new ArrayList<>();
    String fmt = "yyyy-MM-dd HH:00:00";
    if (startDate == null) {
      startDate = LocalDateTime.now().minusDays(7);
    }
    if (endDate == null) {
      endDate = LocalDateTime.now();
    }
    if (ChronoUnit.DAYS.between(startDate, endDate) <= 1) {
      fmt = "yyyy-MM-dd HH:00:00";
      LocalDateTime currentTime = startDate;
      while (!currentTime.isAfter(endDate)) {
        dateList.add(currentTime.format(DateTimeFormatter.ofPattern(fmt)));
        currentTime = currentTime.plusHours(1);
      }
    } else if (ChronoUnit.DAYS.between(startDate, endDate) > 1 && ChronoUnit.DAYS.between(startDate, endDate) <= 7) {
      fmt = "yyyy-MM-dd";
      LocalDateTime currentTime = startDate;
      while (!currentTime.isAfter(endDate)) {
        dateList.add(currentTime.format(DateTimeFormatter.ofPattern(fmt)));
        currentTime = currentTime.plusDays(1);
      }

    } else if (ChronoUnit.DAYS.between(startDate, endDate) > 7 && ChronoUnit.DAYS.between(startDate, endDate) <= 31) {
      fmt = "yyyy-MM-dd";
      LocalDateTime currentTime = startDate;
      while (!currentTime.isAfter(endDate)) {
        dateList.add(currentTime.format(DateTimeFormatter.ofPattern(fmt)));
        currentTime = currentTime.plusDays(1);
      }
    } else if (ChronoUnit.DAYS.between(startDate, endDate) > 31 && ChronoUnit.DAYS.between(startDate, endDate) <= 365) {
      fmt = "yyyy-MM";
      LocalDateTime currentTime = startDate;
      while (!currentTime.isAfter(endDate)) {
        dateList.add(currentTime.format(DateTimeFormatter.ofPattern(fmt)));
        currentTime = currentTime.plusMonths(1);
      }
    } else {
      fmt = "yyyy";
      LocalDateTime currentTime = startDate;
      while (!currentTime.isAfter(endDate)) {
        dateList.add(currentTime.format(DateTimeFormatter.ofPattern(fmt)));
        currentTime = currentTime.plusYears(1);
      }
    }

    Predicate condition = getCondition(keyword, startDate, endDate, isSuccess);

    Map<String, Long> successMap = stat(projectId, condition.and(field(ApiRequestLog::getIsSuccess).eq(true)), fmt).stream().collect(Collectors.toMap(LogStat::getDate, LogStat::getTotal));
    Map<String, Long> failMap = stat(projectId, condition.and(field(ApiRequestLog::getIsSuccess).eq(false)), fmt).stream().collect(Collectors.toMap(LogStat::getDate, LogStat::getTotal));
    statDTO = new LogStatResponse.ApiChart();
    List<Long> successData = new ArrayList<>();
    List<Long> failData = new ArrayList<>();
    for (String date : dateList) {
      successData.add(successMap.getOrDefault(date, 0L));
      failData.add(failMap.getOrDefault(date, 0L));
    }
    statDTO.setDateList(dateList);
    statDTO.setSuccessData(successData);
    statDTO.setFailData(failData);

    List<LogStat> stat = stat(projectId, condition, fmt);

    List<LogApiRank> apiRankList = ranking(projectId, condition);

    return LogStatResponse.builder().apiStatList(stat).apiRankingList(apiRankList).apiChart(statDTO).build();
  }

  private static Predicate getCondition(String keyword, LocalDateTime startDate, LocalDateTime endDate, Boolean isSuccess) {
    Predicate condition = Expressions.TRUE;
    if (keyword != null) {
      condition = condition.and(field(ApiRequestLog::getRequestBody).contains(keyword));
    }
    if (startDate != null && endDate != null) {
      condition = condition.and(field(ApiRequestLog::getCreatedAt).between(startDate, endDate));
    }
    if (isSuccess != null) {
      condition = condition.and(field(ApiRequestLog::getIsSuccess).eq(isSuccess));
    }
    return condition;
  }

  public ApiRequestLog create(ApiRequestLog apiRequestLog) {
    apiRequestLog.setCreatedAt(LocalDateTime.now());
    return apiLogRepository.save(apiRequestLog);
  }

  public List<ApiRequestLog> find(String projectId, Predicate filter, Integer current, Integer pageSize) {
    return apiLogRepository.find(projectId, filter, current, pageSize);
  }

  public long count(String projectId, Predicate filter) {
    return apiLogRepository.count(projectId, filter);
  }

  public List<LogStat> stat(String projectId, Predicate filter, String fmt) {
    return apiLogRepository.stat(projectId, filter, fmt);
  }

  public List<LogApiRank> ranking(String projectId, Predicate filter) {
    return apiLogRepository.ranking(projectId, filter);
  }

  public void purgeOldLogs(int maxDays) {
    log.info("Purging old logs older than {} days", maxDays);
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(maxDays);
    apiLogRepository.delete(field(ApiRequestLog::getCreatedAt).lte(purgeDate));
  }
}
