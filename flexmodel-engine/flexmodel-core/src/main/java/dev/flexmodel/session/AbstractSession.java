package dev.flexmodel.session;

import dev.flexmodel.query.DSL;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.EventAwareDataService;
import dev.flexmodel.service.SchemaService;
import dev.flexmodel.type.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * 统一的Session实现，完全合并了所有装饰器和中间层功能
 * 消除了所有中间层和装饰器，直接实现Session接口
 *
 * @author cjbi
 */
public abstract class AbstractSession implements Session {

  private final AbstractSessionContext sessionContext;
  protected final DataService dataService;
  private final SchemaService schemaService;
  protected final Logger log = LoggerFactory.getLogger(this.getClass());
  protected final String sessionId;

  public AbstractSession(AbstractSessionContext sessionContext, DataService dataService,
                         SchemaService schemaService) {
    this.sessionContext = sessionContext;
    this.sessionContext.setSession(this);
    this.dataService = dataService;
    this.schemaService = schemaService;
    this.sessionId = UUID.randomUUID().toString();
    log.debug("Created session {}", sessionId);
  }

  @Override
  public DataService data() {
    return dataService;
  }

  @Override
  public SchemaService schema() {
    return schemaService;
  }

  @Override
  public SessionFactory getFactory() {
    return sessionContext.getFactory();
  }

  public Map<String, ? extends TypeHandler<?>> getTypeHandlerMap() {
    return sessionContext.getTypeHandlerMap();
  }

  @Override
  public String getName() {
    return sessionContext.getSchemaName();
  }

  @Override
  public DSL dsl() {
    return new DSL(this);
  }

  /**
   * 在 Session.close()（连接已归还连接池）后调用，发布缓冲的后置事件。
   * 如果 dataService 是 EventAwareDataService，则 flush 其 pending 列表；
   * 否则（如 MongoDataService）为空操作。
   * 自身吞掉异常，避免覆盖 close 的原始异常。
   */
  protected final void flushEvents() {
    if (dataService instanceof EventAwareDataService eads) {
      try {
        eads.flushPendingEvents();
      } catch (Exception e) {
        log.error("Error flushing pending events for session {}", sessionId, e);
      }
    }
  }
}
