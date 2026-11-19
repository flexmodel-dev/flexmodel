package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * MCP 工具：项目管理
 * 提供项目列表、详情、创建和删除能力
 * <p>
 * 与 {@link AppTools} 相同，注解使用 langchain4j 定义，MCP Server 自动注册复用。
 * 访问控制由 {@link McpToolAccess} 统一处理。
 */
@Slf4j
@ApplicationScoped
public class ProjectTools {

  @Inject
  ProjectService projectService;

  @Inject
  McpToolAccess access;

  @Tool(name = "list_projects", value = """
    List all projects in the Flexmodel system. \
    A project is the top-level container that holds models (entities, enums, native queries), \
    branches, data records, and configurations. \
    Returns an array of project objects with id, name, description, databaseName, etc.\
    """)
  public String list_projects() {
    log.info("list_projects called");
    try {
      access.requireDirectory();
      List<ProjectResponse> projects = projectService.findProjects(new ProjectListRequest());
      return JsonUtils.toJsonString(projects);
    } catch (Exception e) {
      log.error("list_projects failed", e);
      return "Error: list_projects failed - " + e.getMessage();
    }
  }

  @Tool(name = "get_project", value = """
    Get detailed information of a specific project, \
    including its branch list, current active branch, datasource configuration, and metadata.\
    """)
  public String get_project(
    @P("The unique project identifier, e.g. 'dev_test', 'default'") String projectId
  ) {
    log.info("get_project called, projectId={}", projectId);
    try {
      access.requireDirectory();
      ProjectResponse project = projectService.findProjectResponse(projectId);
      if (project == null) {
        return "Error: Project not found: " + projectId;
      }
      return JsonUtils.toJsonString(project);
    } catch (Exception e) {
      log.error("get_project failed, projectId={}", projectId, e);
      return "Error: get_project failed - " + e.getMessage();
    }
  }

  @Tool(name = "create_project", value = """
    Create a new project in the Flexmodel system. \
    The project ID must start with a lowercase letter, contain only lowercase letters, digits, and underscores, \
    and be 2-63 characters long (regex: ^[a-z][a-z0-9_]{1,62}$). \
    A project provides isolated storage for models, data, and branches.\
    """)
  public String create_project(
    @P("""
      Unique project ID: lowercase letter start, only [a-z0-9_], 2-63 chars. \
      e.g. 'my_app', 'test_project'\
      """) String projectId,
    @P("Human-readable project name, e.g. 'My Application'") String projectName,
    @P(value = "Project description (optional)", required = false) String description
  ) {
    log.info("create_project called, projectId={}, projectName={}, description={}", projectId, projectName, description);
    try {
      access.requireDirectory();
      Project project = new Project();
      project.setId(projectId);
      project.setName(projectName);
      project.setDescription(description);
      Project created = projectService.createProject(project);
      return "Project created successfully: " + JsonUtils.toJsonString(created);
    } catch (Exception e) {
      log.error("create_project failed, projectId={}", projectId, e);
      return "Error: create_project failed - " + e.getMessage();
    }
  }

  @Tool(name = "delete_project", value = """
    Delete a project and all its data, branches, models, and associated resources permanently. \
    The built-in 'default' project cannot be deleted. Use with extreme caution.\
    """)
  public String delete_project(
    @P("The project ID to delete, e.g. 'test_project'. Cannot be 'default'.") String projectId
  ) {
    log.info("delete_project called, projectId={}", projectId);
    try {
      access.requireDirectory();
      if ("default".equals(projectId)) {
        return "Error: The default project cannot be deleted.";
      }
      projectService.deleteProject(projectId);
      return "Project deleted successfully: " + projectId;
    } catch (Exception e) {
      log.error("delete_project failed, projectId={}", projectId, e);
      return "Error: delete_project failed - " + e.getMessage();
    }
  }
}
