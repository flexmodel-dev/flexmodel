package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppDataAuthorization;
import dev.flexmodel.apps.dto.AppDataAuthorization.*;
import dev.flexmodel.codegen.entity.DataAuthorization;
import dev.flexmodel.codegen.entity.DataPermissionControl;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.persistence.ServerSessions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

import static dev.flexmodel.codegen.System.dataAuthorization;
import static dev.flexmodel.codegen.System.dataPermissionControl;
import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class AppDataAuthorizationRepository {
  @Inject
  ServerSessions sessions;
  @Inject
  ProjectUnitOfWork transactions;

  public List<AppDataAuthorization> find(String appId, String rootForm) {
    try (var session = sessions.open("system")) {
      var predicate = dataAuthorization.appId.eq(appId);
      if (rootForm != null) predicate = predicate.and(dataAuthorization.formName.eq(rootForm));
      List<AppDataAuthorization> result = new ArrayList<>();
      for (var rule : session.dsl().selectFrom(DataAuthorization.class).where(predicate).execute()) {
        List<Control> controls = session.dsl().selectFrom(DataPermissionControl.class)
          .where(dataPermissionControl.authorizationId.eq(rule.getId())).execute().stream()
          .map(row -> new Control(row.getFormName(), Boolean.TRUE.equals(row.getReadAll()), Boolean.TRUE.equals(row.getWriteAll()),
            Boolean.TRUE.equals(row.getAllowDelete()), fieldControls(row.getFieldControls()), condition(row.getCondition()))).toList();
        result.add(new AppDataAuthorization(rule.getId(), appId, rule.getFormName(), rule.getName(),
          Boolean.TRUE.equals(rule.getEnabled()), selections(rule.getSubjects()), selections(rule.getDataScope()), controls));
      }
      return result;
    }
  }

  public AppDataAuthorization save(AppDataAuthorization rule) {
    return transactions.executePlatform(rule.appId(), () -> {
      try (var session = sessions.open("system")) {
        var entity = new DataAuthorization();
        entity.setId(rule.id());
        entity.setAppId(rule.appId());
        entity.setFormName(rule.formName());
        entity.setName(rule.name());
        entity.setEnabled(rule.enabled());
        entity.setSubjects(rule.subjects());
        entity.setDataScope(rule.dataScope());
        session.dsl().mergeInto(DataAuthorization.class).values(entity).execute();
        session.dsl().deleteFrom(DataPermissionControl.class).where(dataPermissionControl.authorizationId.eq(rule.id())).execute();
        for (var control : rule.controls()) {
          var row = new DataPermissionControl();
          row.setId(UUID.randomUUID().toString());
          row.setAuthorizationId(rule.id());
          row.setFormName(control.formName());
          row.setReadAll(control.readAll());
          row.setWriteAll(control.writeAll());
          row.setAllowDelete(control.allowDelete());
          row.setFieldControls(control.fieldControls());
          row.setCondition(control.condition());
          session.dsl().mergeInto(DataPermissionControl.class).values(row).execute();
        }
      }
      return rule;
    });
  }

  public void delete(AppDataAuthorization rule) {
    transactions.executePlatform(rule.appId(), () -> {
      try (var session = sessions.open("system")) {
        session.dsl().deleteFrom(DataPermissionControl.class).where(dataPermissionControl.authorizationId.eq(rule.id())).execute();
        session.dsl().deleteFrom(DataAuthorization.class)
          .where(dataAuthorization.id.eq(rule.id()).and(dataAuthorization.appId.eq(rule.appId()))).execute();
      }
      return null;
    });
  }

  private List<Selection> selections(Object value) {
    return JsonUtils.parseToList(JsonUtils.toJsonString(value), Selection.class);
  }

  private Map<String, FieldControl> fieldControls(Object value) {
    Map<String, FieldControl> result = new LinkedHashMap<>();
    object(value).forEach((key, entry) -> result.put(key, JsonUtils.convertValue(entry, FieldControl.class)));
    return result;
  }

  private Map<String, Object> condition(Object value) {
    return value == null ? null : object(value);
  }
}
