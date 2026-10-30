package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppFormViewTabRecord;
import dev.flexmodel.apps.dto.AppReferenceValueResponse;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppRecordServiceTest {

  @Test
  void findRecordsExpandsReferenceFields() {
    AppFormFieldResponse nameField = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormFieldResponse referenceField = new AppFormFieldResponse(
      "customer_id", "Customer", "REFERENCE", false, false, null, "customer");
    AppFormResponse form = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(nameField, referenceField), null, List.of());
    RecordingDataService dataService = new RecordingDataService();
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(form);
    service.dataService = dataService;
    service.sessionContext = new SessionContext();

    service.findRecords(
      "app", "orders", 1, 20, null, null, null, null, null, null, null, null);

    assertEquals(List.of("customer_id"), dataService.expand);
  }

  @Test
  void rendersReferenceValuesForListAndDetail() {
    AppFormFieldResponse orderName = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormFieldResponse customerReference = new AppFormFieldResponse(
      "customer_id", "Customer", "REFERENCE", false, false, null, "customer");
    AppFormResponse orderForm = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(orderName, customerReference),
      layout("name", "customer_id"), List.of());
    AppFormFieldResponse customerName = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormResponse customerForm = new AppFormResponse(
      "customer", "Customer", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(customerName), null, List.of());
    RenderingDataService dataService = new RenderingDataService();
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(Map.of(
      "orders", orderForm,
      "customer", customerForm));
    service.dataService = dataService;
    service.sessionContext = new SessionContext();

    AppReferenceValueResponse expected = new AppReferenceValueResponse(
      "customer-1", "Customer One", "customer");
    PageDTO<Map<String, Object>> page = service.findRecords(
      "app", "orders", 1, 20, null, null, null, null, null, null, null, null);
    assertEquals(expected, page.list().getFirst().get("customer_id"));

    Map<String, Object> record = service.findRecord(
      "app", "orders", "order-1", null, null, null);
    assertEquals(expected, record.get("customer_id"));
  }

  @Test
  void findRecordsOnlyReturnsFieldsUsedInLayout() {
    AppFormFieldResponse nameField = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormFieldResponse secretField = new AppFormFieldResponse(
      "secret", "Secret", "TEXT", false, false, null, null);
    AppFormResponse form = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(nameField, secretField),
      layout("name"), List.of());
    RenderingDataService dataService = new RenderingDataService();
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(form);
    service.dataService = dataService;
    service.sessionContext = new SessionContext();

    Map<String, Object> record = service.findRecords(
        "app", "orders", 1, 20, null, null, null, null, null, null, null, null)
      .list().getFirst();

    assertEquals(Set.of("id", "name"), record.keySet());
  }

  @Test
  void findRecordsAppliesViewTabFilter() {
    AppFormFieldResponse nameField = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormResponse form = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(nameField), null,
      List.of(new AppFormViewTabRecord("zhang", "张姓", Map.of(
        "_and", List.of(Map.of("name", Map.of("_contains", "张")))))));
    RecordingDataService dataService = new RecordingDataService();
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(form);
    service.dataService = dataService;
    service.sessionContext = new SessionContext();

    service.findRecords(
      "app", "orders", 1, 20, null, null, null, "zhang", null, null, null, null);

    List<?> conditions = (List<?>) JsonUtils.parseToMap(dataService.filter).get("_and");
    assertEquals(Map.of("name", Map.of("_contains", "张")), conditions.getFirst());
  }

  @Test
  void findRecordsCombinesViewTabAndSearchFilters() {
    AppFormFieldResponse nameField = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormResponse form = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(nameField), null,
      List.of(new AppFormViewTabRecord("zhang", "张姓", Map.of(
        "_and", List.of(Map.of("name", Map.of("_contains", "张")))))));
    RecordingDataService dataService = new RecordingDataService();
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(form);
    service.dataService = dataService;
    service.sessionContext = new SessionContext();

    service.findRecords(
      "app", "orders", 1, 20, "三", null, null, "zhang", null, null, null, null);

    Map<String, Object> filter = JsonUtils.parseToMap(dataService.filter);
    assertEquals(2, ((List<?>) filter.get("_and")).size());
  }

  @Test
  void findRecordsRejectsUnknownViewTab() {
    AppFormFieldResponse nameField = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormResponse form = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(nameField), null, List.of());
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(form);
    service.dataService = new RecordingDataService();
    service.sessionContext = new SessionContext();

    assertThrows(dev.flexmodel.common.ValidationException.class, () -> service.findRecords(
      "app", "orders", 1, 20, null, null, null, "unknown", null, null, null, null));
  }

  private static final class StubAppFormService extends AppFormService {
    private final Map<String, AppFormResponse> forms;

    private StubAppFormService(AppFormResponse form) {
      this.forms = Map.of(form.name(), form);
    }

    private StubAppFormService(Map<String, AppFormResponse> forms) {
      this.forms = forms;
    }

    @Override
    public AppFormResponse findForm(String appId, String formName) {
      return forms.get(formName);
    }

  }

  private static AppFormLayoutRecord layout(String... fieldNames) {
    return new AppFormLayoutRecord(List.of(fieldNames).stream()
      .map(fieldName -> new AppFormItemRecord(
        "FIELD", fieldName, null, null, null, null, null, null, null))
      .toList());
  }

  private static final class RecordingDataService extends DataService {
    private List<String> expand;
    private String filter;

    @Override
    public PageDTO<Map<String, Object>> findPagingRecords(
      String projectId,
      String modelName,
      int page,
      int size,
      String filter,
      String sort,
      List<String> expand
    ) {
      this.expand = expand;
      this.filter = filter;
      return new PageDTO<>(List.of(), 0L);
    }
  }

  private static final class RenderingDataService extends DataService {
    @Override
    public PageDTO<Map<String, Object>> findPagingRecords(
      String projectId,
      String modelName,
      int page,
      int size,
      String filter,
      String sort,
      List<String> expand
    ) {
      if ("orders".equals(modelName)) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "order-1");
        record.put("name", "Order One");
        record.put("customer_id", "customer-1");
        record.put("secret", "hidden-value");
        return new PageDTO<>(List.of(record), 1L);
      }
      if ("customer".equals(modelName)) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "customer-1");
        record.put("name", "Customer One");
        return new PageDTO<>(List.of(record), 1L);
      }
      throw new IllegalArgumentException("Unexpected model: " + modelName);
    }

    @Override
    public Map<String, Object> findOneRecord(
      String projectId,
      String modelName,
      String id,
      List<String> expand
    ) {
      Map<String, Object> record = new LinkedHashMap<>();
      record.put("id", "order-1");
      record.put("name", "Order One");
      record.put("customer_id", "customer-1");
      return record;
    }
  }

  private static final class RoutingDataService extends DataService {
    private String projectId;
    private String datasourceName;
    private String modelName;

    @Override
    public PageDTO<Map<String, Object>> findPagingRecords(
      String projectId,
      String datasourceName,
      String modelName,
      int page,
      int size,
      String filter,
      String sort,
      List<String> expand
    ) {
      this.projectId = projectId;
      this.datasourceName = datasourceName;
      this.modelName = modelName;
      return new PageDTO<>(List.of(), 0L);
    }
  }
}
