package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppReferenceValueResponse;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppRecordServiceTest {

  @Test
  void findRecordsExpandsReferenceFields() {
    AppFormFieldResponse nameField = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormFieldResponse referenceField = new AppFormFieldResponse(
      "customer_id", "Customer", "REFERENCE", false, false, null, "customer");
    AppFormResponse form = new AppFormResponse(
      "orders", "Orders", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(nameField, referenceField), null);
    RecordingDataService dataService = new RecordingDataService();
    AppRecordService service = new AppRecordService();
    service.appFormService = new StubAppFormService(form);
    service.dataService = dataService;
    service.sessionContext = new SessionContext();

    service.findRecords(
      "app", "orders", 1, 20, null, null, null, null, null, null);

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
      LocalDateTime.now(), LocalDateTime.now(), List.of(orderName, customerReference), null);
    AppFormFieldResponse customerName = new AppFormFieldResponse(
      "name", "Name", "TEXT", true, false, null, null);
    AppFormResponse customerForm = new AppFormResponse(
      "customer", "Customer", "", "name", "name", true,
      LocalDateTime.now(), LocalDateTime.now(), List.of(customerName), null);
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
      "app", "orders", 1, 20, null, null, null, null, null, null);
    assertEquals(expected, page.list().getFirst().get("customer_id"));

    Map<String, Object> record = service.findRecord(
      "app", "orders", "order-1", null, null, null);
    assertEquals(expected, record.get("customer_id"));
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

  private static final class RecordingDataService extends DataService {
    private List<String> expand;

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
}
