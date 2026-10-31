package dev.flexmodel.apps.workbench;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.workbench.dto.*;
import dev.flexmodel.common.ValidationException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class AppWorkbenchServiceTest {

  @Inject
  AppWorkbenchService appWorkbenchService;

  @Test
  void capabilityCatalogDescribesTargetSchema() {
    WorkbenchCapabilityResponse capability = appWorkbenchService.findCapabilities("dev_test");
    List<WorkbenchComponentSpecRecord> components = capability.components();

    assertEquals(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, capability.schemaVersion());
    assertEquals(7, components.size());
    assertEquals(List.of("metric", "chart", "chart", "chart", "dataloader", "shortcuts", "tasks"),
      components.stream().map(WorkbenchComponentSpecRecord::type).toList());
    assertEquals(List.of("line", "bar", "pie"),
      components.stream()
        .filter(item -> "chart".equals(item.type()))
        .map(WorkbenchComponentSpecRecord::chartType)
        .toList());
    assertTrue(components.stream()
      .filter(item -> "chart".equals(item.type()))
      .noneMatch(item -> item.defaultConfig().containsKey("measureField")));
    assertNotNull(capability.forms());
    assertEquals(List.of("count", "sum", "avg"), capability.availableValues().get("aggregation"));
    assertInstanceOf(WorkbenchTasksConfig.class,
      capability.exampleLayout().components().getFirst().config());
  }

  @Test
  void shortcutColumnsPerRowMustBeValid() {
    WorkbenchComponentRecord component = new WorkbenchComponentRecord(
      "component_shortcuts",
      "快捷入口",
      new WorkbenchPlacementRecord(0, 0, 6, 3),
      new WorkbenchShortcutsConfig(List.of(), 13));
    WorkbenchCreateRequest request = new WorkbenchCreateRequest(
      "快捷入口每行数量校验-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, List.of(component)));

    assertThrows(ValidationException.class, () -> appWorkbenchService.createWorkbench("dev_test", request));
  }

  @Test
  void chartConfigMustUseValidAggregation() {
    WorkbenchComponentRecord component = new WorkbenchComponentRecord(
      "component_chart",
      "销售趋势",
      new WorkbenchPlacementRecord(0, 0, 6, 4),
      new WorkbenchChartConfig(
        "line", "f_user", "name", null, "invalid",
        false, true, true, "primary"));
    WorkbenchCreateRequest request = new WorkbenchCreateRequest(
      "图表配置校验-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, List.of(component)));

    assertThrows(ValidationException.class, () -> appWorkbenchService.createWorkbench("dev_test", request));
  }

  @Test
  void chartTypeControlsSmoothAndAggregationControlsMeasureField() {
    appWorkbenchService.findWorkbenches("dev_test");
    assertThrows(ValidationException.class, () -> createChartWorkbench("line", "count", null, null));
    for (String chartType : List.of("bar", "pie")) {
      var saved = createChartWorkbench(chartType, "count", null, null);
      try {
        var savedConfig = saved.layout().components().getFirst().config();
        assertInstanceOf(WorkbenchChartConfig.class, savedConfig);
        assertNull(((WorkbenchChartConfig) savedConfig).smooth());
      } finally {
        appWorkbenchService.deleteWorkbench("dev_test", saved.id());
      }
    }
    assertThrows(ValidationException.class, () -> createChartWorkbench("bar", "count", "name", null));
    assertThrows(ValidationException.class, () -> createChartWorkbench("bar", "sum", null, null));
  }

  @Test
  void metricAggregationFieldMustBeNumericAndChartDimensionMustBeGroupable() {
    WorkbenchComponentRecord metric = new WorkbenchComponentRecord(
      "component_metric",
      "核心指标",
      new WorkbenchPlacementRecord(0, 0, 3, 3),
      new WorkbenchMetricConfig("f_user", "sum", "name"));
    WorkbenchCreateRequest metricRequest = new WorkbenchCreateRequest(
      "指标字段校验-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, List.of(metric)));
    assertThrows(ValidationException.class, () -> appWorkbenchService.createWorkbench("dev_test", metricRequest));

    WorkbenchComponentRecord chart = new WorkbenchComponentRecord(
      "component_chart",
      "用户分组",
      new WorkbenchPlacementRecord(0, 0, 6, 4),
      new WorkbenchChartConfig(
        "line", "f_user", "manager_user_id", null, "count",
        false, true, true, "primary"));
    WorkbenchCreateRequest chartRequest = new WorkbenchCreateRequest(
      "图表分组校验-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, List.of(chart)));
    assertThrows(ValidationException.class, () -> appWorkbenchService.createWorkbench("dev_test", chartRequest));
  }

  private WorkbenchChartConfig chartConfig(
    String chartType,
    String aggregation,
    String measureField,
    Boolean smooth
  ) {
    return new WorkbenchChartConfig(
      chartType, "f_user", "name", measureField, aggregation, false, true, smooth, "primary");
  }

  private dev.flexmodel.apps.workbench.dto.WorkbenchResponse createChartWorkbench(
    String chartType,
    String aggregation,
    String measureField,
    Boolean smooth
  ) {
    WorkbenchComponentRecord component = new WorkbenchComponentRecord(
      "component_chart_" + chartType,
      "Chart type validation-" + chartType,
      new WorkbenchPlacementRecord(0, 0, 6, 4),
      chartConfig(chartType, aggregation, measureField, smooth));
    WorkbenchCreateRequest request = new WorkbenchCreateRequest(
      "Chart type validation-" + chartType + "-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, List.of(component)));
    return appWorkbenchService.createWorkbench("dev_test", request);
  }
}
