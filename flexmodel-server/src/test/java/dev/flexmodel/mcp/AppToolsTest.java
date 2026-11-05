package dev.flexmodel.mcp;

import dev.flexmodel.apps.form.dto.AppFormFieldControls;
import dev.flexmodel.apps.form.dto.AppFormViewTabRecord;
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

  @Test
  void parsesViewTabsFromJson() {
    List<AppFormViewTabRecord> views = AppTools.parseViews("""
      [
        {"id": "all", "label": "全部", "type": "TABLE", "sort": {"field": "created_at", "order": "DESC"}},
        {"label": "高优先级", "type": "TABLE", "filter": {"_and": [{"priority": {"_eq": "high"}}]}},
        {"label": "状态看板", "type": "KANBAN", "displayFields": ["name", "status"],
         "config": {"groupField": "status", "titleField": "name"}}
      ]
      """);

    assertEquals(3, views.size());
    AppFormViewTabRecord all = views.get(0);
    assertEquals("all", all.id());
    assertEquals("全部", all.label());
    assertEquals("TABLE", all.type());
    assertEquals("created_at", all.sort().field());
    assertEquals("DESC", all.sort().order());
    AppFormViewTabRecord filtered = views.get(1);
    assertNull(filtered.id());
    assertEquals(Map.of("_and", List.of(Map.of("priority", Map.of("_eq", "high")))), filtered.filter());
    assertNull(filtered.sort());
    AppFormViewTabRecord kanban = views.get(2);
    assertEquals("KANBAN", kanban.type());
    assertEquals(List.of("name", "status"), kanban.displayFields());
    assertEquals("status", kanban.config().groupField());
    assertEquals("name", kanban.config().titleField());
    assertNull(kanban.config().detailMode());
  }

  @Test
  void rejectsNonArrayViewTabs() {
    assertThrows(RuntimeException.class, () -> AppTools.parseViews("{}"));
    assertThrows(RuntimeException.class, () -> AppTools.parseViews("null"));
  }
}
