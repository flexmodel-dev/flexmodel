package dev.flexmodel.event.impl;

import dev.flexmodel.event.EventType;
import dev.flexmodel.event.PreChangeEvent;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.SessionFactory;

/**
 * 前置查询事件
 *
 * @author cjbi
 */
public class PreQueryEvent extends PreChangeEvent {

    public PreQueryEvent(String modelName, String schemaName, Query query, String sessionId, SessionFactory source) {
        super(EventType.PRE_QUERY, modelName, schemaName, null, null, null, query, sessionId, source);
    }
}
