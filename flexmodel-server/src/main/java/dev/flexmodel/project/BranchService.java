package dev.flexmodel.project;

import dev.flexmodel.SchemaProvider;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.SessionDatasourceImpl;
import dev.flexmodel.connect.SessionDatasource;
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
import java.util.List;
import java.util.regex.Pattern;


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
