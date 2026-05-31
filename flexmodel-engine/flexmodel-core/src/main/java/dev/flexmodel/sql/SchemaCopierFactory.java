package dev.flexmodel.sql;

import dev.flexmodel.session.SessionFactory;

/**
 * Schema 复制器工厂，创建基于 FML 导出导入的复制器。
 *
 * @author cjbi
 */
public class SchemaCopierFactory {

  /**
   * 创建 SchemaCopier 实例。
   *
   * @param sessionFactory SessionFactory（FmlSchemaCopier 需要）
   * @return SchemaCopier 实例
   */
  public static SchemaCopier create(SessionFactory sessionFactory) {
    return new DefaultSchemaCopier(sessionFactory);
  }
}
