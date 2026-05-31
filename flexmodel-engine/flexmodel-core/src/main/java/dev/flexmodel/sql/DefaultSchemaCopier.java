package dev.flexmodel.sql;

import dev.flexmodel.SchemaProvider;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.NativeQueryDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 基于 FML 导出导入的 Schema 复制器。
 * <p>
 * 适用于 MySQL、PostgreSQL、Oracle 等服务端数据库。
 * 流程：从源数据源导出所有模型为 FML，然后导入到目标数据源，从而创建相同的表结构。
 *
 * @author cjbi
 */
public class DefaultSchemaCopier implements SchemaCopier {

  private static final Logger log = LoggerFactory.getLogger(DefaultSchemaCopier.class);

  private final SessionFactory sessionFactory;

  public DefaultSchemaCopier(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void copySchema(SchemaProvider source, String targetSchemaName, SchemaProvider target) {
    String sourceSchemaName = source.getName();
    log.info("开始通过 FML 导出导入复制 Schema: {} -> {}", sourceSchemaName, targetSchemaName);

    // 1. 注册目标数据源到 SessionFactory
    sessionFactory.registerSchemaProvider(target);

    try (Session targetSession = sessionFactory.createSession(targetSchemaName)) {
      // 2. 从源库获取所有模型
      List<SchemaObject> models = sessionFactory.getModels(sourceSchemaName);
      if (models == null || models.isEmpty()) {
        log.info("源 Schema '{}' 没有模型，跳过 FML 导出导入", sourceSchemaName);
        return;
      }

      for (SchemaObject model : models) {
        try {
          if (model instanceof EntityDefinition entityDefinition) {
            targetSession.schema().createEntity(entityDefinition);
          }
          if (model instanceof EnumDefinition enumDefinition) {
            targetSession.schema().createEnum(enumDefinition);
          }
          if (model instanceof NativeQueryDefinition nativeQueryDefinition) {
            targetSession.schema().createNativeQuery(nativeQueryDefinition);
          }
        } catch (Exception e) {
          log.error("复制模型 {} 失败: {}", model.getName(), e.getMessage());
        }

      }

      log.info("Schema 复制完成: {} -> {} (共 {} 个模型)", sourceSchemaName, targetSchemaName, models.size());
    } catch (Exception e) {
      // 失败时取消注册，避免留下脏状态
      sessionFactory.unregisterSchemaProvider(targetSchemaName);
      throw new RuntimeException("通过 FML 复制 Schema 失败: " + e.getMessage(), e);
    }
  }
}
