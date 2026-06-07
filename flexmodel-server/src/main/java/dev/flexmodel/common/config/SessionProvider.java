package dev.flexmodel.common.config;

import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session提供者
 * 用于在CDI容器中提供Session实例
 * <p>
 * 该提供者通过QuarkusSessionManager获取当前Session，支持在异步操作中访问
 * （通过上下文传播机制）
 * <p>
 * 注意：Session的生命周期由SessionInterceptor管理，此Provider只是提供访问入口
 * <p>
 * 修复说明：移除 ThreadLocal — CDI @RequestScoped 本身已保证每个请求上下文一个实例，
 * 使用实例字段即可。ThreadLocal 可能导致 @PreDestroy 在不同线程执行时连接泄漏。
 *
 * @author cjbi
 */
@RequestScoped
public class SessionProvider {

  private static final Logger log = LoggerFactory.getLogger(SessionProvider.class);

  @Inject
  SessionFactory sessionFactory;

  private Session session;

  /**
   * 提供默认Session
   *
   * @return Session实例
   */
  @Produces
  @RequestScoped
  public Session provideSession() {
    log.debug("Providing session via CDI");

    // CDI @RequestScoped 保证每次请求上下文内 provideSession() 只调用一次，
    // 因此使用实例字段即可，无需 ThreadLocal
    if (session == null || session.isClosed()) {
      session = sessionFactory.createSession();
    }

    return session;
  }

  @PreDestroy
  public void destroy() {
    if (session != null) {
      try {
        session.close();
      } catch (Exception e) {
        log.warn("Error closing session", e);
      }
    }
    log.debug("Closing session via CDI");
  }

}

