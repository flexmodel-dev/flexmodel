package dev.flexmodel.apps;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.dto.WorkbenchCapabilityResponse;
import dev.flexmodel.apps.dto.WorkbenchChartConfig;
import dev.flexmodel.apps.dto.WorkbenchComponentRecord;
import dev.flexmodel.apps.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.apps.dto.WorkbenchPlacementRecord;
import dev.flexmodel.apps.dto.WorkbenchShortcutsConfig;
import dev.flexmodel.apps.dto.WorkbenchTasksConfig;
import dev.flexmodel.common.ValidationException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class WorkbenchServiceTest {

  @Inject
  WorkbenchService workbenchService;

  @Test
  void capabilityCatalogDescribesTargetSchema() {
    WorkbenchCapabilityResponse capability = workbenchService.findCapabilities("dev_test");

    assertEquals(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, capability.schemaVersion());
    assertEquals(List.of("metric", "chart", "dataloader", "shortcuts", "tasks"),
      capability.components().stream().map(item -> item.type()).toList());
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

    assertThrows(ValidationException.class, () -> workbenchService.createWorkbench("dev_test", request));
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

    assertThrows(ValidationException.class, () -> workbenchService.createWorkbench("dev_test", request));
  }
}
