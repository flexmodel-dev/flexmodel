package dev.flexmodel.event.impl;

import dev.flexmodel.event.EventType;
import dev.flexmodel.event.PreChangeEvent;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.SessionFactory;

import java.util.Map;

/**
 * 前置更新事件
 *
 * @author cjbi
 */
public class PreUpdateEvent extends PreChangeEvent {

    public PreUpdateEvent(String modelName, String schemaName, Map<String, Object> oldData, Map<String, Object> newData, Object id, Query query, String sessionId, SessionFactory source) {
        super(EventType.PRE_UPDATE, modelName, schemaName, oldData, newData, id, query, sessionId, source);
    }
}
