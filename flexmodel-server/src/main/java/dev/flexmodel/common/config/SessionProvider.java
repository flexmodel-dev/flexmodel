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
 *
 * @author cjbi
 */
@RequestScoped
public class SessionProvider {

  private static final Logger log = LoggerFactory.getLogger(SessionProvider.class);

  private final ThreadLocal<Session> sessionHolder = new ThreadLocal<>();

  @Inject
  SessionFactory sessionFactory;


  /**
   * 提供默认Session
   *
   * @return Session实例
   */
  @Produces
  @RequestScoped
  public Session provideSession() {
    log.debug("Providing session via CDI");

    // 获取当前Session（如果不存在会创建，但这应该由SessionInterceptor负责）
    Session session = sessionHolder.get();

    if (session == null || session.isClosed()) {
      session = sessionFactory.createSession();
      sessionHolder.set(session);
    }

    return session;
  }

  @PreDestroy
  public void destroy() {
    Session session = sessionHolder.get();
    if (session != null) {
      session.close();
    }
    log.debug("Closing session via CDI");
    sessionHolder.remove();
  }

}

