package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.dto.WorkbenchComponentConfig;
import dev.flexmodel.apps.dto.WorkbenchComponentRecord;
import dev.flexmodel.apps.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.dto.WorkbenchLayoutItemRecord;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.codegen.entity.Workbench;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class WorkbenchServiceTest {

  @Inject
  WorkbenchService workbenchService;

  @Inject
  WorkbenchRepository workbenchRepository;

  @Test
  void testShortcutColumnsPerRowMustBeValid() {
    WorkbenchComponentConfig config = new WorkbenchComponentConfig(
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      List.of(),
      List.of(),
      13
    );
    WorkbenchComponentRecord component = new WorkbenchComponentRecord(
      "component_shortcuts",
      "快捷入口",
      "shortcuts",
      config
    );
    WorkbenchLayoutItemRecord item = new WorkbenchLayoutItemRecord(
      "component_shortcuts",
      0,
      0,
      6,
      3
    );
    WorkbenchCreateRequest request = new WorkbenchCreateRequest(
      "快捷入口每行数量校验-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(List.of(component), List.of(item))
    );

    assertThrows(ValidationException.class, () -> workbenchService.createWorkbench("dev_test", request));
  }

  @Test
  void testLegacyLayoutIsMigratedToExplicitGrid() {
    String workbenchId = UUID.randomUUID().toString();
    String projectId = "dev_test";
    LocalDateTime now = LocalDateTime.now();
    Workbench workbench = new Workbench();
    workbench.setId(workbenchId);
    workbench.setProjectId(projectId);
    workbench.setName("Legacy layout-" + workbenchId);
    workbench.setLayout(Map.of(
      "components", List.of(),
      "items", List.of(Map.of(
        "componentId", "component_shortcuts",
        "width", "HALF",
        "height", "SHORT"
      ))
    ));
    workbench.setCreatedBy("test");
    workbench.setUpdatedBy("test");
    workbench.setCreatedAt(now);
    workbench.setUpdatedAt(now);
    workbenchRepository.save(workbench);

    try {
      workbenchService.migrateLegacyWorkbenchLayout(workbench);

      Workbench migrated = workbenchRepository.findByProjectIdAndId(projectId, workbenchId);
      WorkbenchLayoutRecord layout = JsonUtils.convertValue(migrated.getLayout(), WorkbenchLayoutRecord.class);
      assertEquals(1, layout.items().size());
      assertEquals("component_shortcuts", layout.items().get(0).componentId());
      assertEquals(0, layout.items().get(0).x());
      assertEquals(0, layout.items().get(0).y());
      assertEquals(6, layout.items().get(0).w());
      assertEquals(3, layout.items().get(0).h());
    } finally {
      workbenchRepository.deleteByProjectIdAndId(projectId, workbenchId);
    }
  }
}
