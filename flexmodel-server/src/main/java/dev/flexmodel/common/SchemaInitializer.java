package dev.flexmodel.common;

import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * 使用 project.fml 模板初始化新 Schema 的表结构。
 * <p>
 * 新建物理 Schema 后，调用此组件在其中创建所有预定义表（flow、trigger、storage 等）。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class SchemaInitializer {

  @Inject
  SessionFactory sessionFactory;

  /**
   * 在指定 Schema 中执行 project.fml，初始化项目级表结构。
   *
   * @param schemaName 目标 Schema 名称
   */
  public void init(String schemaName) {
    log.info("Initializing schema '{}' with project.fml template", schemaName);
    sessionFactory.loadScript(schemaName, "project.fml");
    log.info("Schema '{}' initialized successfully", schemaName);
  }
}
