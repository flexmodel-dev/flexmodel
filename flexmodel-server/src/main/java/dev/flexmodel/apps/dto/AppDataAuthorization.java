package dev.flexmodel.apps.dto;

import java.util.List;
import java.util.Map;

/**
 * 一条规则包含主表与各子表的控制明细，不按操作拆行。
 */
public record AppDataAuthorization(
  String id, String appId, String formName, String name, boolean enabled,
  List<Selection> subjects, List<Selection> dataScope, List<Control> controls
) {
  public record Selection(String type, List<String> ids, boolean includeChildren) {
  }

  public record FieldControl(boolean read, boolean write) {
  }

  public record Control(String formName, boolean readAll, boolean writeAll, boolean allowDelete,
                        Map<String, FieldControl> fieldControls, Map<String, Object> condition) {
  }
}
