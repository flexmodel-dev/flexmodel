package dev.flexmodel.model.field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility methods for relation filters.
 */
public final class RelationFilterSupport {

  private static final String FIELD_REFERENCE_KEY = "_field";
  private static final String AND_KEY = "_and";
  private static final String OR_KEY = "_or";

  private RelationFilterSupport() {
  }

  public static boolean hasSourceReference(Map<String, Object> filter, String relationFieldName) {
    return hasSourceReferenceInFilter(filter, relationFieldName);
  }

  public static boolean hasSourceTargetComparison(Map<String, Object> filter, String relationFieldName) {
    if (filter == null || filter.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      if (isLogicalKey(entry.getKey())) {
        if (hasSourceTargetComparisonInLogicalValue(entry.getValue(), relationFieldName)) {
          return true;
        }
        continue;
      }
      boolean keyTargetsRelation = isRelationPath(entry.getKey(), relationFieldName);
      Object value = entry.getValue();
      if (keyTargetsRelation && containsFieldReference(value, relationFieldName, false)) {
        return true;
      }
      if (!keyTargetsRelation && containsFieldReference(value, relationFieldName, true)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isSupportedRelationFilter(Map<String, Object> filter, String relationFieldName) {
    if (filter == null || filter.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      if (isLogicalKey(entry.getKey())) {
        if (!(entry.getValue() instanceof List<?> list)
          || list.isEmpty()
          || !list.stream().allMatch(item -> item instanceof Map<?, ?> map
          && isSupportedRelationFilter(toStringKeyMap(map), relationFieldName))) {
          return false;
        }
        continue;
      }
      if (!isRelationPath(entry.getKey(), relationFieldName)
        && !isSourceConditionSupported(entry.getValue(), relationFieldName)) {
        return false;
      }
    }
    return true;
  }

  public static Map<String, Object> resolveTargetFilter(
    Map<String, Object> filter,
    Map<String, Object> sourceData,
    String relationFieldName
  ) {
    if (filter == null || filter.isEmpty()) {
      return new LinkedHashMap<>();
    }
    return resolveFilter(filter, sourceData, relationFieldName);
  }

  public static Map<String, Object> replaceAliases(
    Map<String, Object> filter,
    String sourceAlias,
    String relationFieldName,
    String targetAlias
  ) {
    if (filter == null || filter.isEmpty()) {
      return new LinkedHashMap<>();
    }
    return replaceFilter(filter, sourceAlias, relationFieldName, targetAlias);
  }

  public static boolean isRelationPath(String path, String relationFieldName) {
    return path != null && relationFieldName != null && path.startsWith(relationFieldName + ".");
  }

  public static String stripRelationPath(String path, String relationFieldName) {
    if (!isRelationPath(path, relationFieldName)) {
      return path;
    }
    return path.substring(relationFieldName.length() + 1);
  }

  private static boolean hasSourceReferenceInFilter(Map<String, Object> filter, String relationFieldName) {
    if (filter == null || filter.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      String key = entry.getKey();
      if (isLogicalKey(key)) {
        if (hasSourceReferenceInLogicalValue(entry.getValue(), relationFieldName)) {
          return true;
        }
      } else if (!isRelationPath(key, relationFieldName) || hasSourceReferenceInValue(entry.getValue(), relationFieldName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasSourceReferenceInLogicalValue(Object value, String relationFieldName) {
    if (!(value instanceof List<?> list)) {
      return false;
    }
    for (Object item : list) {
      if (item instanceof Map<?, ?> map
        && hasSourceReferenceInFilter(toStringKeyMap(map), relationFieldName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasSourceReferenceInValue(Object value, String relationFieldName) {
    if (isFieldReference(value)) {
      return !isRelationPath(fieldReferencePath(value), relationFieldName);
    }
    if (value instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (hasSourceReferenceInValue(entry.getValue(), relationFieldName)) {
          return true;
        }
      }
      return false;
    }
    if (value instanceof List<?> list) {
      for (Object item : list) {
        if (hasSourceReferenceInValue(item, relationFieldName)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasSourceTargetComparisonInLogicalValue(Object value, String relationFieldName) {
    if (!(value instanceof List<?> list)) {
      return false;
    }
    for (Object item : list) {
      if (item instanceof Map<?, ?> map
        && hasSourceTargetComparison(toStringKeyMap(map), relationFieldName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsFieldReference(
    Object value,
    String relationFieldName,
    boolean targetReference
  ) {
    if (isFieldReference(value)) {
      return isRelationPath(fieldReferencePath(value), relationFieldName) == targetReference;
    }
    if (value instanceof Map<?, ?> map) {
      return map.values().stream().anyMatch(item -> containsFieldReference(item, relationFieldName, targetReference));
    }
    if (value instanceof List<?> list) {
      return list.stream().anyMatch(item -> containsFieldReference(item, relationFieldName, targetReference));
    }
    return false;
  }

  private static boolean isSourceConditionSupported(Object value, String relationFieldName) {
    if (!(value instanceof Map<?, ?> conditionMap) || conditionMap.isEmpty()) {
      return false;
    }
    for (Map.Entry<?, ?> entry : conditionMap.entrySet()) {
      String operator = Objects.toString(entry.getKey(), "");
      Object operand = entry.getValue();
      if (!operator.startsWith("_")
        || !isFieldReference(operand)
        || !isRelationPath(fieldReferencePath(operand), relationFieldName)) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, Object> resolveFilter(
    Map<String, Object> filter,
    Map<String, Object> sourceData,
    String relationFieldName
  ) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      String key = entry.getKey();
      if (isLogicalKey(key)) {
        result.put(key, resolveLogicalValue(entry.getValue(), sourceData, relationFieldName));
      } else if (isRelationPath(key, relationFieldName)) {
        result.put(stripRelationPath(key, relationFieldName),
          resolveTargetValue(entry.getValue(), sourceData, relationFieldName));
      } else {
        result.putAll(resolveSourceCondition(key, entry.getValue(), sourceData, relationFieldName));
      }
    }
    return result;
  }

  private static List<Object> resolveLogicalValue(
    Object value,
    Map<String, Object> sourceData,
    String relationFieldName
  ) {
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException("Logical operator expects an array of condition objects");
    }
    List<Object> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add(resolveFilter(toStringKeyMap(map), sourceData, relationFieldName));
      } else {
        result.add(item);
      }
    }
    return result;
  }

  private static Map<String, Object> resolveSourceCondition(
    String sourcePath,
    Object value,
    Map<String, Object> sourceData,
    String relationFieldName
  ) {
    if (!(value instanceof Map<?, ?> conditionMap)) {
      throw new IllegalArgumentException(
        "Source field condition " + sourcePath + " must compare a target field through _field");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : conditionMap.entrySet()) {
      String operator = Objects.toString(entry.getKey(), "");
      Object operand = entry.getValue();
      if (!operator.startsWith("_")) {
        throw new IllegalArgumentException("Unsupported condition operator: " + operator);
      }
      if (isFieldReference(operand)
        && isRelationPath(fieldReferencePath(operand), relationFieldName)) {
        Object sourceValue = getSourceValue(sourceData, sourcePath);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put(operator, sourceValue);
        result.put(stripRelationPath(fieldReferencePath(operand), relationFieldName), condition);
      } else {
        throw new IllegalArgumentException(
          "Source field condition " + sourcePath + " must compare a target field through _field");
      }
    }
    if (result.isEmpty()) {
      throw new IllegalArgumentException("Source field condition " + sourcePath + " must not be empty");
    }
    return result;
  }

  private static Object resolveTargetValue(
    Object value,
    Map<String, Object> sourceData,
    String relationFieldName
  ) {
    if (value instanceof Map<?, ?> map) {
      if (isFieldReference(value)) {
        String referencedPath = fieldReferencePath(value);
        if (isRelationPath(referencedPath, relationFieldName)) {
          return Map.of(FIELD_REFERENCE_KEY, stripRelationPath(referencedPath, relationFieldName));
        }
        return getSourceValue(sourceData, referencedPath);
      }
      Map<String, Object> resolved = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        resolved.put(Objects.toString(entry.getKey(), ""), resolveTargetValue(entry.getValue(), sourceData, relationFieldName));
      }
      return resolved;
    }
    if (value instanceof List<?> list) {
      List<Object> resolved = new ArrayList<>();
      for (Object item : list) {
        resolved.add(resolveTargetValue(item, sourceData, relationFieldName));
      }
      return resolved;
    }
    return value;
  }

  private static Map<String, Object> replaceFilter(
    Map<String, Object> filter,
    String sourceAlias,
    String relationFieldName,
    String targetAlias
  ) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      String key = entry.getKey();
      if (isLogicalKey(key)) {
        result.put(key, replaceLogicalValue(entry.getValue(), sourceAlias, relationFieldName, targetAlias));
      } else {
        result.put(replaceFieldPath(key, sourceAlias, relationFieldName, targetAlias),
          replaceConditionValue(entry.getValue(), sourceAlias, relationFieldName, targetAlias));
      }
    }
    return result;
  }

  private static List<Object> replaceLogicalValue(
    Object value,
    String sourceAlias,
    String relationFieldName,
    String targetAlias
  ) {
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException("Logical operator expects an array of condition objects");
    }
    List<Object> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add(replaceFilter(toStringKeyMap(map), sourceAlias, relationFieldName, targetAlias));
      } else {
        result.add(item);
      }
    }
    return result;
  }

  private static Object replaceConditionValue(
    Object value,
    String sourceAlias,
    String relationFieldName,
    String targetAlias
  ) {
    if (value instanceof Map<?, ?> map) {
      if (isFieldReference(value)) {
        return Map.of(FIELD_REFERENCE_KEY,
          replaceFieldPath(fieldReferencePath(value), sourceAlias, relationFieldName, targetAlias));
      }
      Map<String, Object> replaced = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        replaced.put(Objects.toString(entry.getKey(), ""),
          replaceConditionValue(entry.getValue(), sourceAlias, relationFieldName, targetAlias));
      }
      return replaced;
    }
    if (value instanceof List<?> list) {
      List<Object> replaced = new ArrayList<>();
      for (Object item : list) {
        replaced.add(replaceConditionValue(item, sourceAlias, relationFieldName, targetAlias));
      }
      return replaced;
    }
    return value;
  }

  private static String replaceFieldPath(
    String path,
    String sourceAlias,
    String relationFieldName,
    String targetAlias
  ) {
    if (isRelationPath(path, relationFieldName)) {
      return targetAlias + "." + stripRelationPath(path, relationFieldName);
    }
    if (path.startsWith(sourceAlias + ".") || path.startsWith(targetAlias + ".")) {
      return path;
    }
    return sourceAlias + "." + path;
  }

  private static boolean isLogicalKey(String key) {
    return AND_KEY.equals(key) || OR_KEY.equals(key);
  }

  private static boolean isFieldReference(Object value) {
    return value instanceof Map<?, ?> map
      && map.size() == 1
      && map.containsKey(FIELD_REFERENCE_KEY)
      && map.get(FIELD_REFERENCE_KEY) != null;
  }

  private static String fieldReferencePath(Object value) {
    return Objects.toString(((Map<?, ?>) value).get(FIELD_REFERENCE_KEY), "");
  }

  private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      result.put(Objects.toString(entry.getKey(), ""), entry.getValue());
    }
    return result;
  }

  private static Object getSourceValue(Map<String, Object> sourceData, String sourcePath) {
    if (sourceData.containsKey(sourcePath)) {
      return sourceData.get(sourcePath);
    }
    String camelCasePath = underscoreToCamelCase(sourcePath);
    if (!Objects.equals(camelCasePath, sourcePath) && sourceData.containsKey(camelCasePath)) {
      return sourceData.get(camelCasePath);
    }
    throw new IllegalArgumentException("Source field " + sourcePath + " is missing from source data");
  }

  private static String underscoreToCamelCase(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (char character : path.toCharArray()) {
      if (character == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(character));
        capitalizeNext = false;
      } else {
        result.append(Character.toLowerCase(character));
      }
    }
    return result.toString();
  }
}
