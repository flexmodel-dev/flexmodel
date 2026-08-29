package dev.flexmodel.observability.apilog.dto;

import lombok.*;
import dev.flexmodel.observability.apilog.LogApiRank;
import dev.flexmodel.observability.apilog.LogStat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cjbi
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LogStatResponse {
  @Builder.Default
  private List<LogStat> apiStatList = new ArrayList<>();
  @Builder.Default
  private List<LogApiRank> apiRankingList = new ArrayList<>();
  private ApiChart apiChart;

  @Getter
  @Setter
  public static class ApiChart {
    private List<String> dateList;
    private List<Long> successData;
    private List<Long> failData;
  }
}
