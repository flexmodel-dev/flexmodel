package dev.flexmodel.event.impl;

import dev.flexmodel.event.EventType;
import dev.flexmodel.event.PreChangeEvent;
import dev.flexmodel.session.SessionFactory;

import java.util.Map;

/**
 * 前置插入事件
 *
 * @author cjbi
 */
public class PreInsertEvent extends PreChangeEvent {

    public PreInsertEvent(String modelName, String schemaName, Map<String, Object> newData, Object id, String sessionId, SessionFactory source) {
        super(EventType.PRE_INSERT, modelName, schemaName, newData, id, sessionId, source);
    }

}
