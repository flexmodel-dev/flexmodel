package dev.flexmodel.project;

import dev.flexmodel.api.consumer.GraphQLEventConsumer;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SchemaInitializer;
import dev.flexmodel.common.SchemaRegistry;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.config.AgroalDataSourceFactory;
import dev.flexmodel.common.config.EngineConfig;
import dev.flexmodel.common.utils.StringUtils;
import dev.flexmodel.flow.service.FlowDeploymentService;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import dev.flexmodel.projectauth.AuthProviderConfigService;
import dev.flexmodel.sql.JdbcSchemaManager;
import dev.flexmodel.sql.SchemaManager;
import dev.flexmodel.storage.BucketRepository;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class ProjectService {

  /**
   * 项目ID格式：以小写字母开头，由小写字母、数字和下划线组成，长度2~63个字符
   */
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
  BucketRepository bucketRepository;
  @Inject
  SchemaRegistry schemaRegistry;
  @Inject
  SchemaInitializer schemaInitializer;
  @Inject
  FlexmodelConfig flexmodelConfig;
  @Inject
  GraphQLEventConsumer graphQLEventConsumer;
  @Inject
  AuthProviderConfigService authProviderConfigService;

  private final SchemaManager schemaManager = new JdbcSchemaManager();

  public List<Project> findProjects() {
    return projectRepository.findProjects();
  }

  public List<Project> findTopLevelProjects() {
    return projectRepository.findTopLevelProjects();
  }

  public List<Project> findBranchProjects(String parentProjectId) {
    return projectRepository.findBranchProjects(parentProjectId);
  }

  public List<ProjectResponse> findProjects(ProjectListRequest request) {
    return projectRepository.findTopLevelProjects().stream()
      .map(project -> {
          ProjectResponse response = toProjectResponse(project);
          try {
            if (Objects.equals(request.getIncldue(), "stats")) {
              ProjectResponse.ProjectStats projectStats = new ProjectResponse.ProjectStats(
                -1,
                flowDeploymentService.count(project.getId()),
                bucketRepository.count("PROJECT", project.getId())
              );
              response.setStats(projectStats);
            }

          } catch (Exception e) {
            log.error("查询项目统计信息失败, projectId={}", project.getId(), e);
          }
          return response;
        }
      ).toList();
  }

  @CacheResult(cacheName = "project-cache")
  public Project findProject(String projectId) {
    return projectRepository.findProject(projectId);
  }

  /**
   * 根据项目当前活跃分支解析对应的 databaseName。
   * 优先从 f_branch 表查询，若未找到则回退到 project.databaseName（向后兼容），最终回退到 projectId。
   */
  public String resolveDatabaseName(String projectId) {
    Project project = findProject(projectId);
    if (project == null) {
      throw new IllegalArgumentException("项目不存在: " + projectId);
    }
    return project.getDatabaseName();
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
    // 分支项目（parentProjectId 不为空）不拥有分支列表，使用父项目的分支
    String branchOwnerId = project.getParentProjectId() != null ? project.getParentProjectId() : project.getId();
    response.setBranches(branchService.listBranches(branchOwnerId));
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
    // main 分支的 databaseName 约定为 projectId
    String mainDatabaseName = project.getId();
    project.setOwnerId(SessionContextHolder.getUserId() != null ? SessionContextHolder.getUserId() : "admin");
    project.setDatabaseName(mainDatabaseName);


    // 1. 创建物理 Schema
    DataSource systemDs = getSystemDataSource(flexmodelConfig);
    schemaManager.createSchema(systemDs, mainDatabaseName);

    // 2. 注册 SchemaProvider 到 SessionFactory（必须在初始化表结构之前）
    schemaRegistry.registerSchema(mainDatabaseName);

    // 3. 用 project.fml 初始化表结构
    schemaInitializer.init(mainDatabaseName);

    // 4. 保存 f_project 记录
    Project saved = projectRepository.save(project);

    // 5. 创建 main 分支记录
    Branch mainBranch = new Branch();
    mainBranch.setProjectId(saved.getId());
    mainBranch.setName("main");
    mainBranch.setDatabaseName(mainDatabaseName);
    mainBranch.setDescription("主分支");
    mainBranch.setCreatedBy(project.getOwnerId());
    branchRepository.save(mainBranch);

    // 6. 为新项目生成 GraphQL Schema
    graphQLEventConsumer.refreshProject(saved);

    return saved;
  }

  @CacheInvalidateAll(cacheName = "project-cache")
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
    // 保护 databaseName 不被覆写（PUT 请求可能不包含此字段）
    if (project.getDatabaseName() == null) {
      project.setDatabaseName(existingProject.getDatabaseName());
    }
    if(project.getMetadata()==null) {
      project.setMetadata(existingProject.getMetadata());
    }
    return projectRepository.save(project);
  }

  @CacheInvalidateAll(cacheName = "project-cache")
  public void deleteProject(String projectId) {
    if ("default".equals(projectId)) {
      throw new IllegalArgumentException("默认项目不能删除");
    }
    Project project = findProject(projectId);
    DataSource systemDs = getSystemDataSource(flexmodelConfig);

    // 0. 删除所有分支项目（取消注册 SchemaProvider、删除物理数据库、删除 f_project 记录）
    List<Project> branchProjects = projectRepository.findBranchProjects(projectId);
    for (Project bp : branchProjects) {
      schemaRegistry.unregisterSchema(bp.getDatabaseName());
      schemaRegistry.unregisterModels(bp.getDatabaseName());
      try {
        schemaManager.dropSchema(systemDs, bp.getDatabaseName());
      } catch (Exception e) {
        // 物理 Schema 删除失败不阻断流程
      }
      graphQLEventConsumer.removeProject(bp.getId());
      projectRepository.delete(bp.getId());
    }

    // 1. 删除所有分支记录和取消注册 SchemaProvider（幂等清理物理 Schema）
    List<Branch> branches = branchRepository.findByProjectId(projectId);
    for (Branch branch : branches) {
      schemaRegistry.unregisterSchema(branch.getDatabaseName());
      schemaRegistry.unregisterModels(branch.getDatabaseName());
      try {
        schemaManager.dropSchema(systemDs, branch.getDatabaseName());
      } catch (Exception e) {
        // 物理 Schema 删除失败不阻断流程（可能已在步骤 0 中删除）
      }
      branchRepository.delete(projectId, branch.getName());
    }

    // 2. 取消注册 main 分支 SchemaProvider
    String mainDatabaseName = resolveDatabaseName(projectId);
    schemaRegistry.unregisterSchema(mainDatabaseName);
    schemaRegistry.unregisterModels(mainDatabaseName);

    // 3. 删除物理 Schema
    try {
      schemaManager.dropSchema(systemDs, mainDatabaseName);
    } catch (Exception e) {
      // Schema 删除失败不影响记录删除，记录日志即可
      // 部分数据库（如 Oracle）可能无法自动删除
    }

    // 4. 移除 GraphQL
    graphQLEventConsumer.removeProject(projectId);

    // 5. 删除 f_project 记录
    projectRepository.delete(projectId);
  }

  /**
   * 构建系统数据源，用于执行 Schema 管理 DDL。
   */
  static DataSource getSystemDataSource(FlexmodelConfig flexmodelConfig) {
    FlexmodelConfig.DatasourceConfig config = flexmodelConfig.datasources().get(EngineConfig.SYSTEM_DS_KEY);
    return AgroalDataSourceFactory.createSystemDataSource(
      config.url(),
      config.username().orElse(null),
      config.password().orElse(null));
  }
}
