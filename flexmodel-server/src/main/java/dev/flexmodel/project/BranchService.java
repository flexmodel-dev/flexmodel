package dev.flexmodel.project;

import dev.flexmodel.SchemaProvider;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.SessionDatasourceImpl;
import dev.flexmodel.connect.SessionDatasource;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.NativeQueryDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.project.dto.BranchMergeRequest;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaManager;
import dev.flexmodel.sql.JdbcSchemaProvider;
import dev.flexmodel.sql.SchemaCopier;
import dev.flexmodel.sql.SchemaCopierFactory;
import dev.flexmodel.sql.SchemaManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 分支服务，管理分支的创建、删除、切换。
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class BranchService {

  /** 分支名格式：小写字母开头，由小写字母、数字和下划线组成，长度2~63 */
  private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");

  @Inject
  BranchRepository branchRepository;

  @Inject
  ProjectRepository projectRepository;

  @Inject
  SessionDatasource sessionDatasource;

  @Inject
  SessionDatasourceImpl sessionDatasourceImpl;

  @Inject
  FlexmodelConfig flexmodelConfig;

  @Inject
  SessionFactory sessionFactory;

  private final SchemaManager schemaManager = new JdbcSchemaManager();

  public List<Branch> listBranches(String projectId) {
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      return List.of();
    }
    List<Branch> dbBranches = branchRepository.findByProjectId(projectId).stream()
      .filter(b -> !"main".equals(b.getName()))
      .toList();
    List<Branch> result = new ArrayList<>();
    // main 分支始终作为第一条，不存储在 f_branch 表中
    Branch mainBranch = new Branch();
    mainBranch.setId("main");
    mainBranch.setProjectId(projectId);
    mainBranch.setName("main");
    mainBranch.setDatabaseName(resolveDatabaseName(project));
    result.add(mainBranch);
    result.addAll(dbBranches);
    return result;
  }

  public Branch createBranch(String projectId, String branchName, String sourceBranch, String description) {
    // 1. 校验分支名
    if (!BRANCH_NAME_PATTERN.matcher(branchName).matches()) {
      throw new IllegalArgumentException("分支名格式不正确，需以小写字母开头，由小写字母、数字和下划线组成，长度2~63个字符");
    }
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    if ("main".equals(branchName)) {
      throw new IllegalArgumentException("不能创建名为 main 的分支（已存在）");
    }
    Branch existing = branchRepository.findByProjectIdAndName(projectId, branchName);
    if (existing != null) {
      throw new IllegalArgumentException("分支 " + branchName + " 已存在");
    }

    // 2. 确定源分支的 databaseName
    String sourceDbName;
    if (sourceBranch == null || "main".equals(sourceBranch)) {
      sourceDbName = resolveDatabaseName(project);
    } else {
      Branch sourceBranchRecord = branchRepository.findByProjectIdAndName(projectId, sourceBranch);
      if (sourceBranchRecord == null) {
        throw new IllegalArgumentException("源分支 " + sourceBranch + " 不存在");
      }
      sourceDbName = sourceBranchRecord.getDatabaseName();
    }

    // 3. 计算目标数据库名
    String branchDbName = resolveDatabaseName(project) + "_" + branchName;

    // 4. 构建目标数据源并通过 FML 导出导入复制模型结构
    DataSource targetDs = sessionDatasourceImpl.buildJdbcDataSource(branchDbName);
    SchemaProvider sourceProvider = sessionFactory.getSchemaProvider(sourceDbName);
    SchemaProvider targetProvider = new JdbcSchemaProvider(branchDbName, targetDs);
    SchemaCopier copier = SchemaCopierFactory.create(sessionFactory);
    copier.copySchema(sourceProvider, branchDbName, targetProvider);

    // 5. 保存分支记录
    Branch branch = new Branch();
    branch.setProjectId(projectId);
    branch.setName(branchName);
    branch.setDatabaseName(branchDbName);
    branch.setSourceBranch(sourceBranch != null ? sourceBranch : "main");
    branch.setDescription(description);
    branch.setCreatedBy(SessionContextHolder.getUserId());
    return branchRepository.save(branch);
  }

  public void deleteBranch(String projectId, String branchName) {
    if ("main".equals(branchName)) {
      throw new IllegalArgumentException("不能删除 main 分支");
    }
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    if (branchName.equals(project.getCurrentBranch())) {
      throw new IllegalArgumentException("不能删除当前活跃分支，请先切换到其他分支");
    }
    Branch branch = branchRepository.findByProjectIdAndName(projectId, branchName);
    if (branch == null) {
      throw new IllegalArgumentException("分支 " + branchName + " 不存在");
    }

    // 1. 取消注册 SchemaProvider
    sessionDatasource.unregisterSchema(branch.getDatabaseName());

    // 2. 删除物理数据库
    try {
      DataSource systemDs = ProjectService.getSystemDataSource(flexmodelConfig);
      schemaManager.dropSchema(systemDs, branch.getDatabaseName());
    } catch (Exception e) {
      log.warn("删除分支数据库失败: {}", e.getMessage());
    }

    // 3. 删除分支记录
    branchRepository.delete(projectId, branchName);
  }

  public Project switchBranch(String projectId, String branchName) {
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    if (!"main".equals(branchName)) {
      Branch branch = branchRepository.findByProjectIdAndName(projectId, branchName);
      if (branch == null) {
        throw new IllegalArgumentException("分支 " + branchName + " 不存在");
      }
    }
    project.setCurrentBranch(branchName);
    return projectRepository.save(project);
  }

  public void mergeBranch(String projectId, String sourceBranch, String targetBranch,
                          BranchMergeRequest.ConflictStrategy conflictStrategy) {
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

    log.info("开始合并分支: {} -> {} (策略: {})", sourceBranch, targetBranch, conflictStrategy);

    // 3. 模型 diff 合并（普通 session）
    List<SchemaObject> sourceModels = sessionFactory.getModels(sourceDbName);
    List<SchemaObject> targetModels = sessionFactory.getModels(targetDbName);
    Map<String, SchemaObject> targetModelMap = targetModels.stream()
        .collect(Collectors.toMap(SchemaObject::getName, m -> m));

    try (Session targetSession = sessionFactory.createSession(targetDbName)) {
      for (SchemaObject sourceModel : sourceModels) {
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
        } catch (Exception e) {
          log.warn("合并模型 {} 失败: {}", sourceModel.getName(), e.getMessage());
        }
      }
    }

    // 4. 数据记录合并（内存 diff 优化，failsafe session）
    boolean isOverwrite = conflictStrategy == BranchMergeRequest.ConflictStrategy.OVERWRITE;

    try (Session sourceSession = sessionFactory.createFailsafeSession(sourceDbName);
         Session dataTargetSession = sessionFactory.createFailsafeSession(targetDbName)) {

      for (SchemaObject sourceModel : sourceModels) {
        if (!(sourceModel instanceof EntityDefinition sourceEntity)) {
          continue;
        }
        String modelName = sourceEntity.getName();

        // 新创建的模型，直接批量插入所有源数据
        if (targetModelMap.get(modelName) == null) {
          try {
            List<Map<String, Object>> sourceRecords = sourceSession.data().find(modelName, new Query());
            if (!sourceRecords.isEmpty()) {
              dataTargetSession.data().insertAll(modelName, sourceRecords);
              log.info("新模型 {} 插入 {} 条数据", modelName, sourceRecords.size());
            }
          } catch (Exception e) {
            log.warn("合并新模型 {} 数据失败: {}", modelName, e.getMessage());
          }
          continue;
        }

        try {
          // 双方都有此模型，按主键 diff
          TypedField<?, ?> idField = sourceEntity.findIdField().orElse(null);
          if (idField == null) {
            log.warn("模型 {} 没有主键，跳过数据合并", modelName);
            continue;
          }
          String idFieldName = idField.getName();

          // 全量加载双方数据
          List<Map<String, Object>> sourceRecords = sourceSession.data().find(modelName, new Query());
          List<Map<String, Object>> targetRecords = dataTargetSession.data().find(modelName, new Query());

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
        } catch (Exception e) {
          log.warn("合并模型 {} 数据失败: {}", modelName, e.getMessage());
        }
      }
    }

    log.info("分支合并完成: {} -> {}", sourceBranch, targetBranch);
  }

  /**
   * 解析指定分支对应的 databaseName。
   */
  private String resolveBranchDatabaseName(Project project, String branchName) {
    if ("main".equals(branchName)) {
      if (project.getDatabaseName() != null && !project.getDatabaseName().isBlank()) {
        return project.getDatabaseName();
      }
      return project.getId();
    }
    Branch branch = branchRepository.findByProjectIdAndName(project.getId(), branchName);
    if (branch == null) {
      throw new IllegalArgumentException("分支 " + branchName + " 不存在");
    }
    return branch.getDatabaseName();
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

  /**
   * 根据项目当前活跃分支解析对应的 databaseName。
   * 优先从 f_branch 表查询，若未找到则回退到 project.databaseName（向后兼容），最终回退到 projectId。
   */
  private String resolveDatabaseName(Project project) {
    String currentBranch = project.getCurrentBranch();
    // 非 main 分支：从 f_branch 表查询
    if (currentBranch != null && !"main".equals(currentBranch)) {
      Branch branch = branchRepository.findByProjectIdAndName(project.getId(), currentBranch);
      if (branch == null) {
        throw new IllegalArgumentException("当前分支 " + currentBranch + " 不存在");
      }
      return branch.getDatabaseName();
    }
    // main 分支：优先使用 project.databaseName（向后兼容），否则回退到 projectId
    if (project.getDatabaseName() != null && !project.getDatabaseName().isBlank()) {
      return project.getDatabaseName();
    }
    return project.getId();
  }
}
