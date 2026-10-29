package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppRecordHistoryRecorderTest {

  private static AppFormFieldResponse field(String name, String label, String type, String referenceForm) {
    return new AppFormFieldResponse(name, label, type, false, false, null, referenceForm);
  }

  private static AppFormResponse childForm() {
    return new AppFormResponse("orders", "订单", "", "name", "name", true, false,
      LocalDateTime.now(), LocalDateTime.now(),
      List.of(field("name", "名称", "TEXT", null), field("customer_id", "客户", "REFERENCE", "customer")),
      null, List.of());
  }

  private static AppFormResponse parentForm() {
    return new AppFormResponse("customer", "客户", "", "name", "name", true, false,
      LocalDateTime.now(), LocalDateTime.now(),
      List.of(field("name", "名称", "TEXT", null)),
      new AppFormLayoutRecord(List.of(new AppFormItemRecord(
        "SUBFORM", null, "订单", null, null, null, null, "orders", "customer_id"))),
      List.of());
  }

  private static Map<String, Object> record(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index + 1 < pairs.length; index += 2) {
      result.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return result;
  }

  private static final class RecordingRepository extends AppRecordHistoryRepository {
    final List<Map<String, Object>> events = new ArrayList<>();
    final List<Map<String, Object>> targets = new ArrayList<>();
    RuntimeException failure;

    @Override
    public void insertEvent(String appId, Map<String, Object> event) {
      if (failure != null) throw failure;
      events.add(new HashMap<>(event));
    }

    @Override
    public void insertTargets(String appId, List<Map<String, Object>> targetRows) {
      if (failure != null) throw failure;
      targetRows.forEach(row -> targets.add(new HashMap<>(row)));
    }
  }

  private static ProjectUnitOfWork immediateTransactions() {
    return new ProjectUnitOfWork() {
      @Override
      public <T> T executePlatform(String projectId, Supplier<T> action) {
        return action.get();
      }
    };
  }

  private AppRecordHistoryRecorder recorder(RecordingRepository repository, AppFormResponse... forms) {
    AppRecordHistoryRecorder recorder = new AppRecordHistoryRecorder();
    recorder.repository = repository;
    recorder.transactions = immediateTransactions();
    recorder.historyContext = new AppRecordHistoryContext();
    recorder.sessionContext = new SessionContext();
    recorder.sessionContext.setUserId("user-1");
    recorder.sessionContext.setAuthenticatedUser(true);
    recorder.appFormService = new AppFormService() {
      @Override
      public AppFormResponse findForm(String appId, String formName) {
        for (AppFormResponse form : forms) {
          if (form.name().equals(formName)) return form;
        }
        throw new dev.flexmodel.common.NotFoundException("表单不存在: " + formName);
      }
    };
    return recorder;
  }

  @Test
  void createdWritesSelfAndDeclaredParentTargets() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm(), parentForm());
    recorder.created("app-1", childForm(), record(
      "id", "order-1", "name", "订单A", "customer_id", "customer-9", "created_by", "user-1"));
    assertEquals(1, repository.events.size());
    Map<String, Object> event = repository.events.getFirst();
    assertEquals("RECORD_CREATED", event.get("event_type"));
    assertEquals("APPS", event.get("source"));
    assertEquals("USER", event.get("actor_type"));
    assertEquals("user-1", event.get("actor_id"));
    assertEquals("user-1", event.get("actor_name"));
    assertEquals("订单A", event.get("record_label"));
    assertEquals("订单", event.get("form_label"));
    assertEquals(2, repository.targets.size());
    assertEquals("SELF", repository.targets.get(0).get("target_type"));
    assertEquals("order-1", repository.targets.get(0).get("record_id"));
    assertEquals("PARENT", repository.targets.get(1).get("target_type"));
    assertEquals("customer-9", repository.targets.get(1).get("record_id"));
    assertEquals(repository.events.getFirst().get("id"), repository.targets.getFirst().get("event_id"));
  }

  @Test
  void updatedWithoutBusinessChangeWritesNothing() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm(), parentForm());
    recorder.updated("app-1", childForm(), "order-1",
      record("id", "order-1", "name", "订单A", "updated_at", LocalDateTime.now()),
      record("id", "order-1", "name", "订单A", "updated_at", LocalDateTime.now().plusMinutes(1)));
    assertTrue(repository.events.isEmpty());
  }

  @Test
  void updatedWithDiffRecordsChangeAndBothParentsOnMove() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm(), parentForm());
    recorder.updated("app-1", childForm(), "order-1",
      record("id", "order-1", "name", "订单A", "customer_id", "customer-1"),
      record("id", "order-1", "name", "订单B", "customer_id", "customer-2"));
    assertEquals(1, repository.events.size());
    Map<String, Object> event = repository.events.getFirst();
    assertEquals("RECORD_UPDATED", event.get("event_type"));
    assertEquals("订单B", event.get("record_label"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> changes = (List<Map<String, Object>>) event.get("changes");
    assertEquals(2, changes.size());
    List<String> targetRecords = repository.targets.stream()
      .map(row -> String.valueOf(row.get("record_id"))).toList();
    assertEquals(List.of("order-1", "customer-1", "customer-2"), targetRecords);
  }

  @Test
  void undeclaredReferenceRelationIsNotParentTarget() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm());
    recorder.created("app-1", childForm(), record(
      "id", "order-1", "name", "订单A", "customer_id", "customer-9"));
    assertEquals(1, repository.events.size());
    assertEquals(1, repository.targets.size());
    assertEquals("SELF", repository.targets.getFirst().get("target_type"));
  }

  @Test
  void duplicateEventKeyIsTreatedAsRecorded() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm());
    repository.failure = new RuntimeException(new SQLException("unique", "23505"));
    assertDoesNotThrow(() -> recorder.created("app-1", childForm(), record(
      "id", "order-1", "name", "订单A")));
    assertTrue(repository.events.isEmpty());
  }

  @Test
  void repositoryFailureDoesNotPropagate() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm());
    repository.failure = new RuntimeException("boom");
    assertDoesNotThrow(() -> recorder.created("app-1", childForm(), record(
      "id", "order-1", "name", "订单A")));
    assertDoesNotThrow(() -> recorder.updated("app-1", childForm(), "order-1",
      record("name", "a"), record("name", "b")));
  }

  @Test
  void importCreatedUsesStableEventKeyAndBatchPersist() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm(), parentForm());
    AppFormResponse child = childForm();
    AppRecordHistoryEvent first = recorder.importCreated("app-1", "import-1", child,
      record("id", "order-1", "name", "订单A", "customer_id", "customer-9"), "user-2");
    AppRecordHistoryEvent second = recorder.importCreated("app-1", "import-1", child,
      record("id", "order-2", "name", "订单B", "customer_id", "customer-9"), "user-2");
    assertEquals(first.eventKey(), AppRecordHistoryRecorder.digest("import-1", "orders", "order-1", "RECORD_CREATED"));
    assertEquals(AppRecordHistoryRecorder.digest("import-1", "orders", "order-2", "RECORD_CREATED"), second.eventKey());
    assertEquals("IMPORT", first.source());
    assertEquals("user-2", first.actorId());
    assertEquals("import-1", first.operationId());
    recorder.recordBatch(List.of(first, second));
    assertEquals(2, repository.events.size());
    assertEquals(4, repository.targets.size());
  }

  @Test
  void approvalContextRecordsSystemActorWithInitiator() {
    RecordingRepository repository = new RecordingRepository();
    AppRecordHistoryRecorder recorder = recorder(repository, childForm(), parentForm());
    AppFormResponse child = childForm();
    recorder.historyContext.as(
      new AppRecordHistoryContext.Snapshot("run-1", "task-1", "user-9"),
      () -> {
        recorder.created("app-1", child, record("id", "order-1", "name", "订单A"));
        return null;
      });
    Map<String, Object> event = repository.events.getFirst();
    assertEquals("APPROVAL", event.get("source"));
    assertEquals("SYSTEM", event.get("actor_type"));
    assertNull(event.get("actor_id"));
    assertEquals("系统", event.get("actor_name"));
    assertEquals("run-1", event.get("operation_id"));
    @SuppressWarnings("unchecked")
    Map<String, Object> context = (Map<String, Object>) event.get("context");
    assertEquals("user-9", context.get("initiator"));
    assertEquals("run-1", context.get("approvalInstanceId"));
    assertEquals("task-1", context.get("nodeId"));
  }
}
