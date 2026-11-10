package dev.flexmodel.common;

import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.session.UnitOfWork;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Supplier;

/**
 * 项目语义门面：projectId→schema 解析。
 * 事务传播、提交后回调、行锁等语义由 engine UnitOfWork 提供。
 */
@ApplicationScoped
public class ProjectUnitOfWork {
  private static final String PLATFORM_SCHEMA = "system";

  @Inject
  SessionFactory sessionFactory;
  @Inject
  UnitOfWork unitOfWork;

  /**
   * 开启项目业务库事务。
   */
  public <T> T execute(String projectId, Supplier<T> action) {
    return unitOfWork.execute(projectSchema(projectId), action);
  }

  /**
   * 在平台库中开启事务。平台级表统一存于 system schema，无法与业务库保持原子性。
   */
  public <T> T executePlatform(String projectId, Supplier<T> action) {
    return unitOfWork.execute(PLATFORM_SCHEMA, action);
  }

  /**
   * 在平台库中开启独立事务执行发布类动作（不加入外层平台事务、不随其回滚；
   * 外层平台事务活动期间延迟到其结束后执行），用于业务事务提交后的发布。
   */
  public void publishPlatformNew(String projectId, Runnable action) {
    unitOfWork.publishNew(PLATFORM_SCHEMA, action);
  }

  /**
   * 接管既有会话：为其开启事务或加入当前事务，会话生命周期仍由调用方负责。
   */
  public <T> T adopt(String projectId, Session session, Supplier<T> action) {
    return unitOfWork.adopt(session, action);
  }

  public static void afterCommit(Runnable action) {
    UnitOfWork.afterCommit(action);
  }

  public static int lock(String projectId, String model, String column, Object value) {
    return UnitOfWork.lock(model, column, value);
  }

  public static int lock(String projectId, Class<?> model, String column, Object value) {
    return UnitOfWork.lock(ReflectionUtils.getModelNameFromClass(model), column, value);
  }

  public static Session borrowPlatform() {
    return UnitOfWork.borrow(PLATFORM_SCHEMA);
  }

  public static Session borrowSchema(String schema) {
    return UnitOfWork.borrow(schema);
  }

  private String projectSchema(String projectId) {
    try (Session system = sessionFactory.createSession()) {
      Project project = system.dsl().selectFrom(Project.class)
        .where(dev.flexmodel.codegen.System.project.id.eq(projectId)).executeOne();
      if (project == null) throw new NotFoundException("应用不存在");
      return project.getDatabaseName();
    }
  }
}
