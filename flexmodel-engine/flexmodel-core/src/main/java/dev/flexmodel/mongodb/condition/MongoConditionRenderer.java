package dev.flexmodel.mongodb.condition;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.condition.ConditionNode;
import dev.flexmodel.condition.FieldReference;
import dev.flexmodel.condition.ConditionOperator;
import dev.flexmodel.condition.FieldConditionNode;
import dev.flexmodel.condition.LogicalConditionNode;
import dev.flexmodel.model.field.RelationFilterSupport;

import java.util.*;

import static dev.flexmodel.condition.ConditionParser.toCollection;

/**
 * 将条件语法树渲染为 Mongo 查询 JSON。
 */
public final class MongoConditionRenderer {

  private MongoConditionRenderer() {
  }

  public static String render(ConditionNode node) {
    return render(node, null);
  }

  public static String render(ConditionNode node, String relationFieldName) {
    Map<String, Object> document = renderNode(node, relationFieldName);
    if (document.isEmpty()) {
      return "{}";
    }
    return JsonUtils.toJsonString(document);
  }

  private static Map<String, Object> renderNode(ConditionNode node, String relationFieldName) {
    if (node == null || node.isEmpty()) {
      return Map.of();
    }
    if (node instanceof LogicalConditionNode logical) {
      return renderLogical(logical, relationFieldName);
    }
    if (node instanceof FieldConditionNode field) {
      return renderField(field, relationFieldName);
    }
    throw new IllegalArgumentException("Unknown condition node: " + node);
  }

  private static Map<String, Object> renderLogical(LogicalConditionNode logical, String relationFieldName) {
    List<Map<String, Object>> children = new ArrayList<>();
    for (ConditionNode child : logical.getChildren()) {
      Map<String, Object> doc = renderNode(child, relationFieldName);
      if (!doc.isEmpty()) {
        children.add(doc);
      }
    }
    if (children.isEmpty()) {
      return Map.of();
    }
    if (logical.getOperator() == ConditionOperator.AND) {
      if (children.size() == 1) {
        return children.get(0);
      }
      return Map.of("$and", children);
    }
    return Map.of("$or", children);
  }

  private static Map<String, Object> renderField(FieldConditionNode field, String relationFieldName) {
    boolean relationContext = relationFieldName != null;
    boolean isTargetField = isRelationField(field.getFieldPath(), relationFieldName);
    String name = isTargetField
      ? stripRelationField(field.getFieldPath(), relationFieldName)
      : field.getFieldPath();
    Object value = field.getValue();
    if (value instanceof FieldReference reference) {
      String left = isTargetField || !relationContext ? "$" + name : sourceOperand(name);
      String right = fieldReferenceOperand(reference.path(), relationFieldName);
      return switch (field.getOperator()) {
        case EQ -> expression("$eq", left, right);
        case NE -> expression("$ne", left, right);
        case GT -> expression("$gt", left, right);
        case GTE -> expression("$gte", left, right);
        case LT -> expression("$lt", left, right);
        case LTE -> expression("$lte", left, right);
        default -> throw new IllegalArgumentException("Field references only support comparison operators");
      };
    }
    if (relationContext && !isTargetField) {
      return renderSourceField(name, field.getOperator(), value, relationFieldName);
    }
    return switch (field.getOperator()) {
      case EQ -> fieldCondition(name, "$eq", value);
      case NE -> fieldCondition(name, "$ne", value);
      case GT -> fieldCondition(name, "$gt", value);
      case GTE -> fieldCondition(name, "$gte", value);
      case LT -> fieldCondition(name, "$lt", value);
      case LTE -> fieldCondition(name, "$lte", value);
      case IN -> collectionCondition(name, "$in", toCollection(value), relationFieldName);
      case NIN -> collectionCondition(name, "$nin", toCollection(value), relationFieldName);
      case BETWEEN -> renderBetween(name, value);
      case CONTAINS -> renderRegex(name, value, "contains", false);
      case NOT_CONTAINS -> renderRegex(name, value, "contains", true);
      case STARTS_WITH -> renderRegex(name, value, "starts", false);
      case ENDS_WITH -> renderRegex(name, value, "ends", false);
      default -> throw new IllegalStateException("Unsupported operator for Mongo: " + field.getOperator());
    };
  }

  private static Map<String, Object> renderSourceField(
    String name,
    ConditionOperator operator,
    Object value,
    String relationFieldName
  ) {
    String field = sourceOperand(name);
    return switch (operator) {
      case EQ -> expression("$eq", field, value);
      case NE -> expression("$ne", field, value);
      case GT -> expression("$gt", field, value);
      case GTE -> expression("$gte", field, value);
      case LT -> expression("$lt", field, value);
      case LTE -> expression("$lte", field, value);
      case IN -> expression("$in", field, renderOperands(toCollection(value), relationFieldName));
      case NIN -> expression("$nin", field, renderOperands(toCollection(value), relationFieldName));
      case BETWEEN -> renderSourceBetween(field, value);
      default -> throw new IllegalArgumentException(
        "Source fields only support comparison, in, nin and between operators");
    };
  }

  private static Map<String, Object> renderSourceBetween(String field, Object value) {
    Collection<?> collection = toCollection(value);
    if (collection.size() != 2) {
      throw new IllegalArgumentException("_between operator expects exactly 2 values");
    }
    Object[] values = collection.toArray();
    Map<String, Object> lowerBound = new LinkedHashMap<>();
    lowerBound.put("$gte", Arrays.asList(field, values[0]));
    Map<String, Object> upperBound = new LinkedHashMap<>();
    upperBound.put("$lte", Arrays.asList(field, values[1]));
    List<Object> conditions = List.of(
      lowerBound,
      upperBound
    );
    return Map.of("$expr", Map.of("$and", conditions));
  }

  private static Map<String, Object> expression(String operator, String field, Object value) {
    List<Object> arguments = new ArrayList<>();
    arguments.add(field);
    arguments.add(value);
    return Map.of("$expr", Map.of(operator, arguments));
  }

  private static String fieldReferenceOperand(String path, String relationFieldName) {
    if (relationFieldName == null) {
      return "$" + path;
    }
    if (isRelationField(path, relationFieldName)) {
      return "$" + RelationFilterSupport.stripRelationPath(path, relationFieldName);
    }
    return sourceOperand(path);
  }

  private static String sourceOperand(String path) {
    return "$$" + FieldReference.variableName(path);
  }

  private static boolean isRelationField(String path, String relationFieldName) {
    return RelationFilterSupport.isRelationPath(path, relationFieldName);
  }

  private static String stripRelationField(String path, String relationFieldName) {
    return RelationFilterSupport.stripRelationPath(path, relationFieldName);
  }

  private static Map<String, Object> fieldCondition(String name, String operator, Object value) {
    return Collections.singletonMap(name, Collections.singletonMap(operator, value));
  }

  private static Map<String, Object> collectionCondition(
    String name,
    String operator,
    Collection<?> values,
    String relationFieldName
  ) {
    if (values.stream().anyMatch(MongoConditionRenderer::isFieldReference)) {
      return expression(operator, "$" + name, renderOperands(values, relationFieldName));
    }
    return fieldCondition(name, operator, new ArrayList<>(values));
  }

  private static List<Object> renderOperands(Collection<?> values, String relationFieldName) {
    List<Object> operands = new ArrayList<>(values.size());
    for (Object value : values) {
      if (value instanceof FieldReference reference) {
        operands.add(fieldReferenceOperand(reference.path(), relationFieldName));
      } else {
        operands.add(value);
      }
    }
    return operands;
  }

  private static boolean isFieldReference(Object value) {
    return value instanceof FieldReference;
  }
  private static Map<String, Object> renderBetween(String name, Object value) {
    Collection<?> collection = toCollection(value);
    if (collection.size() != 2) {
      throw new IllegalArgumentException("_between operator expects exactly 2 values");
    }
    Object[] values = collection.toArray();
    Map<String, Object> ranges = new LinkedHashMap<>();
    ranges.put("$gte", values[0]);
    ranges.put("$lte", values[1]);
    return Map.of(name, ranges);
  }

  private static Map<String, Object> renderRegex(String name, Object value, String mode, boolean negate) {
    if (value instanceof Collection<?> collection) {
      if (collection.isEmpty()) {
        return Map.of();
      }
      String operator = negate ? "$nin" : "$in";
      return Map.of(name, Map.of(operator, new ArrayList<>(collection)));
    }
    if (value == null) {
      return Map.of();
    }
    String pattern = switch (mode) {
      case "starts" -> "^" + escapeRegex(value.toString()) + ".*";
      case "ends" -> ".*" + escapeRegex(value.toString()) + "$";
      case "contains" -> ".*" + escapeRegex(value.toString()) + ".*";
      default -> value.toString();
    };
    Map<String, Object> regex = Map.of("$regex", pattern);
    if (negate) {
      return Map.of(name, Map.of("$not", regex));
    }
    return Map.of(name, regex);
  }

  private static String escapeRegex(String value) {
    StringBuilder sb = new StringBuilder();
    for (char c : value.toCharArray()) {
      if ("\\.[]{}()*+-?^$|".indexOf(c) >= 0) {
        sb.append("\\");
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
