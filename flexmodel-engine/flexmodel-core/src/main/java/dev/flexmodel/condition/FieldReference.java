package dev.flexmodel.condition;

import java.util.Locale;

public record FieldReference(String path) {

  public FieldReference {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Field reference path must not be blank");
    }
  }

  public static String variableName(String path) {
    String variable = path.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
    if (!variable.matches("[A-Za-z][A-Za-z0-9_]*")) {
      variable = "v_" + variable;
    }
    if (path.contains(".")) {
      variable += "_" + Integer.toUnsignedString(path.hashCode(), 36);
    }
    return variable;
  }

  public String variableName() {
    return variableName(path);
  }
}
