package dev.flexmodel.event.impl;

import dev.flexmodel.event.EventType;
import dev.flexmodel.event.PreChangeEvent;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.SessionFactory;

import java.util.Map;

/**
 * 前置删除事件
 *
 * @author cjbi
 */
public class PreDeleteEvent extends PreChangeEvent {

    public PreDeleteEvent(String modelName, String schemaName, Map<String, Object> oldData, Object id, Query query, String sessionId, SessionFactory source) {
        super(EventType.PRE_DELETE, modelName, schemaName, oldData, null, id, query, sessionId, source);
    }
}
