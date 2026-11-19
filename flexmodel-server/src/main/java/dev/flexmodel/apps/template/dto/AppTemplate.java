package dev.flexmodel.apps.template.dto;

import java.util.List;

/**
 * 内置应用模板目录协议（app-templates/index.json）与列表响应。
 *
 * @see docs/plans/app-templates.md
 */
public final class AppTemplate {
  private AppTemplate() {
  }

  public record Index(List<Entry> templates) {
  }

  public record Entry(String id, String name, String description, String category,
                      String iconName, Integer order) {
  }

  public record Summary(String id, String name, String description, String category,
                        String iconName, int formCount, boolean includesData, String exportedAt) {
  }
}
