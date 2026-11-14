package dev.flexmodel.common;

import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.inject.Inject;


/**
 * @author chengjinbao
 */
public abstract class AbstractRepository {

  @Inject
  protected SessionFactory sessionFactory;

  /**
   * 打开平台库 Session（system schema）。
   * <p>
   * 平台级表统一存于平台库并按 project_id 隔离，调用方需在查询条件中显式附加 project_id。
   */
  protected Session openSession() {
    Session borrowed = ProjectUnitOfWork.borrowPlatform();
    return borrowed != null ? borrowed : sessionFactory.createSession();
  }
}
