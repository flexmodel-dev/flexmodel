package dev.flexmodel.apps;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.dto.AppTransfer.*;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.StringField;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.util.*;
import static dev.flexmodel.apps.dto.AppTransfer.*;

/** All business rows and the idempotency receipt share one project transaction. */
@ApplicationScoped
public class AppTransferExecutor {
  @Inject ServerSessions sessions;
  @Inject ProjectUnitOfWork transactions;
  @Inject ProjectService projects;
  @Inject AppFormService forms;
  @Inject AppTransferForms transferForms;
  @Inject AppRecordService records;
  @Inject AppTransferPlanner planner;
  @Inject ObjectMapper json;
  private final Set<String> initialized = new HashSet<>();
  private final Object[] commitLocks = java.util.stream.IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();

  synchronized void ensure(String schema) {
    if (initialized.contains(schema)) return;
    try (Session session = sessions.openOwned(schema)) {
      EntityDefinition entity = new EntityDefinition(RECEIPT)
        .addAdditionalProperty("system", true)
        .addAdditionalProperty("migration", Map.of("enabled", false))
        .addField(new StringField("id").setLength(64).asIdentity().setNullable(false))
        .addField(new StringField("app_id").setLength(64).setNullable(false))
        .addField(new StringField("user_id").setLength(255).setNullable(false))
        .addField(new StringField("plan_hash").setLength(64).setNullable(false))
        .addField(new StringField("result").setLength(10000).setNullable(false));
      // Registration is needed again after restart even when the physical table already exists.
      try { session.schema().createEntity(entity); }
      catch (RuntimeException createError) {
        try { session.dsl().selectFrom(RECEIPT).page(1, 1).execute(); }
        catch (RuntimeException missing) { throw createError; }
      }
      initialized.add(schema);
    }
  }

  TransferResult receipt(String appId, String userId, String importId, String hash) {
    String schema = projects.resolveDatabaseName(appId);
    ensure(schema);
    try (Session session = sessions.open(schema)) {
      var existing = session.data().findById(RECEIPT, importId).orElse(null);
      return existing == null ? null : result(existing, appId, userId, hash);
    }
  }

  private TransferResult result(Map<String, Object> receipt, String appId, String userId, String hash) {
    if (!appId.equals(receipt.get("app_id")) || !userId.equals(receipt.get("user_id")))
      throw new WebApplicationException("导入任务不存在", 404);
    if (hash != null && !hash.equals(receipt.get("plan_hash"))) throw new WebApplicationException("导入计划摘要不匹配", 409);
    try { return json.readValue(String.valueOf(receipt.get("result")), TransferResult.class); }
    catch (Exception error) { throw new IllegalStateException("读取导入结果失败", error); }
  }

  TransferResult commit(ImportPlan plan) {
    synchronized (commitLocks[Math.floorMod(plan.id().hashCode(), commitLocks.length)]) {
      return commitLocked(plan);
    }
  }

  private TransferResult commitLocked(ImportPlan plan) {
    if (!plan.errors().isEmpty()) throw new ValidationException("请先修复预览中的错误");
    String schema = projects.resolveDatabaseName(plan.appId());
    ensure(schema);
    try {
      return transactions.execute(plan.appId(), () -> {
        try (Session session = sessions.open(schema)) {
          var existing = session.data().findById(RECEIPT, plan.id()).orElse(null);
          if (existing != null) return result(existing, plan.appId(), plan.userId(), plan.planHash());
          var resolved = new HashMap<String, dev.flexmodel.apps.dto.AppFormResponse>();
          var available = transferForms.all(plan.appId());
          plan.formHashes().forEach((name, hash) -> {
            var form = available.get(name);
            if (!hash.equals(planner.hash(form))) throw new WebApplicationException("表单定义已变化，请重新预览", 409);
            resolved.put(name, form);
          });
          TransferResult result = new TransferResult(plan.id(), "COMMITTED", plan.rows().size(), plan.forms());
          String encoded;
          try { encoded = json.writeValueAsString(result); }
          catch (Exception error) { throw new IllegalStateException(error); }
          // Claim the unique import ID first; competing commits block on this primary key.
          session.data().insert(RECEIPT, new LinkedHashMap<>(Map.of("id", plan.id(), "app_id", plan.appId(),
            "user_id", plan.userId(), "plan_hash", plan.planHash(), "result", encoded)));
          Map<String, Set<String>> localIds = new HashMap<>();
          for (var row : plan.rows()) localIds.computeIfAbsent(row.formName(), ignored -> new HashSet<>()).add(row.id());
          Set<String> checkedReferences = new HashSet<>();
          for (int index = 0; index < plan.rows().size();) {
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
            try { session.data().insertAll(form.name(), batch); }
            catch (RuntimeException error) {
              throw new ValidationException(first.sheet() + " 第 " + first.row() + " 行起的批次写入失败，整包已回滚，请检查唯一键及字段约束", error);
            }
          }
          return result;
        }
      });
    } catch (RuntimeException error) {
      TransferResult completed = receipt(plan.appId(), plan.userId(), plan.id(), plan.planHash());
      if (completed != null) return completed;
      throw error;
    }
  }
}
