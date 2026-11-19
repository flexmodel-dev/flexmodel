package dev.flexmodel.apps.view;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.dto.AppFormFieldOptionResponse;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.view.dto.AppViewGroup;
import dev.flexmodel.condition.ConditionParser;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AppViewServiceTest {
  private final AppViewService service = new AppViewService();

  private AppFormFieldResponse field(String name, String type) {
    return new AppFormFieldResponse(name, name, type, false, false, null, null);
  }

  @Test
  void calendarRangeIncludesOpenEndedEventsButNotInvalidRanges() {
    Map<String, Object> condition = service.rangeCondition(
      field("start", "DATE"), field("end", "DATE"), "2026-10-01", "2026-10-31", false);
    String json = JsonUtils.toJsonString(condition);
    assertTrue(json.contains("\"_eq\":null"));
    assertTrue(json.contains("\"_field\":\"start\""));
    assertTrue(json.contains("\"_gte\":\"2026-10-01\""));
    assertDoesNotThrow(() -> new ConditionParser().parse(json));
  }

  @Test
  void ganttRangeRequiresBothEndpoints() {
    Map<String, Object> condition = service.rangeCondition(
      field("start", "DATETIME"), field("end", "DATETIME"),
      "2026-10-01T00:00:00", "2026-11-01T00:00:00", true);
    String json = JsonUtils.toJsonString(condition);
    assertTrue(json.contains("\"start\":{\"_ne\":null}"));
    assertTrue(json.contains("\"end\":{\"_ne\":null}"));
    assertTrue(json.contains("\"_field\":\"start\""));
  }

  @Test
  void cycleDetectionClearsRepresentativeParentAndFlagsNodes() {
    LinkedHashMap<String, String> parents = new LinkedHashMap<>();
    parents.put("a", "b");
    parents.put("b", "c");
    parents.put("c", "b");
    parents.put("d", null);
    Set<String> anomalies = new LinkedHashSet<>();

    service.breakCycles(List.of("a", "b", "c", "d"), parents, anomalies);

    assertEquals(Set.of("b", "c"), anomalies);
    assertNull(parents.get("b"));
    assertEquals("b", parents.get("a"));
    assertNull(parents.get("d"));
  }

  @Test
  void dateMovePreservesDuration() {
    String movedEnd = service.moveTemporalEnd(
      field("start", "DATE"), "2026-01-01", "2026-01-04", "2026-01-02");
    assertEquals("2026-01-05", movedEnd);
  }

  @Test
  void datetimeMovePreservesDurationAndRejectsInvalidEnd() {
    AppFormFieldResponse field = field("start", "DATETIME");
    assertEquals("2026-01-01T11:30", service.moveTemporalEnd(
      field, "2026-01-01T09:00:00", "2026-01-01T10:30:00", "2026-01-01T10:00:00"));
    assertThrows(RuntimeException.class, () -> service.ensureEndAfterStart(
      field, "2026-01-01T10:00:00", "2026-01-01T10:00:00"));
  }

  @Test
  void optionGroupsKeepDefinitionOrderAndAppendUnknownValues() {
    AppFormFieldResponse field = new AppFormFieldResponse(
      "status", "状态", "SELECT", false, false,
      List.of(
        new AppFormFieldOptionResponse("a", "选项A", null, false),
        new AppFormFieldOptionResponse("b", "选项B", null, false)),
      null);
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put("b", 2L);
    counts.put("legacy", 1L);

    List<AppViewGroup> groups = service.optionGroups(field, counts);

    assertEquals(List.of("a", "b", "legacy"), groups.stream().map(AppViewGroup::value).toList());
    assertEquals("选项B", groups.get(1).label());
    assertEquals(2L, groups.get(1).total());
  }

  @Test
  void booleanGroupsHandleSqliteStyleValues() {
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put("1", 3L);
    counts.put("0", 2L);

    List<AppViewGroup> groups = service.booleanGroups(counts);

    assertEquals(3L, groups.getFirst().total());
    assertEquals(2L, groups.get(1).total());
    assertEquals(Boolean.TRUE, groups.getFirst().value());
  }
}
