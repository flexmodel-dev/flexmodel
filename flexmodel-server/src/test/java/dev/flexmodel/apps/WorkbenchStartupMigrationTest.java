package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.codegen.entity.Workbench;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(WorkbenchLegacyMigrationTestResource.class)
class WorkbenchStartupMigrationTest {

  @Inject
  WorkbenchRepository workbenchRepository;

  @AfterEach
  void cleanUp() {
    workbenchRepository.deleteByProjectIdAndId("dev_test", "workbench-legacy");
  }

  @Test
  void testStartupMigratesLegacyLayoutAfterEngineInitialization() {
    Workbench workbench = workbenchRepository.findByProjectIdAndId("dev_test", "workbench-legacy");
    WorkbenchLayoutRecord layout = JsonUtils.convertValue(workbench.getLayout(), WorkbenchLayoutRecord.class);

    assertEquals(1, layout.items().size());
    assertEquals("component_shortcuts", layout.items().get(0).componentId());
    assertEquals(0, layout.items().get(0).x());
    assertEquals(0, layout.items().get(0).y());
    assertEquals(6, layout.items().get(0).w());
    assertEquals(3, layout.items().get(0).h());
  }
}
