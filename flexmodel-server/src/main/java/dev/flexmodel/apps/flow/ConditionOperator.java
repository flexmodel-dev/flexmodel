package dev.flexmodel.apps.flow;

import dev.flexmodel.common.ValidationException;
import dev.flexmodel.query.FilterExpression;
import dev.flexmodel.query.Predicate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Single source of the condition operator contract: validation, SQL predicates, in-memory
 * evaluation and the MCP capability enums. The JavaScript twin is ApprovalFlowModel.condition.
 */
public enum ConditionOperator {
  EQ("eq"), NE("ne"), GT("gt"), GTE("gte"), LT("lt"), LTE("lte"), CONTAINS("contains"), IS_EMPTY("isEmpty"), NOT_EMPTY("notEmpty");

  public static final List<String> NAMES = Arrays.stream(values()).map(item -> item.wire).toList();
  private final String wire;

  ConditionOperator(String wire) {
    this.wire = wire;
  }

  public static ConditionOperator parse(String name) {
    for (var operator : values())
      if (operator.wire.equals(name)) return operator;
    throw new ValidationException("条件操作符无效");
  }

  public static boolean valid(String name) {
    return NAMES.contains(name);
  }

  public Predicate test(FilterExpression<Object> field, Object right) {
    return switch (this) {
      case EQ -> field.eq(right);
      case NE -> field.ne(right);
      case GT -> field.gt(right);
      case GTE -> field.gte(right);
      case LT -> field.lt(right);
      case LTE -> field.lte(right);
      case CONTAINS -> field.contains(right);
      case IS_EMPTY, NOT_EMPTY -> throw new ValidationException("集合空值判断仅用于流程分支或事件条件");
    };
  }

  public boolean apply(Object left, Object right) {
    return switch (this) {
      case IS_EMPTY -> empty(left);
      case NOT_EMPTY -> !empty(left);
      case EQ -> equals(left, right);
      case NE -> !equals(left, right);
      case GT -> left != null && compare(left, right) > 0;
      case GTE -> left != null && compare(left, right) >= 0;
      case LT -> left != null && compare(left, right) < 0;
      case LTE -> left != null && compare(left, right) <= 0;
      case CONTAINS -> left instanceof Collection<?> values ? values.contains(right)
        : left != null && left.toString().contains(Objects.toString(right, ""));
    };
  }

  private static boolean empty(Object value) {
    return value == null || value instanceof String s && s.isEmpty() || value instanceof Collection<?> c && c.isEmpty() || value instanceof java.util.Map<?, ?> m && m.isEmpty();
  }

  private static boolean equals(Object left, Object right) {
    return left instanceof Number && right instanceof Number ? compare(left, right) == 0 : Objects.equals(left, right);
  }

  private static int compare(Object left, Object right) {
    if (left instanceof Number && right instanceof Number)
      return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
    if (left != null && right != null) return left.toString().compareTo(right.toString());
    return 0;
  }
}
