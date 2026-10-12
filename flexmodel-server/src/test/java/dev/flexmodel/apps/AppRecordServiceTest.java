package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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

  private static final class StubAppFormService extends AppFormService {
    private final AppFormResponse form;

    private StubAppFormService(AppFormResponse form) {
      this.form = form;
    }

    @Override
    public AppFormResponse findForm(String appId, String formName) {
      return form;
    }

    @Override
    public String resolveEntityName(String appId, String formName) {
      return "order_entity";
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
}
