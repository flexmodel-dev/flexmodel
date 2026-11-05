package dev.flexmodel.apps.workbench;

import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormFieldOptionResponse;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.apps.workbench.dto.*;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.config.EngineConfig;
import dev.flexmodel.data.DataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class AppWorkbenchDataService {

  private static final int MAX_CHART_GROUPS = 100;
  private static final int MAX_DATA_LOADER_SIZE = 50;

  @Inject
  AppWorkbenchService appWorkbenchService;

  @Inject
  AppFormService appFormService;

  @Inject
  AppRecordService appRecordService;

  @Inject
  DataService dataService;

  public WorkbenchComponentDataResponse findComponentData(
    String appId,
    String workbenchId,
    String componentId,
    Integer page,
    Integer size,
    String search
  ) {
    WorkbenchComponentRecord component = appWorkbenchService.findComponent(appId, workbenchId, componentId);
    WorkbenchComponentData data = switch (component.config()) {
      case dev.flexmodel.apps.workbench.dto.WorkbenchMetricConfig config -> metricData(appId, config);
      case dev.flexmodel.apps.workbench.dto.WorkbenchChartConfig config -> chartData(appId, config);
      case dev.flexmodel.apps.workbench.dto.WorkbenchDataLoaderConfig config -> dataLoaderData(
        appId, config, normalizedPage(page), normalizedSize(size), search);
      case dev.flexmodel.apps.workbench.dto.WorkbenchShortcutsConfig ignored -> throw unsupportedComponent("shortcuts");
      case dev.flexmodel.apps.workbench.dto.WorkbenchTasksConfig ignored -> throw unsupportedComponent("tasks");
      case dev.flexmodel.apps.workbench.dto.WorkbenchAiConfig ignored -> throw unsupportedComponent("ai");
    };
    return new WorkbenchComponentDataResponse(componentId, data);
  }

  private WorkbenchMetricData metricData(
    String appId,
    dev.flexmodel.apps.workbench.dto.WorkbenchMetricConfig config
  ) {
    List<Map<String, Object>> rows = aggregate(
      appId, config.formName(), null, config.field(), config.aggregation(), 1);
    return new WorkbenchMetricData(numberValue(rows.isEmpty() ? null : rows.getFirst(), "value"));
  }

  private WorkbenchChartData chartData(
    String appId,
    dev.flexmodel.apps.workbench.dto.WorkbenchChartConfig config
  ) {
    List<Map<String, Object>> rows = aggregate(
      appId,
      config.formName(),
      config.dimensionField(),
      config.measureField(),
      config.aggregation(),
      MAX_CHART_GROUPS);
    var dimensionField = appFormService.findForm(appId, config.formName()).fields().stream()
      .filter(field -> config.dimensionField() != null && config.dimensionField().equals(field.name()))
      .findFirst()
      .orElse(null);
    Map<String, String> optionTexts = dimensionField == null || dimensionField.options() == null
      ? Map.of()
      : dimensionField.options().stream()
      .filter(option -> option.id() != null)
      .collect(Collectors.toMap(
        AppFormFieldOptionResponse::id,
        option -> option.text() == null || option.text().isBlank() ? option.id() : option.text(),
        (left, right) -> right));
    return new WorkbenchChartData(rows.stream()
      .map(row -> new WorkbenchDataPointRecord(
        dimensionValue(row.get("dimension"), dimensionField, optionTexts),
        numberValue(row, "value")))
      .toList());
  }

  private WorkbenchDataLoaderData dataLoaderData(
    String appId,
    dev.flexmodel.apps.workbench.dto.WorkbenchDataLoaderConfig config,
    int page,
    int size,
    String search
  ) {
    var records = appRecordService.findRecords(
      appId, config.formName(), page, size, search, null, null, null, null, null, null, null);
    return new WorkbenchDataLoaderData(records.list(), records.total());
  }

  private List<Map<String, Object>> aggregate(
    String appId,
    String formName,
    String dimensionField,
    String measureField,
    String aggregation,
    int limit
  ) {
    var form = appFormService.findForm(appId, formName);
    String datasourceName = form.builtin()
      ? EngineConfig.SYSTEM_DS_KEY
      : null;
    if (datasourceName == null) {
      return dataService.aggregateRecords(
        appId, formName, dimensionField, measureField, aggregation, limit);
    }
    return dataService.aggregateRecords(
      appId, datasourceName, formName, dimensionField, measureField, aggregation, limit);
  }

  private Number numberValue(Map<String, Object> row, String key) {
    Object value = row == null ? null : row.get(key);
    return value instanceof Number number ? number : 0;
  }

  private String dimensionValue(Object value, AppFormFieldResponse field, Map<String, String> optionTexts) {
    if (value == null || String.valueOf(value).isBlank()) return "未填写";
    String raw = String.valueOf(value);
    if (field != null && "BOOL".equals(field.type())) {
      return "true".equals(raw) ? "是" : "否";
    }
    return optionTexts.getOrDefault(raw, raw);
  }

  private int normalizedPage(Integer page) {
    if (page == null) {
      return 1;
    }
    if (page < 1) {
      throw new ValidationException("工作台组件数据页码不合法: " + page);
    }
    return page;
  }

  private int normalizedSize(Integer size) {
    if (size == null) {
      return 20;
    }
    if (size < 1 || size > MAX_DATA_LOADER_SIZE) {
      throw new ValidationException("工作台组件数据每页数量必须在 1 到 50 之间: " + size);
    }
    return size;
  }

  private ValidationException unsupportedComponent(String type) {
    return new ValidationException("工作台组件不支持真实数据查询: " + type);
  }
}
