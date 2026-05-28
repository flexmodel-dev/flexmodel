package dev.flexmodel.sql;

import javax.sql.DataSource;

/**
 * Schema 生命周期管理接口，封装不同数据库的 Schema 创建/删除差异。
 *
 * @author cjbi
 */
public interface SchemaManager {

  /**
   * 创建物理 Schema（幂等，已存在则跳过）。
   *
   * @param systemDataSource 系统数据源（用于执行 DDL）
   * @param schemaName       目标 Schema 名称
   */
  void createSchema(DataSource systemDataSource, String schemaName);

  /**
   * 删除物理 Schema（幂等，不存在则跳过）。
   *
   * @param systemDataSource 系统数据源（用于执行 DDL）
   * @param schemaName       目标 Schema 名称
   */
  void dropSchema(DataSource systemDataSource, String schemaName);

  /**
   * 检查 Schema 是否存在。
   *
   * @param systemDataSource 系统数据源
   * @param schemaName       目标 Schema 名称
   * @return 是否存在
   */
  boolean schemaExists(DataSource systemDataSource, String schemaName);
}
