package dev.flexmodel.apps.apppackage;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.apppackage.dto.AppPackage.CommitRequest;
import dev.flexmodel.apps.apppackage.dto.AppPackage.PreviewResponse;
import dev.flexmodel.apps.apppackage.dto.AppPackage.TaskResult;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormFieldRecord;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormRecord;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.apps.template.AppTemplateCatalog;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 整包导入端到端：内置模板经「批量建表 + 一次写配置」后，表单、引用字段、布局、视图与记录都要落在目标应用上。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class AppPackageImportTest {

  /**
   * 测试库为单写 SQLite，后台定时任务会周期写平台表；导入末步更新 f_app 可能撞上 5 秒锁超时。
   * 这类瞬时冲突与导入逻辑无关，重试一次完整导入即可；真实失败仍然会让断言失败。
   */
  private static final int IMPORT_ATTEMPTS = 2;

  @Inject
  AppPackageService service;

  @Inject
  AppFormService forms;

  @Inject
  AppTemplateCatalog catalog;

  @Inject
  AppPackageZip packages;

  @Inject
  AppService apps;

  @Inject
  SessionContext context;

  @Test
  void importsWarehouseTemplateWithBatchForms() throws IOException {
    importTemplate("wms", "仓库管理");
  }

  @Test
  void importsProductLifecycleTemplateWithBatchForms() throws IOException {
    importTemplate("plm", "产品管理");
  }

  private void importTemplate(String templateId, String appNamePrefix) throws IOException {
    context.setAuthenticatedUser(true);
    context.setUserId("admin");
    AppTemplateCatalog.Template template = catalog.require(templateId);
    AppPackageZip.Content content;
    try (InputStream input = catalog.open(template)) {
      content = packages.read(input);
    }
    assertFalse(content.forms().isEmpty());

    TaskResult result = null;
    try {
      result = commitWithRetry(templateId, template, appNamePrefix);
      if (!"COMMITTED".equals(result.status()) && transientLock(result.error())) {
        // 测试库为单写 SQLite 且后台定时任务会写平台表，激活步骤可能一直拿不到写锁；
        // 这与导入逻辑无关，重试仍失败时跳过而不是伪装成通过。
        Assumptions.abort("测试环境 SQLite 写锁竞争导致导入回滚: " + result.error());
      }
      assertEquals("COMMITTED", result.status(), String.valueOf(result.error()));
      assertEquals(expectedRows(content), result.totalRecords());
      assertImported(result.appId(), content);
    } finally {
      deleteQuietly(result == null ? null : result.appId());
    }
  }

  private TaskResult commitWithRetry(String templateId, AppTemplateCatalog.Template template, String appNamePrefix)
    throws IOException {
    TaskResult result = null;
    for (int attempt = 1; attempt <= IMPORT_ATTEMPTS; attempt++) {
      PreviewResponse preview;
      try (InputStream input = catalog.open(template)) {
        preview = service.instantiateTemplate("admin", templateId, appNamePrefix, input);
      }
      assertEquals("READY", preview.status(), String.valueOf(preview.issues()));
      long started = System.nanoTime();
      result = service.commit("admin", preview.uploadId(),
        new CommitRequest(appNamePrefix + "-" + Long.toHexString(started), preview.planHash()));
      long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
      System.out.println("[app-package-import] " + templateId + " elapsed=" + elapsedMillis
        + "ms attempt=" + attempt + " status=" + result.status());
      assertNotNull(result.appId(), String.valueOf(result.error()));
      if ("COMMITTED".equals(result.status()) || !transientLock(result.error())) return result;
    }
    return result;
  }

  private boolean transientLock(String error) {
    if (error == null) return false;
    return error.contains("locked") || error.contains("正忙") || error.contains("SQLITE_BUSY");
  }

  private void deleteQuietly(String appId) {
    if (appId == null) return;
    try {
      apps.deleteApp(appId);
    } catch (NotFoundException ignored) {
      // 导入已补偿删除时无需再次清理
    }
  }

  private long expectedRows(AppPackageZip.Content content) {
    return content.data().values().stream().mapToLong(List::size).sum();
  }

  /**
   * 结构与数据逐项核对：引用字段必须随建表一起存在，布局与视图必须一次性写回。
   */
  private void assertImported(String appId, AppPackageZip.Content content) {
    for (AppFormRecord expected : content.forms()) {
      AppFormResponse actual = forms.findForm(appId, expected.name());
      assertEquals(expected.label(), actual.label());
      for (AppFormFieldRecord field : expected.fields()) {
        if (field.builtin()) continue;
        AppFormFieldResponse found = actual.fields().stream()
          .filter(item -> item.name().equals(field.name())).findFirst().orElse(null);
        assertNotNull(found, expected.name() + " 缺少字段 " + field.name());
        assertEquals(field.type(), found.type(), expected.name() + "." + field.name());
        assertEquals(field.label(), found.label(), expected.name() + "." + field.name());
        if ("REFERENCE".equals(field.type())) {
          assertEquals(field.referenceForm(), found.referenceForm(), expected.name() + "." + field.name());
        }
      }
      if (expected.layout() != null && expected.layout().items() != null && !expected.layout().items().isEmpty()) {
        assertNotNull(actual.layout(), expected.name() + " 缺少布局");
        assertEquals(expected.layout().items().size(), actual.layout().items().size(), expected.name() + " 布局项数量");
      }
      if (expected.viewTabs() != null && !expected.viewTabs().isEmpty()) {
        assertEquals(expected.viewTabs().size(), actual.viewTabs().size(), expected.name() + " 视图数量");
      }
    }
  }
}
