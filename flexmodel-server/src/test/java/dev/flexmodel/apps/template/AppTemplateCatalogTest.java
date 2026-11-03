package dev.flexmodel.apps.template;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.apppackage.AppPackageValidator;
import dev.flexmodel.apps.apppackage.AppPackageValidator.Result;
import dev.flexmodel.apps.apppackage.AppPackageZip;
import dev.flexmodel.apps.apppackage.AppPackageZip.Content;
import dev.flexmodel.apps.template.dto.AppTemplate;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内置模板目录：索引容错、包可用性与身份可移植性。
 */
class AppTemplateCatalogTest {
  /**
   * 平台种子目录：每个实例都会初始化 admin 用户与 admin/developer 角色。
   */
  private static final Map<String, Object> PLATFORM_DIRECTORY = Map.of(
    "members", List.of(Map.of("id", "admin")),
    "roles", List.of(Map.of("id", "admin"), Map.of("id", "developer")),
    "organizations", List.of());
  private static final Set<String> PLATFORM_ROLES = Set.of("admin", "developer");

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private AppTemplateCatalog catalog() {
    AppTemplateCatalog catalog = new AppTemplateCatalog();
    catalog.json = json;
    catalog.packages = packages();
    catalog.validator = new AppPackageValidator();
    return catalog;
  }

  private AppPackageZip packages() {
    AppPackageZip zip = new AppPackageZip();
    zip.json = json;
    return zip;
  }

  private InputStream classpath(String name) {
    return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
  }

  private byte[] builtInPackage(String id) throws IOException {
    try (InputStream input = classpath("app-templates/" + id + ".flexmodel.zip")) {
      assertNotNull(input, "内置模板包缺失: " + id);
      return input.readAllBytes();
    }
  }

  private Function<String, InputStream> source(Map<String, byte[]> resources) {
    return name -> Optional.ofNullable(resources.get(name)).map(ByteArrayInputStream::new).orElse(null);
  }

  private Map<String, byte[]> resources(String index, String id, byte[] pkg) {
    Map<String, byte[]> resources = new HashMap<>();
    resources.put(AppTemplateCatalog.BASE + "index.json", index.getBytes(StandardCharsets.UTF_8));
    if (id != null) resources.put(AppTemplateCatalog.BASE + id + ".flexmodel.zip", pkg);
    return resources;
  }

  @Test
  void loadsBuiltInTemplates() {
    AppTemplateCatalog catalog = catalog();
    catalog.load(this::classpath);
    assertFalse(catalog.summaries().isEmpty(), "应至少内置一个应用模板");
    // 目录里的 index 条目必须全部可加载，防止坏包被静默跳过后前端少模板。
    assertEquals(4, catalog.summaries().size());
    AppTemplate.Summary erp = catalog.summaries().stream()
      .filter(summary -> "erp".equals(summary.id())).findFirst().orElseThrow();
    assertEquals("ERP 企业资源计划", erp.name());
    assertEquals(34, erp.formCount());
    assertTrue(erp.includesData());
    assertNotNull(erp.exportedAt());
    assertEquals("erp", catalog.require("erp").summary().id());
    AppTemplate.Summary scm = catalog.summaries().stream()
      .filter(summary -> "scm".equals(summary.id())).findFirst().orElseThrow();
    assertEquals("SCM 供应链管理", scm.name());
    assertEquals(11, scm.formCount());
    assertEquals("scm", catalog.require("scm").summary().id());
    AppTemplate.Summary wms = catalog.summaries().stream()
      .filter(summary -> "wms".equals(summary.id())).findFirst().orElseThrow();
    assertEquals("WMS 仓库管理系统", wms.name());
    assertEquals(13, wms.formCount());
    assertEquals("wms", catalog.require("wms").summary().id());
  }

  @Test
  void rejectsUnknownOrInvalidTemplateId() {
    AppTemplateCatalog catalog = catalog();
    catalog.load(this::classpath);
    assertThrows(WebApplicationException.class, () -> catalog.require("missing"));
    assertThrows(WebApplicationException.class, () -> catalog.require("../erp"));
    assertThrows(WebApplicationException.class, () -> catalog.require(null));
  }

  @Test
  void emptyCatalogWhenIndexUnreadable() {
    AppTemplateCatalog catalog = catalog();
    catalog.load(name -> null);
    assertTrue(catalog.summaries().isEmpty());
  }

  @Test
  void emptyCatalogWhenIndexHasUnknownFields() {
    AppTemplateCatalog catalog = catalog();
    catalog.load(source(resources("""
      {"templates":[{"id":"demo","name":"演示","extra":true}]}
      """, null, null)));
    assertTrue(catalog.summaries().isEmpty());
  }

  @Test
  void skipsInvalidDuplicateMissingAndBrokenEntries() throws IOException {
    byte[] pkg = builtInPackage("erp");
    String index = """
      {"templates":[
        {"id":"demo","name":"演示模板","order":2},
        {"id":"demo","name":"重复模板","order":1},
        {"id":"../evil","name":"非法ID"},
        {"id":"missing","name":"缺失包"},
        {"id":"broken","name":"损坏包"}
      ]}
      """;
    Map<String, byte[]> resources = resources(index, "demo", pkg);
    resources.put(AppTemplateCatalog.BASE + "broken.flexmodel.zip",
      "not-a-zip".getBytes(StandardCharsets.UTF_8));
    AppTemplateCatalog catalog = catalog();
    catalog.load(source(resources));
    assertEquals(1, catalog.summaries().size());
    assertEquals("demo", catalog.summaries().getFirst().id());
  }

  @Test
  void skipsSemanticallyInvalidPackage() throws IOException {
    java.nio.file.Path temporary = java.nio.file.Files.createTempFile("template", ".zip");
    try {
      // 协议版本不支持但 ZIP 可解析，目录加载必须做语义校验而不是只解析包。
      AppPackageZip.Content invalid = new AppPackageZip.Content(
        new dev.flexmodel.apps.apppackage.dto.AppPackage.Manifest("flexmodel-app-package", 99, "2026-10-31T00:00:00Z",
          new dev.flexmodel.apps.apppackage.dto.AppPackage.ManifestSource("source", "test"), List.of(), List.of()),
        new dev.flexmodel.apps.apppackage.dto.AppPackage.AppInfo("无效模板", "", "Boxes", "blue"),
        List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of());
      packages().write(temporary, invalid);
      byte[] bytes = java.nio.file.Files.readAllBytes(temporary);
      AppTemplateCatalog catalog = catalog();
      catalog.load(source(resources("""
        {"templates":[{"id":"invalid","name":"无效模板"}]}
        """, "invalid", bytes)));
      assertTrue(catalog.summaries().isEmpty());
    } finally {
      java.nio.file.Files.deleteIfExists(temporary);
    }
  }

  @Test
  void builtInTemplatesArePortable() throws IOException {
    AppTemplateCatalog catalog = catalog();
    catalog.load(this::classpath);
    AppPackageZip packages = packages();
    AppPackageValidator validator = new AppPackageValidator();
    for (AppTemplate.Summary summary : catalog.summaries()) {
      Content content = packages.read(new ByteArrayInputStream(builtInPackage(summary.id())));
      Result result = validator.validate(content, PLATFORM_DIRECTORY, (form, id) -> false);
      assertTrue(result.valid(), summary.id() + " 在目标实例基线目录下不可导入: " + result.issues());
      assertTrue(result.warnings().isEmpty(),
        summary.id() + " 存在不可移植的示例数据警告: " + result.warnings());
      assertTrue(content.authorizations().isEmpty(), summary.id() + " 不应携带数据权限规则");
      assertPublicNavigation(content.navigation(), summary.id());
      assertPortableApprovals(content.approvals(), summary.id());
      assertPortableData(content, summary.id());
    }
  }

  /**
   * 示例数据不得依赖源环境附件对象，也不得包含表单中不存在的字段（会被静默丢弃）。
   */
  private void assertPortableData(Content content, String templateId) {
    Map<String, Set<String>> attachmentFields = new HashMap<>();
    for (var form : content.forms()) {
      Set<String> fields = new LinkedHashSet<>();
      for (var field : form.fields() == null ? List.<dev.flexmodel.apps.form.dto.AppFormFieldRecord>of() : form.fields()) {
        if (!field.builtin() && ("FILE".equals(field.type()) || "IMAGE".equals(field.type()))) fields.add(field.name());
      }
      if (!fields.isEmpty()) attachmentFields.put(form.name(), fields);
    }
    content.data().forEach((formName, rows) -> {
      Set<String> fields = attachmentFields.getOrDefault(formName, Set.of());
      for (Map<String, Object> row : rows)
        for (String field : fields)
          assertTrue(row.get(field) == null || String.valueOf(row.get(field)).isBlank(),
            templateId + " 示例数据包含附件字段值: " + formName + "." + field);
    });
  }

  private void assertPublicNavigation(List<dev.flexmodel.apps.navigation.dto.AppNavRecord> navs, String templateId) {
    for (var nav : navs == null ? List.<dev.flexmodel.apps.navigation.dto.AppNavRecord>of() : navs) {
      assertTrue(nav.visibility() == null || nav.visibility().isEmpty(),
        templateId + " 导航 " + nav.id() + " 不应引用目录成员");
      assertPublicNavigation(nav.children(), templateId);
    }
  }

  private void assertPortableApprovals(List<Map<String, Object>> approvals, String templateId) {
    Set<String> allowedSources = Set.of("initiator", "node", "variable", "roles");
    for (Map<String, Object> flow : approvals == null ? List.<Map<String, Object>>of() : approvals) {
      for (Map<String, Object> node : flatten(flow.get("definition") instanceof Map<?, ?> definition
        ? definition.get("nodes") : null)) {
        Object config = node.get("data") instanceof Map<?, ?> data ? data.get("config") : null;
        if (!(config instanceof Map<?, ?> configMap)) continue;
        for (Map<String, Object> rule : objects(configMap.get("rules"))) {
          String source = String.valueOf(rule.get("source"));
          assertTrue(allowedSources.contains(source),
            templateId + " 审批执行人使用了不可移植来源: " + source);
          if ("roles".equals(source))
            ruleValues(rule).forEach(role -> assertTrue(PLATFORM_ROLES.contains(role),
              templateId + " 审批执行人引用了非平台角色: " + role));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> flatten(Object nodes) {
    List<Map<String, Object>> result = new java.util.ArrayList<>();
    for (Object item : nodes instanceof List<?> list ? list : List.of()) {
      if (!(item instanceof Map<?, ?> node)) continue;
      result.add((Map<String, Object>) node);
      result.addAll(flatten(node.get("blocks")));
    }
    return result;
  }

  private List<Map<String, Object>> objects(Object value) {
    List<Map<String, Object>> result = new java.util.ArrayList<>();
    for (Object item : value instanceof List<?> list ? list : List.of())
      if (item instanceof Map<?, ?> map) result.add(castMap(map));
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  private Set<String> ruleValues(Map<String, Object> rule) {
    Set<String> values = new LinkedHashSet<>();
    for (Object value : rule.get("values") instanceof List<?> list ? list : List.of())
      values.add(String.valueOf(value));
    return values;
  }
}
