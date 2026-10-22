package dev.flexmodel.approval;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.project.ProjectRepository;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;

import java.util.*;
import java.util.function.Supplier;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.query.Expressions.*;

/**
 * Lock the selected business rows before checking approval locks, then mutate only that checked set.
 */
@ApplicationScoped
public class ApprovalRecordGuard {
  private static final Set<String> INTERNAL = Set.of("f_runtime_message",
    "f_approval_instance", "f_approval_wait", "f_approval_task", "f_approval_record_lock",
    "f_em_flow_definition", "f_em_flow_deployment", "f_ei_flow_instance", "f_ei_node_instance", "f_ei_node_instance_log", "f_ei_instance_data", "f_ei_flow_instance_mapping");
  @Inject
  ProjectRepository projects;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  ApprovalRepository repository;

  public Object intercept(Session session, String operation, Object[] args, Supplier<Object> proceed) {
    String model = args != null && args.length > 0 ? Objects.toString(args[0], "") : "";
    if (INTERNAL.contains(model)) return proceed.get();
    var project = projects.findProjects().stream().filter(item -> !item.getId().equals("default") && session.getName().equals(item.getDatabaseName())).findFirst();
    if (project.isEmpty()) return proceed.get();
    String app = project.get().getId();
    return transactions.adopt(app, session, () -> {
      if (!operation.startsWith("update") && !operation.startsWith("delete")) return proceed.get();
      var entity = (EntityDefinition) session.schema().getModel(model);
      String id = entity.findIdField().orElseThrow().getName();
      var query = new Query();
      boolean byId = operation.equals("updateById") || operation.equals("deleteById");
      if (byId) {
        Object value = args[operation.equals("updateById") ? 2 : 1];
        ServerSessions.managed(session).lockRow(model, id, value);
        query.setFilter(field(id).eq(value).toJsonString());
      } else if (!operation.equals("deleteAll")) {
        Object filter = args[operation.equals("update") ? 2 : 1];
        query.setFilter(filter instanceof dev.flexmodel.query.Predicate predicate ? predicate.toJsonString() : Objects.toString(filter, ""));
      }
      var rows = ServerSessions.managed(session).lockRows(model, query);
      if (rows.isEmpty()) return 0;
      var ids = rows.stream().map(row -> row.get(id)).toList();
      Set<String> selected = new HashSet<>(ids.stream().map(String::valueOf).toList());
      boolean blocked = repository.list(app, "f_approval_record_lock", field("schema_name").eq(session.getName()).and(field("model_name").eq(model))).stream()
        .anyMatch(lock -> !text(lock, "run_id").equals(ApprovalRecordContext.runId()) && selected.contains(text(lock, "record_id")));
      if (blocked) throw new ClientErrorException("记录正在审批中，暂时不能编辑或删除", 409);
      if (operation.equals("deleteAll")) return session.data().delete(model, field(id).in(ids));
      if (!byId) {
        int index = operation.equals("update") ? 2 : 1;
        var checked = field(id).in(ids);
        args[index] = args[index] instanceof dev.flexmodel.query.Predicate ? checked : checked.toJsonString();
      }
      return proceed.get();
    });
  }
}
