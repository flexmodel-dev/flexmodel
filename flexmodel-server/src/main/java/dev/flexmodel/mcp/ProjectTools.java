package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * MCP 工具：项目管理
 * 提供项目列表、详情、创建和删除能力
 */
public class ProjectTools {

  private static final Logger log = Logger.getLogger(ProjectTools.class);

  @Inject
  ProjectService projectService;


  @Tool(description = """
    List all projects in the Flexmodel system. \
    A project is the top-level container that holds models (entities, enums, native queries), \
    branches, data records, and configurations. \
    Returns an array of project objects with id, name, description, databaseName, etc.\
    """)
  public String list_projects() {
    log.info("list_projects called");
    try {
      List<ProjectResponse> projects = projectService.findProjects(new ProjectListRequest());
      return JsonUtils.toJsonString(projects);
    } catch (Exception e) {
      log.error("list_projects failed", e);
      return "Error: list_projects failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Get detailed information of a specific project, \
    including its branch list, current active branch, datasource configuration, and metadata.\
    """)
  public String get_project(
    @ToolArg(description = "The unique project identifier, e.g. 'dev_test', 'default'") String projectId
  ) {
    log.infof("get_project called, projectId=%s", projectId);
    try {
      ProjectResponse project = projectService.findProjectResponse(projectId);
      if (project == null) {
        return "Error: Project not found: " + projectId;
      }
      return JsonUtils.toJsonString(project);
    } catch (Exception e) {
      log.errorf(e, "get_project failed, projectId=%s", projectId);
      return "Error: get_project failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Create a new project in the Flexmodel system. \
    The project ID must start with a lowercase letter, contain only lowercase letters, digits, and underscores, \
    and be 2-63 characters long (regex: ^[a-z][a-z0-9_]{1,62}$). \
    A project provides isolated storage for models, data, and branches.\
    """)
  public String create_project(
    @ToolArg(description = """
      Unique project ID: lowercase letter start, only [a-z0-9_], 2-63 chars. \
      e.g. 'my_app', 'test_project'\
      """) String projectId,
    @ToolArg(description = "Human-readable project name, e.g. 'My Application'") String projectName,
    @ToolArg(description = "Project description (optional)") String description
  ) {
    log.infof("create_project called, projectId=%s, projectName=%s, description=%s", projectId, projectName, description);
    try {
      Project project = new Project();
      project.setId(projectId);
      project.setName(projectName);
      project.setDescription(description);
      Project created = projectService.createProject(project);
      return "Project created successfully: " + JsonUtils.toJsonString(created);
    } catch (Exception e) {
      log.errorf(e, "create_project failed, projectId=%s", projectId);
      return "Error: create_project failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Delete a project and all its data, branches, models, and associated resources permanently. \
    The built-in 'default' project cannot be deleted. Use with extreme caution.\
    """)
  public String delete_project(
    @ToolArg(description = "The project ID to delete, e.g. 'test_project'. Cannot be 'default'.") String projectId
  ) {
    log.infof("delete_project called, projectId=%s", projectId);
    try {
      if ("default".equals(projectId)) {
        return "Error: The default project cannot be deleted.";
      }
      projectService.deleteProject(projectId);
      return "Project deleted successfully: " + projectId;
    } catch (Exception e) {
      log.errorf(e, "delete_project failed, projectId=%s", projectId);
      return "Error: delete_project failed - " + e.getMessage();
    }
  }
}
