package dev.flexmodel.functions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.auth.service.InternalTokenService;
import dev.flexmodel.codegen.entity.Function;
import dev.flexmodel.codegen.entity.FunctionTemplate;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.flexmodel.codegen.System.function;

/**
 * Function lifecycle management: CRUD, deploy to Deno functions runtime, invoke.
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FunctionService {

  @Inject
  FunctionRepository functionRepository;

  @Inject
  FunctionTemplateRepository functionTemplateRepository;

  @Inject
  FunctionInvoker functionInvoker;

  @Inject
  JwtService jwtService;

  @Inject
  InternalTokenService internalTokenService;

  @Inject
  FlexmodelConfig config;

  @Inject
  ObjectMapper objectMapper;

  /**
   * List all function templates ordered by sort_order.
   */
  public List<FunctionTemplate> list() {
    log.info("Listing function templates");
    return functionTemplateRepository.list();
  }

  // ============================================================
  // CRUD Operations
  // ============================================================

  /**
   * Deploy (upsert) a function: save DB → deploy to Deno
   */
  public FunctionResponse deploy(String projectId, String name, FunctionDeployRequest req) {
    Function fn = functionRepository.findByName(projectId, name);
    boolean isNew = (fn == null);

    if (isNew) {
      fn = new Function();
      fn.setName(name);
      fn.setTimeout(req.getTimeout() != null ? req.getTimeout() : 30);
      fn.setCreatedAt(LocalDateTime.now());
    } else {
      if (req.getName() != null && !req.getName().isBlank()) {
        fn.setName(req.getName());
      }
      if (req.getTimeout() != null) {
        fn.setTimeout(req.getTimeout());
      }
    }

    if (req.getSourceFiles() != null && !req.getSourceFiles().isEmpty()) {
      fn.setSourceFiles(req.getSourceFiles());
    }
    fn.setUpdatedAt(LocalDateTime.now());

    functionRepository.save(projectId, fn);
    log.info("Function {}: {}:{}", isNew ? "created" : "updated", projectId, name);

    // Deploy to Deno functions runtime
    try {
      deployToRuntime(projectId, fn);
    } catch (Exception e) {
      log.error("Deploy failed for function: {}:{}", projectId, name, e);
    }

    return FunctionResponse.from(fn);
  }

  /**
   * Delete a function: delete from Deno → delete from DB
   */
  public void delete(String projectId, String name) {
    Function fn = functionRepository.findByName(projectId, name);
    if (fn == null) {
      throw new FunctionException("Function not found: " + name);
    }

    functionInvoker.delete(projectId, name);
    functionRepository.deleteById(projectId, fn.getId());
    log.info("Function deleted: {}:{}", projectId, name);
  }

  /**
   * Get function detail
   */
  public FunctionResponse findByName(String projectId, String name) {
    Function fn = functionRepository.findByName(projectId, name);
    if (fn == null) {
      throw new FunctionException("Function not found: " + name);
    }
    return FunctionResponse.from(fn);
  }

  /**
   * List functions with pagination
   */
  public PageDTO<FunctionResponse> findPage(String projectId, FunctionPageRequest request) {
    Predicate filter = Expressions.TRUE;
    if (request.getName() != null && !request.getName().isBlank()) {
      filter = filter.and(function.name.eq(request.getName()));
    }

    long total = functionRepository.count(projectId, filter);
    if (total == 0) {
      return PageDTO.empty();
    }

    List<FunctionResponse> list = functionRepository.find(projectId, filter, request.getPage(), request.getSize())
      .stream()
      .map(FunctionResponse::from)
      .toList();

    return new PageDTO<>(list, total);
  }

  // ============================================================
  // Invocation
  // ============================================================

  /**
   * Invoke a function via the Deno functions runtime.
   *
   * <p>不再每次 invoke 都预先 deploy。仅当 runtime 返回 404（函数未注册，如 runtime 重启后）
   * 才按需 deploy 并重试一次。避免频繁写文件触发 Deno --watch 重启。
   *
   * @param projectId project ID
   * @param name function name
   * @param body request body sent directly to the function (becomes Request JSON body)
   */
  public Response invoke(String projectId, String name, Object body) {
    Function fn = functionRepository.findByName(projectId, name);
    if (fn == null) {
      throw new FunctionException("Function not found: " + name);
    }

    // 为本次 invoke 签发 Runtime 回调专用 JWT（5 分钟有效期）
    String authToken = internalTokenService.signToken(projectId);
    // 生成本次调用的唯一ID，用于关联 f_function_log 日志记录
    String invokeId = UUID.randomUUID().toString();

    Response response = functionInvoker.invoke(projectId, name, body, authToken, invokeId);

    // runtime 重启等情况会导致函数未注册（404），此时按需部署后重试一次
    if (response.getStatus() == 404) {
      log.info("Function not registered in runtime, deploying: {}:{}", projectId, name);
      response.close();
      deployToRuntime(projectId, fn);
      response = functionInvoker.invoke(projectId, name, body, authToken, invokeId);
    }

    log.info("Function {} invoked, status={}", name, response.getStatus());
    return response;
  }

  // ============================================================
  // Edge Function — Invoke Token
  // ============================================================

  /**
   * Sign an invoke-token for edge function direct invocation.
   *
   * <p>The invoke-token is a JWT signed with "svc:invoke" + jwtSecret (5-minute TTL),
   * containing projectId, functionName, authToken (for SDK callback), and invokeId.
   * The frontend uses this token to directly call the Deno Runtime at the URL
   * defined by {@code flexmodel.edge-url-template}.
   *
   * @param projectId project ID
   * @param name      function name
   * @return InvokeTokenResponse containing the token and runtime URL
   */
  public InvokeTokenResponse signInvokeToken(String projectId, String name) {
    Function fn = functionRepository.findByName(projectId, name);
    if (fn == null) {
      throw new FunctionException("Function not found: " + name);
    }

    // 签发 Runtime 回调专用 JWT（SDK 在 Worker 内回调 Java API 时使用）
    String authToken = internalTokenService.signToken(projectId);
    // 生成本次调用的唯一ID
    String invokeId = UUID.randomUUID().toString();

    // 签发 invoke-token（密钥 = "svc:invoke" + jwtSecret，与系统用户 JWT 和 runtime JWT 不同）
    String invokeToken = jwtService.sign(
      "svc:invoke",
      Map.of(
        "projectId", projectId,
        "functionName", name,
        "authToken", authToken,
        "invokeId", invokeId
      ),
      Duration.ofMinutes(5)
    );

    // 构造边缘函数 URL（使用模板替换）
    String runtimeUrl = config.edgeUrlTemplate()
      .replace("{{projectId}}", projectId)
      .replace("{{name}}", name);

    log.info("Invoke-token signed for {}:{}", projectId, name);

    return InvokeTokenResponse.builder()
      .invokeToken(invokeToken)
      .runtimeUrl(runtimeUrl)
      .build();
  }

  // ============================================================
  // Private Helpers
  // ============================================================

  /**
   * Build a FunctionRuntimeDeployRequest for the given function.
   * Used by internal deploy.
   */
  public FunctionRuntimeDeployRequest getRuntimeDeployRequest(String projectId, String name) {
    Function fn = functionRepository.findByName(projectId, name);
    if (fn == null) {
      throw new FunctionException("Function not found: " + name);
    }
    if (fn.getId() == null || fn.getId().isBlank()) {
      throw new FunctionException("Function ID is required: " + fn.getName());
    }

    Map<String, String> sourceFiles = objectMapper.convertValue(
      fn.getSourceFiles(), new TypeReference<Map<String, String>>() {
      });

    if (sourceFiles == null || sourceFiles.isEmpty()) {
      throw new FunctionException("Function source files are required: " + fn.getName());
    }

    return FunctionRuntimeDeployRequest.builder()
      .projectId(projectId)
      .functionId(fn.getId())
      .name(fn.getName())
      .sourceFiles(sourceFiles)
      .timeout(fn.getTimeout() != null ? fn.getTimeout() : 30)
      .build();
  }

  private void deployToRuntime(String projectId, Function fn) {
    if (fn.getId() == null || fn.getId().isBlank()) {
      throw new FunctionException("Function ID is required for deployment: " + fn.getName());
    }

    Map<String, String> sourceFiles = objectMapper.convertValue(
      fn.getSourceFiles(), new TypeReference<Map<String, String>>() {
      });

    if (sourceFiles == null || sourceFiles.isEmpty()) {
      throw new FunctionException("Function source files are required for deployment: " + fn.getName());
    }

    FunctionRuntimeDeployRequest deployReq = FunctionRuntimeDeployRequest.builder()
      .projectId(projectId)
      .functionId(fn.getId())
      .name(fn.getName())
      .sourceFiles(sourceFiles)
      .timeout(fn.getTimeout() != null ? fn.getTimeout() : 30)
      .build();

    functionInvoker.deploy(deployReq);
  }
}
