package dev.flexmodel.project;

import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.codegen.entity.ApprovalInstance;
import dev.flexmodel.codegen.entity.ApprovalRecordLock;
import dev.flexmodel.codegen.entity.ApprovalTask;
import dev.flexmodel.codegen.entity.ApprovalWait;
import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.codegen.entity.FlowInstanceMapping;
import dev.flexmodel.codegen.entity.Function;
import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.codegen.entity.InstanceData;
import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.codegen.entity.PageSite;
import dev.flexmodel.codegen.entity.RuntimeMessage;
import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

/**
 * 清理平台库中属于指定项目的平台级模型数据。
 * <p>
 * 平台级模型统一存于系统库并按 project_id 隔离，项目/分支删除时需要显式清理，避免残留。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class ProjectPlatformDataCleaner {

  /**
   * 按 project_id 隔离的平台级模型清单。顺序保证先删子模型再删主模型。
   */
  private static final List<Class<?>> PLATFORM_MODELS = List.of(
    RuntimeMessage.class,
    ApprovalTask.class,
    ApprovalWait.class,
    ApprovalRecordLock.class,
    ApprovalInstance.class,
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
    PageSite.class,
    AuthProviderConfig.class,
    ApiRequestLog.class,
    AuditLog.class
  );

  @Inject
  SessionFactory sessionFactory;

  public void clean(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      for (Class<?> model : PLATFORM_MODELS) {
        try {
          session.dsl().deleteFrom(model).where(field("project_id").eq(projectId)).execute();
        } catch (Exception e) {
          log.warn("Failed to clean platform model '{}' for project '{}': {}", model.getSimpleName(), projectId, e.getMessage());
        }
      }
    }
  }
}
