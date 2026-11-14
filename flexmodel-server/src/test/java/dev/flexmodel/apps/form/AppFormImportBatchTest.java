package dev.flexmodel.apps.form;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.AppRepository;
import dev.flexmodel.apps.dataauthorization.AppDataAuthorizationService;
import dev.flexmodel.apps.form.dto.AppFormFieldRecord;
import dev.flexmodel.apps.form.dto.AppFormItemRecord;
import dev.flexmodel.apps.form.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.form.dto.AppFormRecord;
import dev.flexmodel.apps.form.dto.AppFormViewTabRecord;
import dev.flexmodel.apps.navigation.AppNavigationRepository;
import dev.flexmodel.codegen.entity.App;
import dev.flexmodel.codegen.entity.AppForm;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.StringField;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.modeling.ModelingService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 整包导入的表单批量重建：一次建表（含引用字段）、一次写配置。
 */
class AppFormImportBatchTest {

  private static final class InMemoryFormRepository implements AppFormRepository {
    final Map<String, List<AppForm>> rows = new LinkedHashMap<>();
    int deletes;
    int saves;

    @Override
    public List<AppForm> findByAppId(String appId) {
      return new ArrayList<>(rows.getOrDefault(appId, List.of()));
    }

    @Override
    public AppForm save(AppForm form) {
      rows.computeIfAbsent(form.getAppId(), ignored -> new ArrayList<>()).add(form);
      saves++;
      return form;
    }

    @Override
    public void deleteByAppId(String appId) {
      rows.remove(appId);
      deletes++;
    }
  }

  private static final class StubModelingService extends ModelingService {
    final List<EntityDefinition> created = new ArrayList<>();
    final List<String> alteredFields = new ArrayList<>();

    @Override
    public SchemaObject createModel(String projectId, SchemaObject model) {
      created.add((EntityDefinition) model);
      return model;
    }

    @Override
    public List<SchemaObject> findModels(String projectId) {
      return new ArrayList<>(created);
    }

    @Override
    public TypedField<?, ?> createField(String projectId, TypedField<?, ?> field) {
      alteredFields.add(field.getName());
      return field;
    }
  }

  private static final class StubAppRepository implements AppRepository {
    final App app = new App();
    int saves;

    StubAppRepository() {
      app.setId("app-1");
    }

    @Override
    public List<App> findApps() {
      return List.of(app);
    }

    @Override
    public App findApp(String appId) {
      return app;
    }

    @Override
    public App save(App value) {
      saves++;
      return value;
    }

    @Override
    public void reorder(List<String> appIds) {
    }

    @Override
    public void delete(String appId) {
    }
  }

  private static AppFormService service(InMemoryFormRepository forms, StubModelingService modeling) {
    AppFormService service = new AppFormService();
    service.appFormRepository = forms;
    service.appRepository = new StubAppRepository();
    service.modelingService = modeling;
    service.sessionContext = new SessionContext();
    service.sessionContext.setUserId("user-1");
    service.appNavRecordStore = new AppNavigationRepository() {
      @Override
      public Project requireAppProject(String appId) {
        Project project = new Project();
        project.setId(appId);
        project.setName("app");
        return project;
      }
    };
    service.dataAuthorization = new AppDataAuthorizationService() {
      @Override
      public void reconcile(String app, Set<String> liveForms, Map<String, Set<String>> children) {
      }
    };
    return service;
  }

  private static AppFormFieldRecord field(String name, String label, String type, boolean required,
                                          String referenceForm) {
    return new AppFormFieldRecord(name, label, type, required, false, null, referenceForm);
  }

  private static AppFormRecord record(String name, String label, List<AppFormFieldRecord> fields,
                                      AppFormLayoutRecord layout, List<AppFormViewTabRecord> viewTabs) {
    return new AppFormRecord(name, label, "", "name", "name",
      LocalDateTime.now(), LocalDateTime.now(), fields, layout, viewTabs);
  }

  private static AppFormRecord orderForm() {
    return record("f_order", "订单", List.of(
        field("name", "名称", "TEXT", true, null),
        field("customer", "客户", "REFERENCE", true, "f_customer"),
        field("amount", "金额", "NUMBER", false, null)),
      new AppFormLayoutRecord(List.of()), List.of());
  }

  private static AppFormRecord customerForm() {
    return record("f_customer", "客户", List.of(
        field("name", "名称", "TEXT", true, null),
        field("code", "编码", "TEXT", false, null)),
      new AppFormLayoutRecord(List.of()), List.of());
  }

  @Test
  void buildsEachFormOnceWithReferenceColumnsInline() {
    InMemoryFormRepository forms = new InMemoryFormRepository();
    StubModelingService modeling = new StubModelingService();
    AppFormService service = service(forms, modeling);

    service.createFormsForImport("app-1", List.of(customerForm(), orderForm()));

    // 每个表单只建一次模型；引用字段随建表一起创建，不再逐字段 ALTER
    assertEquals(2, modeling.created.size());
    assertTrue(modeling.alteredFields.isEmpty(), modeling.alteredFields.toString());
    EntityDefinition order = modeling.created.stream()
      .filter(entity -> entity.getName().equals("f_order")).findFirst().orElseThrow();
    TypedField<?, ?> customer = order.getField("customer");
    assertNotNull(customer);
    assertEquals(64, ((StringField) customer).getLength());
    assertTrue(order.getField("amount") != null);
    // 配置只写一次：一次 delete + 两条 insert
    assertEquals(1, forms.deletes);
    assertEquals(2, forms.saves);
    assertEquals("name", stored("app-1", "f_order", forms).displayField());
  }

  @Test
  void allowsMutualReferencesInsideThePackage() {
    InMemoryFormRepository forms = new InMemoryFormRepository();
    StubModelingService modeling = new StubModelingService();
    AppFormService service = service(forms, modeling);

    AppFormRecord first = record("f_a", "A", List.of(
      field("name", "名称", "TEXT", true, null),
      field("b_id", "B", "REFERENCE", false, "f_b")), new AppFormLayoutRecord(List.of()), List.of());
    AppFormRecord second = record("f_b", "B", List.of(
      field("name", "名称", "TEXT", true, null),
      field("a_id", "A", "REFERENCE", false, "f_a")), new AppFormLayoutRecord(List.of()), List.of());

    service.createFormsForImport("app-1", List.of(first, second));

    assertEquals(2, modeling.created.size());
    assertEquals(64, ((StringField) modeling.created.get(0).getField("b_id")).getLength());
    assertEquals(64, ((StringField) modeling.created.get(1).getField("a_id")).getLength());
  }

  @Test
  void keepsDisplayFieldMetadataOnTheColumn() {
    InMemoryFormRepository forms = new InMemoryFormRepository();
    StubModelingService modeling = new StubModelingService();
    AppFormService service = service(forms, modeling);

    AppFormRecord optionalTitle = record("f_title", "标题表单", List.of(
      field("name", "标题", "TEXT", false, null)), new AppFormLayoutRecord(List.of()), List.of());

    service.createFormsForImport("app-1", List.of(optionalTitle));

    EntityDefinition entity = modeling.created.getFirst();
    assertEquals("标题", entity.getField("name").getComment());
    assertTrue(entity.getField("name").isNullable());
    AppFormRecord stored = stored("app-1", "f_title", forms);
    assertEquals("标题", stored.fields().stream()
      .filter(item -> item.name().equals("name")).findFirst().orElseThrow().label());
  }

  @Test
  void appliesLayoutAndViewsInOneWrite() {
    InMemoryFormRepository forms = new InMemoryFormRepository();
    StubModelingService modeling = new StubModelingService();
    AppFormService service = service(forms, modeling);
    service.createFormsForImport("app-1", List.of(customerForm()));

    AppFormRecord withDesign = record("f_customer", "客户", List.of(
        field("name", "名称", "TEXT", true, null),
        field("code", "编码", "TEXT", false, null)),
      new AppFormLayoutRecord(List.of(
        new AppFormItemRecord("FIELD", "name", "名称", null, "HALF", null, null, null, null),
        new AppFormItemRecord("FIELD", "code", "编码", null, "HALF", null, null, null, null))),
      List.of(AppFormService.allView()));

    service.applyFormLayoutsForImport("app-1", List.of(withDesign));

    AppFormRecord stored = stored("app-1", "f_customer", forms);
    assertEquals(2, stored.layout().items().size());
    assertEquals(1, stored.viewTabs().size());
    // 创建一次 + 布局一次，共两次整批写入
    assertEquals(2, forms.deletes);
    assertEquals(2, forms.saves);
  }

  @Test
  void rejectsReferenceOutsideThePackage() {
    InMemoryFormRepository forms = new InMemoryFormRepository();
    StubModelingService modeling = new StubModelingService();
    AppFormService service = service(forms, modeling);

    AppFormRecord ghost = record("f_order", "订单", List.of(
        field("name", "名称", "TEXT", true, null),
        field("customer", "客户", "REFERENCE", true, "f_missing")),
      new AppFormLayoutRecord(List.of()), List.of());

    assertThrows(ValidationException.class,
      () -> service.createFormsForImport("app-1", List.of(ghost)));
    assertTrue(modeling.created.isEmpty());
  }

  private static AppFormRecord stored(String appId, String formName, InMemoryFormRepository forms) {
    return forms.findByAppId(appId).stream()
      .filter(row -> row.getName().equals(formName))
      .map(row -> JsonUtils.convertValue(row.getContent(), AppFormRecord.class))
      .findFirst().orElseThrow();
  }
}
