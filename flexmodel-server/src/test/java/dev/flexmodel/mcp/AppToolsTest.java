package dev.flexmodel.mcp;

import dev.flexmodel.apps.form.dto.AppFormFieldControls;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppToolsTest {

  @Test
  void parsesFieldControlsFromJson() {
    Map<String, AppFormFieldControls> controls = AppTools.parseControls("""
      {
        "title": {
          "hidden": {
            "scenes": ["CREATE", "UPDATE"],
            "condition": {"status": {"_eq": "REJECTED"}}
          },
          "required": {"scenes": ["CREATE"]}
        },
        "amount": {
          "readonly": {"scenes": ["UPDATE"]}
        }
      }
      """);

    assertEquals(2, controls.size());
    AppFormFieldControls title = controls.get("title");
    assertEquals(List.of("CREATE", "UPDATE"), title.hidden().scenes());
    assertEquals(Map.of("status", Map.of("_eq", "REJECTED")), title.hidden().condition());
    assertEquals(List.of("CREATE"), title.required().scenes());
    assertNull(title.required().condition());
    assertNull(title.readonly());
    assertEquals(List.of("UPDATE"), controls.get("amount").readonly().scenes());
  }

  @Test
  void parsesEmptyControlsAsClearRequest() {
    assertTrue(AppTools.parseControls("{}").isEmpty());
  }

  @Test
  void rejectsNonObjectControls() {
    assertThrows(IllegalArgumentException.class, () -> AppTools.parseControls("[]"));
    assertThrows(IllegalArgumentException.class, () -> AppTools.parseControls("null"));
  }
}
