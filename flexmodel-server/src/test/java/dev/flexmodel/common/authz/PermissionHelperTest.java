package dev.flexmodel.common.authz;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PermissionHelper wildcard matching 单元测试。
 */
class PermissionHelperTest {

  @Test
  void singleAndMatch() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:view"), new String[]{"modeling:view"}, Logical.AND));
  }

  @Test
  void singleAndMismatch() {
    assertFalse(PermissionHelper.isPermitted(Set.of("modeling:view"), new String[]{"modeling:create"}, Logical.AND));
  }

  @Test
  void multipleAndAllMatch() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:view", "modeling:create"), new String[]{"modeling:view", "modeling:create"}, Logical.AND));
  }

  @Test
  void multipleAndPartialMatch() {
    assertFalse(PermissionHelper.isPermitted(Set.of("modeling:view"), new String[]{"modeling:view", "modeling:create"}, Logical.AND));
  }

  @Test
  void singleOrMatch() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:create"), new String[]{"modeling:view", "modeling:create"}, Logical.OR));
  }

  @Test
  void singleOrNoMatch() {
    assertFalse(PermissionHelper.isPermitted(Set.of("data:view"), new String[]{"modeling:view", "modeling:create"}, Logical.OR));
  }

  @Test
  void globalWildcardImpliesAll() {
    assertTrue(PermissionHelper.isPermitted(Set.of("*"), new String[]{"modeling:view"}, Logical.AND));
    assertTrue(PermissionHelper.isPermitted(Set.of("*"), new String[]{"data:Classes:create"}, Logical.AND));
  }

  @Test
  void groupWildcardImpliesSub() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:*"), new String[]{"modeling:view"}, Logical.AND));
  }

  @Test
  void groupWildcardDoesNotImplyOtherGroup() {
    assertFalse(PermissionHelper.isPermitted(Set.of("modeling:*"), new String[]{"data:view"}, Logical.AND));
  }

  @Test
  void perModelWildcardImpliesSub() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:Classes:*"), new String[]{"modeling:Classes:view"}, Logical.AND));
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:Classes:*"), new String[]{"modeling:Classes:create"}, Logical.AND));
  }

  @Test
  void perModelWildcardDoesNotImplyOtherModel() {
    assertFalse(PermissionHelper.isPermitted(Set.of("modeling:Classes:*"), new String[]{"modeling:Course:view"}, Logical.AND));
  }

  @Test
  void groupWildcardImpliesPerModel() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:*"), new String[]{"modeling:Classes:view"}, Logical.AND));
  }

  @Test
  void perModelWildcardDoesNotImplyGroupLevel() {
    assertFalse(PermissionHelper.isPermitted(Set.of("modeling:Classes:*"), new String[]{"modeling:view"}, Logical.AND));
  }

  @Test
  void perModelExactImpliesSelf() {
    assertTrue(PermissionHelper.isPermitted(Set.of("modeling:Classes:view"), new String[]{"modeling:Classes:view"}, Logical.AND));
  }

  @Test
  void perModelExactDoesNotImplyOtherOp() {
    assertFalse(PermissionHelper.isPermitted(Set.of("modeling:Classes:view"), new String[]{"modeling:Classes:create"}, Logical.AND));
  }

  @Test
  void dataWildcard() {
    assertTrue(PermissionHelper.isPermitted(Set.of("data:*"), new String[]{"data:Classes:view"}, Logical.AND));
    assertTrue(PermissionHelper.isPermitted(Set.of("data:*"), new String[]{"data:Course:create"}, Logical.AND));
    assertFalse(PermissionHelper.isPermitted(Set.of("data:*"), new String[]{"modeling:view"}, Logical.AND));
  }

  @Test
  void emptyPermissionsDeny() {
    assertFalse(PermissionHelper.isPermitted(Set.of(), new String[]{"modeling:view"}, Logical.AND));
  }

  @Test
  void otherGroupExecutes() {
    assertTrue(PermissionHelper.isPermitted(Set.of("graphql:execute"), new String[]{"graphql:execute"}, Logical.AND));
    assertFalse(PermissionHelper.isPermitted(Set.of("graphql:execute"), new String[]{"flow:execute"}, Logical.AND));
  }

  @Test
  void schedulingPermissions() {
    assertTrue(PermissionHelper.isPermitted(Set.of("scheduling:*"), new String[]{"scheduling:view"}, Logical.AND));
    assertTrue(PermissionHelper.isPermitted(Set.of("scheduling:execute"), new String[]{"scheduling:execute"}, Logical.AND));
    assertFalse(PermissionHelper.isPermitted(Set.of("scheduling:view"), new String[]{"scheduling:execute"}, Logical.AND));
  }

  @Test
  void functionPermissions() {
    assertTrue(PermissionHelper.isPermitted(Set.of("function:execute"), new String[]{"function:execute"}, Logical.AND));
    assertFalse(PermissionHelper.isPermitted(Set.of("function:view"), new String[]{"function:execute"}, Logical.AND));
  }

  @Test
  void mixedPermissionsWithOneMatch() {
    assertTrue(PermissionHelper.isPermitted(
      Set.of("data:Classes:view"),
      new String[]{"modeling:view", "data:Classes:view"},
      Logical.OR));
  }
}
