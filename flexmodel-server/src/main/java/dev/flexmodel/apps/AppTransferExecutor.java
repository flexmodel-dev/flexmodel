package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppTransfer.ImportPlan;
import dev.flexmodel.apps.dto.AppTransfer.PlannedRow;
import dev.flexmodel.apps.dto.AppTransfer.Receipt;
import dev.flexmodel.apps.dto.AppTransfer.TransferResult;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.nio.file.Files;
import java.util.*;

import static java.util.List.*;

/**
 * Business rows commit in one project transaction; the idempotency receipt is a staged file.
 */
@ApplicationScoped
public class AppTransferExecutor {
  @Inject
  ServerSessions sessions;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  ProjectService projects;
  @Inject
  AppFormService forms;
  @Inject
  AppTransferForms transferForms;
  @Inject
  AppRecordService records;
  @Inject
  AppTransferPlanner planner;
  @Inject
  AppTransferStore store;
  @Inject
  AppRecordHistoryRecorder history;
  private final Object[] commitLocks = java.util.stream.IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();

  TransferResult receipt(String appId, String userId, String importId, String hash) {
    if (!Files.isRegularFile(store.file(importId, ".receipt.json"))) return null;
    Receipt receipt = store.read(importId, ".receipt.json", Receipt.class);
    if (!appId.equals(receipt.appId()) || !userId.equals(receipt.userId()))
      throw new WebApplicationException("导入任务不存在", 404);
    if (hash != null && !hash.equals(receipt.planHash())) throw new WebApplicationException("导入计划摘要不匹配", 409);
    return receipt.result();
  }

  TransferResult commit(ImportPlan plan) {
    if (!plan.errors().isEmpty()) throw new ValidationException("请先修复预览中的错误");
    synchronized (commitLocks[Math.floorMod(plan.id().hashCode(), commitLocks.length)]) {
      TransferResult existing = receipt(plan.appId(), plan.userId(), plan.id(), plan.planHash());
      if (existing != null) return existing;
      TransferResult result = new TransferResult(plan.id(), "COMMITTED", plan.rows().size(), plan.forms());
      transactions.execute(plan.appId(), () -> {
        writeRows(plan);
        return null;
      });
      store.save(plan.id(), ".receipt.json", new Receipt(plan.appId(), plan.userId(), plan.planHash(), result));
      return result;
    }
  }

  private void writeRows(ImportPlan plan) {
    String schema = projects.resolveDatabaseName(plan.appId());
    boolean historyEnabled = history.captureSupported(plan.appId());
    List<AppRecordHistoryEvent> historyEvents = historyEnabled
      ? new ArrayList<>() : of();
    try (Session session = sessions.open(schema)) {
      var resolved = new HashMap<String, AppFormResponse>();
      var available = transferForms.all(plan.appId());
      plan.formHashes().forEach((name, hash) -> {
        var form = available.get(name);
        if (!hash.equals(planner.hash(form))) throw new WebApplicationException("表单定义已变化，请重新预览", 409);
        resolved.put(name, form);
      });
      Map<String, Set<String>> localIds = new HashMap<>();
      for (var row : plan.rows()) localIds.computeIfAbsent(row.formName(), ignored -> new HashSet<>()).add(row.id());
      Set<String> checkedReferences = new HashSet<>();
      for (int index = 0; index < plan.rows().size(); ) {
        PlannedRow first = plan.rows().get(index);
        var form = resolved.get(first.formName());
        List<Map<String, Object>> batch = new ArrayList<>();
        while (index < plan.rows().size() && batch.size() < 100 && first.formName().equals(plan.rows().get(index).formName())) {
          PlannedRow row = plan.rows().get(index++);
          Map<String, Object> values = records.prepareImport(form, row.values());
          for (var field : form.fields()) {
            Object value = values.get(field.name());
            if (field.referenceForm() == null || value == null) continue;
            if (!localIds.getOrDefault(field.referenceForm(), Set.of()).contains(String.valueOf(value))
              && checkedReferences.add(AppTransferPlanner.node(field.referenceForm(), String.valueOf(value)))) {
              var target = forms.findForm(plan.appId(), field.referenceForm());
              try (Session reference = sessions.open(target.builtin() ? "system" : schema)) {
                if (reference.data().findById(target.name(), value).isEmpty())
                  throw new WebApplicationException("引用记录已不存在，请重新预览: " + field.label(), 409);
              }
            }
          }
          records.assignCreationFields(form, values, plan.userId());
          values.put("id", row.id());
          batch.add(values);
        }
        try {
          session.data().insertAll(form.name(), batch);
        } catch (RuntimeException error) {
          throw new ValidationException(first.sheet() + " 第 " + first.row() + " 行起的批次写入失败，整包已回滚，请检查唯一键及字段约束", error);
        }
        if (historyEnabled) {
          for (Map<String, Object> values : batch) {
            historyEvents.add(history.importCreated(plan.appId(), plan.id(), form, values, plan.userId()));
          }
        }
      }
      // 历史事件在导入事务成功提交后分批写入，整包回滚不产生任何历史
      if (historyEnabled) {
        history.recordBatch(historyEvents);
      }
    }
  }
}
