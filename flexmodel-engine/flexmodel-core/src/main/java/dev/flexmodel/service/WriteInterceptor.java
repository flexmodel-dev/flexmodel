package dev.flexmodel.service;

import dev.flexmodel.session.Session;

import java.util.function.Supplier;

/**
 * 写拦截器 SPI：在 DataService 写方法（insert、update、delete 前缀）执行前后介入。
 * 拦截器可原地改写 args，proceed() 使用同一数组；不拦截时直接 proceed.get()。
 */
public interface WriteInterceptor {

  /**
   * @param session   当前会话
   * @param operation DataService 方法名
   * @param args      原参数数组（可原地改写）
   * @param proceed   继续执行链
   * @return 调用结果
   */
  Object intercept(Session session, String operation, Object[] args, Supplier<Object> proceed);
}
