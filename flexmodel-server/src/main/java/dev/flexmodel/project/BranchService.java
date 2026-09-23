package dev.flexmodel.project;

import dev.flexmodel.SchemaProvider;
import dev.flexmodel.graphql.GraphQLManager;
import dev.flexmodel.graphql.consumer.GraphQLEventConsumer;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SchemaRegistry;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.NativeQueryDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.project.dto.BranchMergeRequest;
import dev.flexmodel.project.dto.BranchProgressEvent;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.*;
import org.eclipse.microprofile.context.ManagedExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * 分支服务，管理分支的创建、删除、切换。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class BranchService {

  /**
   * 分支名格式：小写字母开头，由小写字母、数字和下划线组成，长度2~63
   */
  private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");

  @Inject
  BranchRepository branchRepository;

  @Inject
  ProjectRepository projectRepository;

  @Inject
  SchemaRegistry schemaRegistry;

  @Inject
  FlexmodelConfig flexmodelConfig;

  @Inject
  SessionFactory sessionFactory;

  @Inject
  GraphQLEventConsumer graphQLEventConsumer;

  @Inject
  GraphQLManager graphQLManager;

  @Inject
  SessionContext sessionContext;

  @Inject
  ManagedExecutor managedExecutor;

  private final SchemaManager schemaManager = new JdbcSchemaManager();

  public List<Branch> listBranches(String projectId) {
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      return List.of();
    }
    String rootProjectId = resolveRootProjectId(project);
    return branchRepository.findByProjectId(rootProjectId);
  }

  public Flow.Publisher<BranchProgressEvent> createBranch(String projectId, String branchName, String sourceBranch, String description) {
    return publishBranchOperation(
      BranchProgressEvent.Operation.CREATE,
      progressListener -> doCreateBranch(projectId, branchName, sourceBranch, description, progressListener)
    );
  }

  private void doCreateBranch(String projectId, String branchName, String sourceBranch, String description,
                              Consumer<BranchProgressEvent> progressListener) {
    emitStage(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Stage.VALIDATING, 0, null);

    // 1. 校验分支名
    if (!BRANCH_NAME_PATTERN.matcher(branchName).matches()) {
      throw new IllegalArgumentException("分支名格式不正确，需以小写字母开头，由小写字母、数字和下划线组成，长度2~63个字符");
    }
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    // 解析根项目ID：如果当前项目是分支项目，分支记录归属于其父项目
    String rootProjectId = resolveRootProjectId(project);
    if ("main".equals(branchName)) {
      throw new IllegalArgumentException("不能创建名为 main 的分支（已存在）");
    }
    Branch existing = branchRepository.findByProjectIdAndName(rootProjectId, branchName);
    if (existing != null) {
      throw new IllegalArgumentException("分支 " + branchName + " 已存在");
    }

    emitStage(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Stage.RESOLVING_SOURCE, 15, null);

    // 2. 确定源分支的 databaseName
    Branch sourceBranchRecord = branchRepository.findByProjectIdAndName(rootProjectId, sourceBranch);
    if (sourceBranchRecord == null) {
      throw new IllegalArgumentException("源分支 " + sourceBranch + " 不存在");
    }
    String sourceDbName = sourceBranchRecord.getDatabaseName();

    // 3. 计算目标数据库名
    String branchDbName = rootProjectId + "_" + branchName;

    emitStage(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Stage.CREATING_SCHEMA, 25, null);

    // 4. 创建目标物理 Schema
    DataSource systemDs = ProjectService.getSystemDataSource(flexmodelConfig);
    schemaManager.createSchema(systemDs, branchDbName);

    emitStage(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Stage.COPYING_SCHEMA, 40, null);

    // 5. 构建目标数据源并通过 FML 导出导入复制模型结构
    SchemaProvider sourceProvider = sessionFactory.getSchemaProvider(sourceDbName);
    if (sourceProvider == null) {
      throw new IllegalArgumentException("源分支数据库 '" + sourceDbName + "' 未就绪，请确认源分支已正确创建");
    }
    try {
      DataSource targetDs = schemaRegistry.buildJdbcDataSource(branchDbName);
      SchemaProvider targetProvider = new JdbcSchemaProvider(branchDbName, targetDs);
      SchemaCopier copier = SchemaCopierFactory.create(sessionFactory);
      copier.copySchema(sourceProvider, branchDbName, targetProvider);
    } catch (Exception e) {
      // 复制失败时回滚物理 Schema，避免留下孤立数据库
      try {
        schemaManager.dropSchema(systemDs, branchDbName);
      } catch (Exception dropEx) {
        log.warn("回滚物理 Schema '{}' 失败: {}", branchDbName, dropEx.getMessage());
      }
      throw e;
    }

    emit(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Type.STARTED,
      BranchProgressEvent.Stage.MIGRATING_DATA, null, 0, 0, 50, null);

    // 6. 迁移源分支数据到新分支
    try (Session sourceSession = sessionFactory.createFailsafeSession(sourceDbName);
         Session targetSession = sessionFactory.createFailsafeSession(branchDbName)) {
      List<SchemaObject> models = sessionFactory.getModels(sourceDbName);
      int totalModels = (int) models.stream()
        .filter(EntityDefinition.class::isInstance)
        .count();
      int processedModels = 0;
      for (SchemaObject model : models) {
        if (!(model instanceof EntityDefinition entity)) continue;
        MigrationConfig migrationConfig = MigrationConfig.of(model);
        String modelName = entity.getName();
        if (!migrationConfig.isEnabled()) {
          log.info("分支创建: 跳过迁移标记为 @migration(enabled: false) 的模型 {}", modelName);
          emit(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Type.MODEL_SKIPPED,
            BranchProgressEvent.Stage.MIGRATING_DATA, modelName, totalModels, processedModels + 1,
            dataMigrationProgress(processedModels + 1, totalModels), null);
          processedModels++;
          continue;
        }
        emit(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Type.MODEL_STARTED,
          BranchProgressEvent.Stage.MIGRATING_DATA, modelName, totalModels, processedModels,
          dataMigrationProgress(processedModels, totalModels), null);
        try {
          List<Map<String, Object>> records = sourceSession.data().findAll(modelName, new Query());
          if (!records.isEmpty()) {
            targetSession.data().insertAll(modelName, records);
            log.info("分支创建: 模型 {} 迁移 {} 条数据", modelName, records.size());
          }
          emit(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Type.MODEL_COMPLETED,
            BranchProgressEvent.Stage.MIGRATING_DATA, modelName, totalModels, processedModels + 1,
            dataMigrationProgress(processedModels + 1, totalModels), null,
            records.size(), records.size(), records.size(), 0);
        } catch (Exception e) {
          log.warn("分支创建: 迁移模型 {} 数据失败: {}", modelName, e.getMessage());
          emit(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Type.MODEL_FAILED,
            BranchProgressEvent.Stage.MIGRATING_DATA, modelName, totalModels, processedModels + 1,
            dataMigrationProgress(processedModels + 1, totalModels), e.getMessage());
        }
        processedModels++;
      }
    }

    emitStage(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Stage.SAVING_RECORDS, 90, null);

    // 7. 保存分支记录
    Branch branch = new Branch();
    branch.setProjectId(rootProjectId);
    branch.setName(branchName);
    branch.setDatabaseName(branchDbName);
    branch.setSourceBranch(sourceBranch != null ? sourceBranch : "main");
    branch.setDescription(description);
    branch.setCreatedBy(sessionContext.getUserId());
    Branch saved = branchRepository.save(branch);

    // 8. 创建分支项目记录（Supabase 风格：每个分支是独立可寻址的项目）
    String branchProjectId = rootProjectId + "_" + branchName;
    Project branchProject = new Project();
    branchProject.setId(branchProjectId);
    branchProject.setName(project.getName() + " (" + branchName + ")");
    branchProject.setParentProjectId(rootProjectId);
    branchProject.setDatabaseName(branchDbName);
    branchProject.setOwnerId(project.getOwnerId());
    branchProject.setEnabled(true);
    projectRepository.save(branchProject);

    emitStage(progressListener, BranchProgressEvent.Operation.CREATE, BranchProgressEvent.Stage.REFRESHING_GRAPHQL, 95, null);

    // 9. 刷新父项目的所有分支 GraphQL
    graphQLEventConsumer.refreshProject(project);

    emitCompleted(progressListener, BranchProgressEvent.Operation.CREATE, saved);
  }

  public void deleteBranch(String projectId, String branchName) {
    if ("main".equals(branchName)) {
      throw new IllegalArgumentException("不能删除 main 分支");
    }
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    String rootProjectId = resolveRootProjectId(project);
    Branch branch = branchRepository.findByProjectIdAndName(rootProjectId, branchName);
    if (branch == null) {
      throw new IllegalArgumentException("分支 " + branchName + " 不存在");
    }

    // 1. 取消注册 SchemaProvider
    schemaRegistry.unregisterSchema(branch.getDatabaseName());

    // 2. 删除物理数据库
    try {
      DataSource systemDs = ProjectService.getSystemDataSource(flexmodelConfig);
      schemaManager.dropSchema(systemDs, branch.getDatabaseName());
    } catch (Exception e) {
      log.warn("删除分支数据库失败: {}", e.getMessage());
    }

    // 3. 删除分支项目记录
    String branchProjectId = rootProjectId + "_" + branchName;
    graphQLManager.removeGraphQL(branchProjectId);
    projectRepository.delete(branchProjectId);

    // 4. 删除分支记录
    branchRepository.delete(rootProjectId, branchName);
  }

  public Flow.Publisher<BranchProgressEvent> mergeBranch(String projectId, String sourceBranch, String targetBranch,
                                                         BranchMergeRequest.ConflictStrategy conflictStrategy) {
    return publishBranchOperation(
      BranchProgressEvent.Operation.MERGE,
      progressListener -> doMergeBranch(projectId, sourceBranch, targetBranch, conflictStrategy, progressListener)
    );
  }

  private Flow.Publisher<BranchProgressEvent> publishBranchOperation(
    BranchProgressEvent.Operation operation,
    Consumer<Consumer<BranchProgressEvent>> action
  ) {
    SubmissionPublisher<BranchProgressEvent> publisher = new SubmissionPublisher<>();
    managedExecutor.execute(() -> {
      try {
        action.accept(publisher::submit);
      } catch (Exception exception) {
        log.error("分支操作失败: {}", operation, exception);
        publisher.submit(BranchProgressEvent.builder()
          .operation(operation)
          .type(BranchProgressEvent.Type.FAILED)
          .message(exception.getMessage())
          .timestamp(Instant.now())
          .build());
      } finally {
        publisher.close();
      }
    });
    return publisher;
  }

  private void doMergeBranch(String projectId, String sourceBranch, String targetBranch,
                             BranchMergeRequest.ConflictStrategy conflictStrategy,
                             Consumer<BranchProgressEvent> progressListener) {
    // 1. 校验参数
    if (sourceBranch == null || sourceBranch.isBlank()) {
      throw new IllegalArgumentException("源分支不能为空");
    }
    if (targetBranch == null || targetBranch.isBlank()) {
      targetBranch = "main";
    }
    if (sourceBranch.equals(targetBranch)) {
      throw new IllegalArgumentException("源分支和目标分支不能相同");
    }

    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在");
    }

    // 2. 解析双方 databaseName
    String sourceDbName = resolveBranchDatabaseName(project, sourceBranch);
    String targetDbName = resolveBranchDatabaseName(project, targetBranch);

    // 3. 模型 diff 合并（普通 session）
    List<SchemaObject> sourceModels = sessionFactory.getModels(sourceDbName);
    List<SchemaObject> targetModels = sessionFactory.getModels(targetDbName);
    int totalModels = sourceModels.size();

    log.info("开始合并分支: {} -> {} (策略: {})", sourceBranch, targetBranch, conflictStrategy);
    emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.STARTED,
      BranchProgressEvent.Stage.MERGING_SCHEMA, null, totalModels, 0, 0, null);

    Map<String, SchemaObject> targetModelMap = targetModels.stream()
      .collect(Collectors.toMap(SchemaObject::getName, m -> m));

    try (Session targetSession = sessionFactory.createSession(targetDbName)) {
      for (int modelIndex = 0; modelIndex < sourceModels.size(); modelIndex++) {
        SchemaObject sourceModel = sourceModels.get(modelIndex);
        SchemaObject targetModel = targetModelMap.get(sourceModel.getName());
        try {
          if (targetModel == null) {
            // 目标不存在，创建整个模型
            createSchemaObject(targetSession, sourceModel);
            log.info("新增模型: {}", sourceModel.getName());
          } else if (sourceModel instanceof EntityDefinition sourceEntity
            && targetModel instanceof EntityDefinition targetEntity) {
            // 字段级 diff
            diffEntityFields(targetSession, sourceEntity, targetEntity);
          } else if (sourceModel instanceof EnumDefinition sourceEnum) {
            // 枚举：drop + recreate
            targetSession.schema().dropModel(sourceEnum.getName());
            targetSession.schema().createEnum(sourceEnum);
            log.info("覆盖枚举: {}", sourceEnum.getName());
          }
          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.STAGE_COMPLETED,
            BranchProgressEvent.Stage.MERGING_SCHEMA, sourceModel.getName(), totalModels, modelIndex + 1,
            percent(modelIndex + 1, totalModels), null);
        } catch (Exception e) {
          log.warn("合并模型 {} 失败: {}", sourceModel.getName(), e.getMessage());
          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_FAILED,
            BranchProgressEvent.Stage.MERGING_SCHEMA, sourceModel.getName(), totalModels, modelIndex + 1,
            percent(modelIndex + 1, totalModels), e.getMessage());
        }
      }
    }

    // 4. 数据记录合并（内存 diff 优化，failsafe session）
    boolean isOverwrite = conflictStrategy == BranchMergeRequest.ConflictStrategy.OVERWRITE;

    try (Session sourceSession = sessionFactory.createFailsafeSession(sourceDbName);
         Session dataTargetSession = sessionFactory.createFailsafeSession(targetDbName)) {
      int totalDataModels = (int) sourceModels.stream()
        .filter(EntityDefinition.class::isInstance)
        .count();
      int processedModels = 0;

      for (SchemaObject sourceModel : sourceModels) {
        if (!(sourceModel instanceof EntityDefinition sourceEntity)) {
          continue;
        }
        String modelName = sourceEntity.getName();

        if (!MigrationConfig.of(sourceModel).isEnabled()) {
          log.info("分支合并: 跳过迁移标记为 @migration(enabled: false) 的模型 {}", modelName);
          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_SKIPPED,
            BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels + 1,
            percent(processedModels + 1, totalDataModels), null);
          processedModels++;
          continue;
        }

        // 新创建的模型，直接批量插入所有源数据
        if (targetModelMap.get(modelName) == null) {
          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_STARTED,
            BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels,
            percent(processedModels, totalDataModels), null);
          try {
            List<Map<String, Object>> sourceRecords = sourceSession.data().findAll(modelName, new Query());
            if (!sourceRecords.isEmpty()) {
              dataTargetSession.data().insertAll(modelName, sourceRecords);
              log.info("新模型 {} 插入 {} 条数据", modelName, sourceRecords.size());
            }
            emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_COMPLETED,
              BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels + 1,
              percent(processedModels + 1, totalDataModels), null,
              sourceRecords.size(), 0, sourceRecords.size(), 0);
          } catch (Exception e) {
            log.warn("合并新模型 {} 数据失败: {}", modelName, e.getMessage());
            emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_FAILED,
              BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels + 1,
              percent(processedModels + 1, totalDataModels), e.getMessage());
          }
          processedModels++;
          continue;
        }

        try {
          // 双方都有此模型，按主键 diff
          TypedField<?, ?> idField = sourceEntity.findIdField().orElse(null);
          if (idField == null) {
            log.warn("模型 {} 没有主键，跳过数据合并", modelName);
            emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_SKIPPED,
              BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels + 1,
              percent(processedModels + 1, totalDataModels), null);
            processedModels++;
            continue;
          }
          String idFieldName = idField.getName();

          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_STARTED,
            BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels,
            percent(processedModels, totalDataModels), null);

          // 全量加载双方数据
          List<Map<String, Object>> sourceRecords = sourceSession.data().findAll(modelName, new Query());
          List<Map<String, Object>> targetRecords = dataTargetSession.data().findAll(modelName, new Query());

          // 目标按主键建索引
          Map<Object, Map<String, Object>> targetMap = new HashMap<>();
          for (Map<String, Object> record : targetRecords) {
            Object id = record.get(idFieldName);
            if (id != null) {
              targetMap.put(id, record);
            }
          }

          // 内存 diff
          List<Map<String, Object>> toInsert = new ArrayList<>();
          List<Map<String, Object>> toUpdate = new ArrayList<>();

          for (Map<String, Object> sourceRecord : sourceRecords) {
            Object id = sourceRecord.get(idFieldName);
            if (id == null) continue;
            if (!targetMap.containsKey(id)) {
              toInsert.add(sourceRecord);
            } else if (isOverwrite) {
              toUpdate.add(sourceRecord);
            }
          }

          // 批量插入
          if (!toInsert.isEmpty()) {
            dataTargetSession.data().insertAll(modelName, toInsert);
            log.info("模型 {} 插入 {} 条新数据", modelName, toInsert.size());
          }

          // 逐条更新
          for (Map<String, Object> record : toUpdate) {
            Object id = record.get(idFieldName);
            dataTargetSession.data().updateById(modelName, record, id);
          }
          if (!toUpdate.isEmpty()) {
            log.info("模型 {} 更新 {} 条数据", modelName, toUpdate.size());
          }
          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_COMPLETED,
            BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels + 1,
            percent(processedModels + 1, totalDataModels), null,
            sourceRecords.size(), targetRecords.size(), toInsert.size(), toUpdate.size());
        } catch (Exception e) {
          log.warn("合并模型 {} 数据失败: {}", modelName, e.getMessage());
          emit(progressListener, BranchProgressEvent.Operation.MERGE, BranchProgressEvent.Type.MODEL_FAILED,
            BranchProgressEvent.Stage.MERGING_DATA, modelName, totalDataModels, processedModels + 1,
            percent(processedModels + 1, totalDataModels), e.getMessage());
        }
        processedModels++;
      }
    }

    log.info("分支合并完成: {} -> {}", sourceBranch, targetBranch);
    emitCompleted(progressListener, BranchProgressEvent.Operation.MERGE, null);
  }

  private int percent(int processed, int total) {
    if (total <= 0) {
      return 100;
    }
    return Math.round(processed * 100.0F / total);
  }

  private int dataMigrationProgress(int processedModels, int totalModels) {
    if (totalModels <= 0) {
      return 90;
    }
    return 50 + Math.round(40.0F * processedModels / totalModels);
  }

  private void emitStage(Consumer<BranchProgressEvent> progressListener,
                         BranchProgressEvent.Operation operation,
                         BranchProgressEvent.Stage stage,
                         int progress,
                         String message) {
    emit(progressListener, operation, BranchProgressEvent.Type.STAGE_COMPLETED, stage,
      null, 0, 0, progress, message);
  }

  private void emitCompleted(Consumer<BranchProgressEvent> progressListener,
                             BranchProgressEvent.Operation operation,
                             Object result) {
    progressListener.accept(BranchProgressEvent.builder()
      .operation(operation)
      .type(BranchProgressEvent.Type.COMPLETED)
      .progress(100)
      .result(result)
      .timestamp(Instant.now())
      .build());
  }

  private void emit(Consumer<BranchProgressEvent> progressListener,
                    BranchProgressEvent.Operation operation,
                    BranchProgressEvent.Type type,
                    BranchProgressEvent.Stage stage,
                    String modelName,
                    int totalModels,
                    int processedModels,
                    int progress,
                    String message) {
    emit(progressListener, operation, type, stage, modelName, totalModels, processedModels, progress, message,
      0, 0, 0, 0);
  }

  private void emit(Consumer<BranchProgressEvent> progressListener,
                    BranchProgressEvent.Operation operation,
                    BranchProgressEvent.Type type,
                    BranchProgressEvent.Stage stage,
                    String modelName,
                    int totalModels,
                    int processedModels,
                    int progress,
                    String message,
                    long sourceRecords,
                    long targetRecords,
                    long insertedRecords,
                    long updatedRecords) {
    progressListener.accept(BranchProgressEvent.builder()
      .operation(operation)
      .type(type)
      .stage(stage)
      .modelName(modelName)
      .totalModels(totalModels)
      .processedModels(processedModels)
      .progress(progress)
      .sourceRecords(sourceRecords)
      .targetRecords(targetRecords)
      .insertedRecords(insertedRecords)
      .updatedRecords(updatedRecords)
      .message(message)
      .timestamp(Instant.now())
      .build());
  }

  /**
   * 解析指定分支对应的 databaseName。
   */
  private String resolveBranchDatabaseName(Project project, String branchName) {
    String rootProjectId = resolveRootProjectId(project);
    Branch branch = branchRepository.findByProjectIdAndName(rootProjectId, branchName);
    if (branch == null) {
      throw new IllegalArgumentException("分支 " + branchName + " 不存在");
    }
    return branch.getDatabaseName();
  }

  /**
   * 解析根项目 ID：如果项目有 parentProjectId（即当前处于分支项目上下文），
   * 返回父项目 ID，因为所有分支记录都归属于父项目。
   */
  private String resolveRootProjectId(Project project) {
    if (project.getParentProjectId() != null) {
      return project.getParentProjectId();
    }
    return project.getId();
  }

  private void createSchemaObject(Session session, SchemaObject model) {
    if (model instanceof EntityDefinition entityDefinition) {
      session.schema().createEntity(entityDefinition);
    } else if (model instanceof EnumDefinition enumDefinition) {
      session.schema().createEnum(enumDefinition);
    } else if (model instanceof NativeQueryDefinition nativeQueryDefinition) {
      session.schema().createNativeQuery(nativeQueryDefinition);
    }
  }

  private void diffEntityFields(Session session, EntityDefinition source, EntityDefinition target) {
    for (TypedField<?, ?> field : source.getFields()) {
      try {
        TypedField<?, ?> targetField = target.getField(field.getName());
        if (targetField == null) {
          session.schema().createField(field);
          log.info("模型 {} 新增字段: {}", source.getName(), field.getName());
        } else if (!field.equals(targetField)) {
          session.schema().modifyField(field);
          log.info("模型 {} 修改字段: {}", source.getName(), field.getName());
        }
      } catch (Exception e) {
        log.warn("合并字段 {} 失败: {}", field.getName(), e.getMessage());
      }
    }
  }

}
