package dev.flexmodel.apps.flow;

import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.codegen.entity.AppFlowInstance;
import dev.flexmodel.codegen.entity.AppFlowRecordLock;
import dev.flexmodel.codegen.entity.AppApprovalTask;
import dev.flexmodel.codegen.entity.AppApprovalWait;
import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.codegen.entity.FlowInstanceMapping;
import dev.flexmodel.codegen.entity.InstanceData;
import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.codegen.entity.RuntimeMessage;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.project.ProjectRepository;
import dev.flexmodel.query.Query;
import dev.flexmodel.service.WriteInterceptor;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;

import java.util.*;
import java.util.function.Supplier;

import static dev.flexmodel.common.Values.*;
import static dev.flexmodel.query.Expressions.*;

/**
 * Lock the selected business rows before checking approval locks, then mutate only that checked set.
 */
@ApplicationScoped
public class AppFlowRecordGuard implements WriteInterceptor {
  private static final Set<String> INTERNAL = Set.of(
    ReflectionUtils.getModelNameFromClass(RuntimeMessage.class),
    ReflectionUtils.getModelNameFromClass(AppFlowInstance.class),
    ReflectionUtils.getModelNameFromClass(AppApprovalWait.class),
    ReflectionUtils.getModelNameFromClass(AppApprovalTask.class),
    ReflectionUtils.getModelNameFromClass(AppFlowRecordLock.class),
    ReflectionUtils.getModelNameFromClass(FlowDefinition.class),
    ReflectionUtils.getModelNameFromClass(FlowDeployment.class),
    ReflectionUtils.getModelNameFromClass(FlowInstance.class),
    ReflectionUtils.getModelNameFromClass(NodeInstance.class),
    ReflectionUtils.getModelNameFromClass(NodeInstanceLog.class),
    ReflectionUtils.getModelNameFromClass(InstanceData.class),
    ReflectionUtils.getModelNameFromClass(FlowInstanceMapping.class));
  @Inject
  ProjectRepository projectRepository;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  AppFlowRecordLockRepository appFlowRecordLockRepository;

  public Object intercept(Session session, String operation, Object[] args, Supplier<Object> proceed) {
    String model = args != null && args.length > 0 ? Objects.toString(args[0], "") : "";
    if (INTERNAL.contains(model)) return proceed.get();
    var project = projectRepository.findProjects().stream().filter(item -> !item.getId().equals("default") && session.getName().equals(item.getDatabaseName())).findFirst();
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
        session.lockRow(model, id, value);
        query.setFilter(field(id).eq(value).toJsonString());
      } else if (!operation.equals("deleteAll")) {
        Object filter = args[operation.equals("update") ? 2 : 1];
        query.setFilter(filter instanceof dev.flexmodel.query.Predicate predicate ? predicate.toJsonString() : Objects.toString(filter, ""));
      }
      var rows = session.lockRows(model, query);
      if (rows.isEmpty()) return 0;
      var ids = rows.stream().map(row -> row.get(id)).toList();
      Set<String> selected = new HashSet<>(ids.stream().map(String::valueOf).toList());
      boolean blocked = appFlowRecordLockRepository.findBySchemaAndModel(app, session.getName(), model).stream()
        .anyMatch(lock -> !text(lock, "run_id").equals(AppFlowRecordContext.runId()) && selected.contains(text(lock, "record_id")));
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
