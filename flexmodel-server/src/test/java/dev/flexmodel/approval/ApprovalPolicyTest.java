package dev.flexmodel.approval;

import dev.flexmodel.approval.ApprovalRecordActions;
import dev.flexmodel.directory.DirectoryService;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalPolicyTest {
  @Test
  void signatures() {
    for (String mode : List.of("all", "any", "sequential"))
      assertEquals("REJECTED", SignPolicy.outcome(mode, List.of("approve", "reject", "")));
    assertEquals("APPROVED", SignPolicy.outcome("any", List.of("approve", "")));
    assertEquals("WAITING", SignPolicy.outcome("all", List.of("approve", "")));
    assertEquals("WAITING", SignPolicy.outcome("sequential", List.of("approve", "")));
    assertEquals("APPROVED", SignPolicy.outcome("all", List.of("approve", "approve")));
  }

  @Test
  void relativeScopesAndStableDeduplication() {
    DirectoryService resolver = new DirectoryService();
    Map<String, Object> directory = Map.of("members", List.of(
        member("boss", "", List.of("manager"), List.of("root")), member("a", "boss", List.of("worker", "manager"), List.of("child")),
        member("b", "boss", List.of("worker"), List.of("child")), member("c", "a", List.of("worker"), List.of("leaf"))),
      "organizations", List.of(Map.of("id", "root", "parentId", ""), Map.of("id", "child", "parentId", "root"), Map.of("id", "leaf", "parentId", "child")));
    assertEquals(List.of("a", "b"), resolver.resolve(List.of(rule("users", List.of("boss"), "directReports")), "a", Map.of(), directory));
    assertEquals(List.of("a", "b", "c"), resolver.resolve(List.of(rule("users", List.of("boss"), "allReports")), "a", Map.of(), directory));
    assertEquals(List.of("boss"), resolver.resolve(List.of(rule("initiator", List.of(), "manager")), "a", Map.of(), directory));
    assertEquals(List.of("b", "a", "boss"), resolver.resolve(List.of(rule("users", List.of("b", "a"), "self"), rule("roles", List.of("manager"), "self")), "a", Map.of(), directory));
    assertEquals(List.of("boss", "a", "b", "c"), resolver.resolve(List.of(rule("organizations", List.of("root"), "self")), "a", Map.of(), directory));
  }

  @Test
  void typedConditions() {
    Map<String, Object> number = Map.of("field", "record.amount", "operator", "gt", "value", Map.of("kind", "literal", "valueType", "number", "value", "100"));
    assertTrue(ApprovalRecordActions.matches(Map.of("match", "all", "conditions", List.of(number)), Map.of("record", Map.of("amount", 101))));
    assertFalse(ApprovalRecordActions.matches(Map.of("match", "all", "conditions", List.of(number)), Map.of("record", Map.of("amount", 9))));
  }

  @Test
  void removedMemberSourceIsRejected() {
    DirectoryService resolver = new DirectoryService();
    var error = assertThrows(dev.flexmodel.common.ValidationException.class,
      () -> resolver.resolve(List.of(rule("script", List.of(), "self")), "admin", Map.of(), Map.of("members", List.of(), "organizations", List.of())));
    assertEquals("执行人来源已不支持，请重新选择", error.getMessage());
  }

  private Map<String, Object> member(String id, String manager, List<String> roles, List<String> orgs) {
    return Map.of("id", id, "managerId", manager, "roleIds", roles, "organizationIds", orgs);
  }

  private Map<String, Object> rule(String source, List<String> ids, String scope) {
    return Map.of("source", source, "values", ids, "scope", scope, "includeChildren", true);
  }
}
