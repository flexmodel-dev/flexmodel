package dev.flexmodel.project;

import com.zaxxer.hikari.HikariDataSource;
import dev.flexmodel.api.consumer.GraphQLEventConsumer;
import dev.flexmodel.common.SchemaInitializer;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.config.SessionConfig;
import dev.flexmodel.common.utils.StringUtils;
import dev.flexmodel.connect.SessionDatasource;
import dev.flexmodel.flow.service.FlowDeploymentService;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.branch.BranchRepository;
import dev.flexmodel.branch.BranchService;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import dev.flexmodel.sql.JdbcSchemaManager;
import dev.flexmodel.sql.SchemaManager;
import dev.flexmodel.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Project;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author cjbi
 */
@ApplicationScoped
public class ProjectService {

  /** 项目ID格式：以小写字母开头，由小写字母、数字和下划线组成，长度2~63个字符 */
  private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");

  @Inject
  ProjectRepository projectRepository;

  @Inject
  BranchRepository branchRepository;

  @Inject
  BranchService branchService;

  @Inject
  FlowDeploymentService flowDeploymentService;
  @Inject
  StorageService storageService;
  @Inject
  SessionDatasource sessionDatasource;
  @Inject
  SchemaInitializer schemaInitializer;
  @Inject
  FlexmodelConfig flexmodelConfig;
  @Inject
  GraphQLEventConsumer graphQLEventConsumer;

  private final SchemaManager schemaManager = new JdbcSchemaManager();

  public List<Project> findProjects() {
    return projectRepository.findProjects();
  }

  public List<ProjectResponse> findProjects(ProjectListRequest request) {
    return projectRepository.findProjects().stream()
      .map(project -> {
          ProjectResponse response = toProjectResponse(project);
          if (Objects.equals(request.getIncldue(), "stats")) {
            ProjectResponse.ProjectStats projectStats = new ProjectResponse.ProjectStats(
              -1,
              flowDeploymentService.count(project.getId()),
              storageService.count(project.getId())
            );
            response.setStats(projectStats);
          }
          return response;
        }
      ).toList();
  }

  public Project findProject(String projectId) {
    return projectRepository.findProject(projectId);
  }

  /**
   * 获取项目详情（包含分支列表）
   */
  public ProjectResponse findProjectResponse(String projectId) {
    Project project = projectRepository.findProject(projectId);
    if (project == null) {
      return null;
    }
    return toProjectResponse(project);
  }

  private ProjectResponse toProjectResponse(Project project) {
    ProjectResponse response = ProjectResponse.fromProject(project);
    response.setBranches(branchService.listBranches(project.getId()));
    return response;
  }

  public Project createProject(Project project) {
    if (StringUtils.isBlank(project.getId())) {
      throw new IllegalArgumentException("项目ID不能为空");
    }
    if (!PROJECT_ID_PATTERN.matcher(project.getId()).matches()) {
      throw new IllegalArgumentException("项目ID格式不正确，需以小写字母开头，由小写字母、数字和下划线组成，长度2~63个字符");
    }
    if (findProject(project.getId()) != null) {
      throw new IllegalArgumentException("项目ID已经存在");
    }
    // 设置 databaseName（若未指定则用 projectId）
    if (StringUtils.isBlank(project.getDatabaseName())) {
      project.setDatabaseName(project.getId());
    }
    project.setOwnerId(SessionContextHolder.getUserId());
    project.setCurrentBranch("main");

    // 1. 创建物理 Schema
    DataSource systemDs = getSystemDataSource();
    schemaManager.createSchema(systemDs, project.getDatabaseName());

    // 2. 注册 SchemaProvider 到 SessionFactory（必须在初始化表结构之前）
    sessionDatasource.registerSchema(project.getDatabaseName());

    // 3. 用 project.fml 初始化表结构
    schemaInitializer.init(project.getDatabaseName());

    // 4. 保存 f_project 记录
    Project saved = projectRepository.save(project);

    // 5. 为新项目生成 GraphQL Schema
    graphQLEventConsumer.refreshProject(saved);

    return saved;
  }

  public Project updateProject(Project project) {
    if ("default".equals(project.getId())) {
      throw new IllegalArgumentException("默认项目不能修改");
    }
    Project existingProject = findProject(project.getId());
    if (existingProject == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    project.setCreatedAt(existingProject.getCreatedAt());
    project.setCreatedBy(existingProject.getCreatedBy());
    project.setOwnerId(existingProject.getOwnerId());
    return projectRepository.save(project);
  }

  public void deleteProject(String projectId) {
    if ("default".equals(projectId)) {
      throw new IllegalArgumentException("默认项目不能删除");
    }
    Project project = findProject(projectId);

    // 0. 删除所有非 main 分支记录和取消注册 SchemaProvider
    List<Branch> branches = branchRepository.findByProjectId(projectId);
    for (Branch branch : branches) {
      sessionDatasource.unregisterSchema(branch.getDatabaseName());
      branchRepository.delete(projectId, branch.getName());
    }

    // 1. 取消注册 SchemaProvider
    sessionDatasource.unregisterSchema(project.getDatabaseName());

    // 2. 删除物理 Schema
    try {
      DataSource systemDs = getSystemDataSource();
      schemaManager.dropSchema(systemDs, project.getDatabaseName());
    } catch (Exception e) {
      // Schema 删除失败不影响记录删除，记录日志即可
      // 部分数据库（如 Oracle）可能无法自动删除
    }

    // 3. 删除 f_project 记录
    projectRepository.delete(projectId);
  }

  /**
   * 构建系统数据源，用于执行 Schema 管理 DDL。
   */
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
