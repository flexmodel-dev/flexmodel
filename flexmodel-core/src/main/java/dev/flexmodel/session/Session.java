package dev.flexmodel.session;

import dev.flexmodel.query.DSL;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.SchemaService;

import java.io.Closeable;

/**
 * @author cjbi
 */
public interface Session extends Closeable {

  /**
   * 开启事务
   */
  void startTransaction();

  /**
   * 提交事务
   */
  void commit();

  /**
   * 回滚事务
   */
  void rollback();

  /**
   * 关闭连接
   */
  void close();

  boolean isClosed();

  SessionFactory getFactory();

  String getName();

  /**
   * 获取数据操作对象
   *
   * @return 数据操作对象
   */
  DataService data();

  /**
   * 获取模型操作对象
   *
   * @return 模型操作对象
   */
  SchemaService schema();

  DSL dsl();

}
