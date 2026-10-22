package dev.flexmodel.approval;

import java.util.List;

public final class SignPolicy {
  private SignPolicy() {
  }

  public static String outcome(String mode, List<String> decisions) {
    if (decisions.contains("reject")) return "REJECTED";
    if (mode.equals("any") && decisions.contains("approve")) return "APPROVED";
    if (!decisions.isEmpty() && decisions.stream().allMatch("approve"::equals)) return "APPROVED";
    return "WAITING";
  }
}
