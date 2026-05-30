package dev.flexmodel.branch;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.config.SessionConfig;
import dev.flexmodel.common.utils.StringUtils;
import dev.flexmodel.connect.SessionDatasource;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.sql.JdbcSchemaManager;
import dev.flexmodel.sql.SchemaManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.zaxxer.hikari.HikariDataSource;

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
  ProjectService projectService;

  @Inject
  SessionDatasource sessionDatasource;

  @Inject
  FlexmodelConfig flexmodelConfig;

  private final SchemaManager schemaManager = new JdbcSchemaManager();

  public List<Branch> listBranches(String projectId) {
    Project project = projectService.findProject(projectId);
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
    mainBranch.setDatabaseName(project.getDatabaseName());
    result.add(mainBranch);
    result.addAll(dbBranches);
    return result;
  }

  public Branch createBranch(String projectId, String branchName, String sourceBranch, String description) {
    // 1. 校验分支名
    if (!BRANCH_NAME_PATTERN.matcher(branchName).matches()) {
      throw new IllegalArgumentException("分支名格式不正确，需以小写字母开头，由小写字母、数字和下划线组成，长度2~63个字符");
    }
    Project project = projectService.findProject(projectId);
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
      sourceDbName = project.getDatabaseName();
    } else {
      Branch sourceBranchRecord = branchRepository.findByProjectIdAndName(projectId, sourceBranch);
      if (sourceBranchRecord == null) {
        throw new IllegalArgumentException("源分支 " + sourceBranch + " 不存在");
      }
      sourceDbName = sourceBranchRecord.getDatabaseName();
    }

    // 3. 计算目标数据库名
    String branchDbName = project.getDatabaseName() + "_" + branchName;

    // 4. 复制数据库文件（SQLite）
    copyDatabase(sourceDbName, branchDbName);

    // 5. 注册新 SchemaProvider
    sessionDatasource.registerSchema(branchDbName);

    // 6. 保存分支记录
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
    Project project = projectService.findProject(projectId);
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
      DataSource systemDs = getSystemDataSource();
      schemaManager.dropSchema(systemDs, branch.getDatabaseName());
    } catch (Exception e) {
      log.warn("删除分支数据库失败: {}", e.getMessage());
    }

    // 3. 删除分支记录
    branchRepository.delete(projectId, branchName);
  }

  public Project switchBranch(String projectId, String branchName) {
    Project project = projectService.findProject(projectId);
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
    return projectService.updateProject(project);
  }

  /**
   * 复制 SQLite 数据库文件。
   * 通过项目 URL 模板推算文件路径，直接拷贝文件。
   */
  private void copyDatabase(String sourceDbName, String targetDbName) {
    String sourceUrl = flexmodelConfig.projectUrlTemplate().replace("{{databaseName}}", sourceDbName);
    String targetUrl = flexmodelConfig.projectUrlTemplate().replace("{{databaseName}}", targetDbName);

    // 从 JDBC URL 中提取文件路径（去掉 jdbc:sqlite:file: 前缀）
    String sourceFilePath = sourceUrl.replaceFirst("^jdbc:sqlite:file:", "");
    String targetFilePath = targetUrl.replaceFirst("^jdbc:sqlite:file:", "");

    Path source = Path.of(sourceFilePath);
    Path target = Path.of(targetFilePath);

    if (!Files.exists(source)) {
      throw new RuntimeException("源数据库文件不存在: " + source.toAbsolutePath());
    }

    try {
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
      log.info("复制数据库文件: {} -> {}", source.toAbsolutePath(), target.toAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException("复制数据库文件失败: " + e.getMessage(), e);
    }
  }

  private DataSource getSystemDataSource() {
    FlexmodelConfig.DatasourceConfig config = flexmodelConfig.datasources().get(SessionConfig.SYSTEM_DS_KEY);
    HikariDataSource ds = new HikariDataSource();
    ds.setMaxLifetime(30000);
    ds.setJdbcUrl(config.url());
    ds.setUsername(config.username().orElse(null));
    ds.setPassword(config.password().orElse(null));
    return ds;
  }
}
