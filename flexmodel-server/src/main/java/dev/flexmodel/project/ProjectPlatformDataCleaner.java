package dev.flexmodel.project;

import dev.flexmodel.codegen.entity.*;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static dev.flexmodel.codegen.System.appDataAuthorization;
import static dev.flexmodel.codegen.System.appDataPermissionControl;
import static dev.flexmodel.query.Expressions.field;

/**
 * 清理平台库中属于指定项目的平台级模型数据。
 * <p>
 * 平台级模型统一存于系统库：f_app_* 系列按 app_id 隔离（应用与项目一对一共享主键），其余按 project_id 隔离。
 * 项目/分支删除时需要显式清理，避免残留。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class ProjectPlatformDataCleaner {

  /**
   * 按 app_id 隔离的应用级模型清单。顺序保证先删子模型再删主模型。
   */
  private static final List<Class<?>> APP_MODELS = List.of(
    AppRecordHistoryTarget.class,
    AppRecordHistory.class,
    AppRecordDiscussion.class,
    AppApprovalTask.class,
    AppApprovalWait.class,
    AppApprovalRecordLock.class,
    AppApprovalInstance.class,
    AppWorkbench.class,
    AppDataAuthorization.class
  );

  /**
   * 按 project_id 隔离的平台级模型清单。顺序保证先删子模型再删主模型。
   */
  private static final List<Class<?>> PLATFORM_MODELS = List.of(
    RuntimeMessage.class,
    NodeInstanceLog.class,
    InstanceData.class,
    NodeInstance.class,
    FlowInstanceMapping.class,
    FlowInstance.class,
    FlowDeployment.class,
    FlowDefinition.class,
    JobExecutionLog.class,
    Trigger.class,
    FunctionLog.class,
    Function.class,
    Bucket.class,
    AuthProviderConfig.class,
    ApiRequestLog.class
  );

  @Inject
  SessionFactory sessionFactory;

  public void clean(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      deleteDataPermissionControls(session, projectId);
      for (Class<?> model : APP_MODELS) {
        try {
          session.dsl().deleteFrom(model).where(field("app_id").eq(projectId)).execute();
        } catch (Exception e) {
          log.warn("Failed to clean app model '{}' for project '{}': {}", model.getSimpleName(), projectId, e.getMessage());
        }
      }
      try {
        // f_app 与 f_project 一对一共享主键，按 id 删除
        session.dsl().deleteFrom(App.class).where(field("id").eq(projectId)).execute();
      } catch (Exception e) {
        log.warn("Failed to clean app for project '{}': {}", projectId, e.getMessage());
      }
      for (Class<?> model : PLATFORM_MODELS) {
        try {
          session.dsl().deleteFrom(model).where(field("project_id").eq(projectId)).execute();
        } catch (Exception e) {
          log.warn("Failed to clean platform model '{}' for project '{}': {}", model.getSimpleName(), projectId, e.getMessage());
        }
      }
    }
  }

  /**
   * 数据权限控制行通过 authorization_id 间接归属应用，需在删除授权行之前先清理。
   */
  private void deleteDataPermissionControls(Session session, String projectId) {
    try {
      List<String> authorizationIds = session.dsl().selectFrom(AppDataAuthorization.class)
        .where(appDataAuthorization.appId.eq(projectId))
        .execute().stream().map(AppDataAuthorization::getId).toList();
      if (!authorizationIds.isEmpty()) {
        session.dsl().deleteFrom(AppDataPermissionControl.class)
          .where(appDataPermissionControl.authorizationId.in(authorizationIds)).execute();
      }
    } catch (Exception e) {
      log.warn("Failed to clean data permission controls for project '{}': {}", projectId, e.getMessage());
    }
  }
}
