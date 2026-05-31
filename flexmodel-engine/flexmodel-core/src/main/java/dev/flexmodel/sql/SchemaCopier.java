package dev.flexmodel.sql;

import dev.flexmodel.SchemaProvider;

/**
 * 数据源复制接口，通过 FML 导出导入实现模型结构的跨数据库复制。
 *
 * @author cjbi
 * @see DefaultSchemaCopier
 * @see SchemaCopierFactory
 */
public interface SchemaCopier {

  /**
   * 将源数据源的模型结构和数据复制到目标数据源。
   *
   * @param source           源数据源 SchemaProvider
   * @param targetSchemaName 目标 Schema 名称
   * @param target           目标数据源 SchemaProvider
   */
  void copySchema(SchemaProvider source, String targetSchemaName, SchemaProvider target);
}
