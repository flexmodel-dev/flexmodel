package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.FunctionTemplate;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.FunctionService;
import dev.flexmodel.functions.dto.FunctionDeployRequest;
import dev.flexmodel.functions.dto.FunctionPageRequest;
import dev.flexmodel.functions.dto.FunctionResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具：边缘函数
 * 提供函数模板列表、函数 CRUD、部署和调用能力
 * <p>
 * 与 {@link AppTools} 相同，注解使用 langchain4j 定义，MCP Server 自动注册复用。
 * 访问控制由 {@link McpToolAccess} 统一处理。
 */
@Slf4j
@ApplicationScoped
public class FunctionTools {

  @Inject
  FunctionService functionService;

  @Inject
  McpToolAccess access;

  @Tool(name = "list_function_templates", value = """
    List all available function templates (platform-level). \
    Each template provides a pre-built code pattern for common use cases like \
    Hello World, Database Query, Database CRUD, Webhook Handler, Data Aggregation, etc. \
    Each template includes: name, slug, description, sourceFiles (code samples), tags, and icon. \
    Use this tool to understand available function patterns and their code structure \
    before deploying a new function — you can reference a template's sourceFiles as a starting point.\
    """)
  public String list_function_templates() {
    log.info("list_function_templates called");
    try {
      access.requireDirectory();
      List<FunctionTemplate> templates = functionService.list();
      return JsonUtils.toJsonString(templates);
    } catch (Exception e) {
      log.error("list_function_templates failed", e);
      return "Error: list_function_templates failed - " + e.getMessage();
    }
  }

  @Tool(name = "list_functions", value = """
    List Edge Functions in a project with pagination. \
    Returns a paginated result with total count and function list. \
    Each function includes: id, name, sourceFiles, timeout, createdAt, updatedAt.\
    """)
  public String list_functions(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P(value = "Optional function name filter. Pass empty string for no filter.", required = false) String name,
    @P("Page number, starting from 1") int page,
    @P("Number of functions per page, e.g. 10, 20") int size
  ) {
    log.info("list_functions called, projectId={}, name={}, page={}, size={}", projectId, name, page, size);
    try {
      access.requireDirectory();
      FunctionPageRequest request = new FunctionPageRequest();
      request.setName((name == null || name.isBlank()) ? null : name);
      request.setPage(page);
      request.setSize(size);
      PageDTO<FunctionResponse> result = functionService.findPage(projectId, request);
      return JsonUtils.toJsonString(result);
    } catch (Exception e) {
      log.error("list_functions failed, projectId={}", projectId, e);
      return "Error: list_functions failed - " + e.getMessage();
    }
  }

  @Tool(name = "get_function", value = """
    Get detailed information of a specific Edge Function, \
    including its source code files (sourceFiles: filename→content map), timeout, and metadata. \
    Use this to inspect a function's code before modifying or invoking it.\
    """)
  public String get_function(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The function name, e.g. 'hello-world', 'my-query-fn'") String name
  ) {
    log.info("get_function called, projectId={}, name={}", projectId, name);
    try {
      access.requireDirectory();
      FunctionResponse fn = functionService.findByName(projectId, name);
      return JsonUtils.toJsonString(fn);
    } catch (Exception e) {
      log.error("get_function failed, projectId={}, name={}", projectId, name, e);
      return "Error: get_function failed - " + e.getMessage();
    }
  }

  @Tool(name = "deploy_function", value = """
    Deploy (create or update) a Edge Function to a project and deploy it to the Deno Functions Runtime. \
    If a function with the given name already exists, it will be updated (upsert behavior). \
    \
    The sourceFiles parameter must be a JSON object mapping filenames to their TypeScript source code content. \
    The main entry file should be named 'index.ts' and export a default function: \
    'export default async function(req: Request) { ... }'. \
    You can include multiple files (e.g. 'utils.ts', 'helpers.ts') that the main file imports. \
    \
    Use list_function_templates first to see available code patterns and their sourceFiles structure. \
    You can adapt a template's source code as a starting point for your function. \
    \
    The function receives a standard Request object and must return a Response object. \
    Available SDK: import { flexmodelClient } from '@flexmodel/sdk' — provides data.from(model).findMany/findOne/create/update/delete. \
    \
    Example sourceFiles: {"index.ts": "import { flexmodelClient } from '@flexmodel/sdk';\\n\\nexport default async function(req: Request) {\\n  const result = await flexmodelClient.data.from('Student').findMany({ page: 1, size: 10 });\\n  return new Response(JSON.stringify(result), { headers: { 'content-type': 'application/json' } });\\n}"}\
    """)
  public String deploy_function(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The function name, e.g. 'hello-world', 'my-query-fn'") String name,
    @P("""
      Source files as JSON object string: filename→content map. \
      The main entry must be 'index.ts' exporting a default async function(req: Request). \
      Example: {"index.ts": "export default async function(req: Request) { return new Response('OK'); }"}\
      """) String sourceFilesJson,
    @P("Function timeout in seconds (default 30). Max recommended: 300.") int timeout
  ) {
    log.info("deploy_function called, projectId={}, name={}, timeout={}", projectId, name, timeout);
    try {
      access.requireDirectory();
      Map<String, String> sourceFiles = JsonUtils.parseToObject(sourceFilesJson, Map.class);
      FunctionDeployRequest request = new FunctionDeployRequest();
      request.setName(name);
      request.setSourceFiles(sourceFiles);
      request.setTimeout(timeout > 0 ? timeout : 30);
      FunctionResponse result = functionService.deploy(projectId, name, request);
      return "Function deployed: " + JsonUtils.toJsonString(result);
    } catch (Exception e) {
      log.error("deploy_function failed, projectId={}, name={}", projectId, name, e);
      return "Error: deploy_function failed - " + e.getMessage();
    }
  }

  @Tool(name = "invoke_function", value = """
    Invoke (execute) a deployed Edge Function and return its output. \
    The function receives the input data as a standard Request object, \
    processes it in an isolated Deno Worker, and returns the result. \
    \
    The input parameter is passed as the Request body — the function \
    can read it via req.json() or req.text(). Metadata like projectId \
    is available via Request headers (x-flexmodel-project-id, \
    x-flexmodel-function-name). \
    \
    If the function is not currently registered in the runtime (e.g. after a runtime restart), \
    it will be automatically re-deployed before invocation. \
    \
    The function's Response is returned directly. \
    Execution metadata (time, logs) may be available.\
    """)
  public String invoke_function(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The function name to invoke, e.g. 'hello-world', 'my-query-fn'") String name,
    @P(value = """
      Input data to pass to the function as JSON string. \
      This becomes the request body that the function receives via req.json() or req.text(). \
      Pass empty string or null for no input.\
      """, required = false) String inputJson
  ) {
    log.info("invoke_function called, projectId={}, name={}", projectId, name);
    try {
      access.requireDirectory();
      Object input = null;
      if (inputJson != null && !inputJson.isBlank()) {
        input = JsonUtils.parseToObject(inputJson, Object.class);
      }
      Response response = functionService.invoke(projectId, name, input);
      int status = response.getStatus();
      Object body = response.readEntity(Object.class);
      String meta = response.getHeaderString("x-function-meta");
      StringBuilder sb = new StringBuilder();
      sb.append("Function invoked. Status: ").append(status).append("\n");
      sb.append("Response: ").append(JsonUtils.toJsonString(body));
      if (meta != null) {
        sb.append("\nExecution metadata: ").append(meta);
      }
      return sb.toString();
    } catch (Exception e) {
      log.error("invoke_function failed, projectId={}, name={}", projectId, name, e);
      return "Error: invoke_function failed - " + e.getMessage();
    }
  }

  @Tool(name = "delete_function", value = """
    Delete a Edge Function from a project. \
    This removes the function definition from the database AND deletes it from the Deno Functions Runtime. \
    Use with caution as this operation is irreversible.\
    """)
  public String delete_function(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The function name to delete, e.g. 'hello-world', 'my-query-fn'") String name
  ) {
    log.info("delete_function called, projectId={}, name={}", projectId, name);
    try {
      access.requireDirectory();
      functionService.delete(projectId, name);
      return "Function deleted: " + name;
    } catch (Exception e) {
      log.error("delete_function failed, projectId={}, name={}", projectId, name, e);
      return "Error: delete_function failed - " + e.getMessage();
    }
  }
}
