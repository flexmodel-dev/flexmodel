package dev.flexmodel.apps.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiToolChangesTest {
  @Test
  void createdAppUsesActualResultId() {
    var change = AiToolChanges.from("create_app", "{}", "{\"id\":\"generated-app\"}", false);
    assertNotNull(change);
    assertEquals("generated-app", change.appId());
    assertEquals("app", change.resource());
  }

  @Test
  void formAndWorkbenchChangesUseActualTargetRatherThanConversationContext() {
    var form = AiToolChanges.from("create_app_form", "{\"appId\":\"other-app\",\"name\":\"orders\"}",
      "{\"name\":\"orders\"}", false);
    assertNotNull(form);
    assertEquals("other-app", form.appId());
    assertEquals("orders", form.formName());
    var layout = AiToolChanges.from("save_app_workbench_layout", "{\"appId\":\"other-app\"}",
      "{\"id\":\"dashboard\"}", false);
    assertNotNull(layout);
    assertEquals("workbench", layout.resource());
    assertEquals("dashboard", layout.entityId());
  }

  @Test
  void readonlyFailedAndInvalidCallsDoNotProduceChanges() {
    assertNull(AiToolChanges.from("list_apps", "{}", "[]", false));
    assertNull(AiToolChanges.from("create_app", "{}", "Error: permission denied", false));
    assertNull(AiToolChanges.from("create_app", "{}", "{\"id\":\"a\"}", true));
    assertNull(AiToolChanges.from("create_app", "{}", "{}", false));
    assertNull(AiToolChanges.from("create_app_form", "invalid", "{}", false));
  }

  @Test
  void partialRecordBatchStillProducesChangeForCommittedRecords() {
    String args = "{\"appId\":\"a\",\"formName\":\"orders\"}";
    assertNull(AiToolChanges.from("create_app_mock_records", args,
      "Error: create_app_mock_records failed at record index 1 - invalid", false));
    var partial = AiToolChanges.from("create_app_mock_records", args,
      "Error: create_app_mock_records failed at record index 2 - invalid", false);
    assertNotNull(partial);
    assertEquals("record", partial.resource());
    assertEquals("orders", partial.formName());
    assertNull(AiToolChanges.from("create_app_mock_records", args,
      "Error: create_app_mock_records failed at record index 9999999999999999999999 - invalid", false));
  }
}
