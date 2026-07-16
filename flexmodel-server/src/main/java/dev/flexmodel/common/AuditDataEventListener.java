package dev.flexmodel.common;

import dev.flexmodel.event.EventListener;
import dev.flexmodel.event.PreChangeEvent;
import dev.flexmodel.event.impl.PreInsertEvent;
import dev.flexmodel.event.impl.PreUpdateEvent;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class AuditDataEventListener implements EventListener {

  @Override
  public void onPreChange(PreChangeEvent event) {
    if (!"system".equals(event.getSchemaName())) {
      return;
    }
    // 拦截查询
    invokeQuery(event);
    // 拦截数据
    invokeData(event);
  }

  private void invokeData(PreChangeEvent event) {
    Map<String, Object> newData = event.getNewData();
    String userId = "admin";
    try {
      SessionContext sessionContext = CDI.current().select(SessionContext.class).get();
      userId = sessionContext.getUserId();
      if (newData == null) {
        return;
      }
    } catch (Exception _) {

    }

    SessionFactory sf = event.getSource();
    EntityDefinition entity = (EntityDefinition) sf.getModelRegistry().getRegistered(event.getSchemaName(), event.getModelName());
    TypedField<?, ?> createdByField = entity.getField("created_by");
    TypedField<?, ?> updatedByField = entity.getField("updated_by");
    TypedField<?, ?> createdAtField = entity.getField("created_at");
    TypedField<?, ?> updatedAtField = entity.getField("updated_at");
    if (event instanceof PreInsertEvent) {
      if (createdByField != null && newData.get("created_by") == null && userId != null) {
        newData.put("created_by", userId);
      }
      if (createdAtField != null && newData.get("created_at") == null) {
        newData.put("created_at", LocalDateTime.now());
      }
    }
    if (event instanceof PreUpdateEvent) {
      if (updatedByField != null && newData.get("updated_by") == null && userId != null) {
        newData.put("updated_by", userId);
      }
      if (updatedAtField != null && newData.get("updated_at") == null) {
        newData.put("updated_at", LocalDateTime.now());
      }
    }
  }

  private void invokeQuery(PreChangeEvent event) {
    // 拦截查询
  }

}
