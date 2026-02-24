package dev.flexmodel.domain.model.flow.shared.util;

import dev.flexmodel.codegen.entity.Datasource;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.LoggerFactory;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.shared.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 脚本执行上下文：封装请求、响应、环境变量等信息
 * context = {
 *     request: {
 *         method,
 *         url,
 *         headers,
 *         body,
 *         query
 *     },
 *     response: {
 *         status,
 *         headers,
 *         body
 *     },
 *     log(msg){},
 *     utils:{
 *         md5(str){}
 *     }
 * }
 *
 */
@Getter
@Setter
@ToString
public class RequestScriptContext {

  public static final String SCRIPT_CONTEXT_KEY = "context";

  private final String projectId;

  /** 原始请求参数（用户输入） */
  private Request request;

  /** 最终响应数据（后置脚本可修改） */
  private Response response;

  private SessionFactory sessionFactory;

  public RequestScriptContext(String projectId) {
    this.projectId = projectId;
  }

  public record Request(String method,
                        String url,
                        Map<String, String> headers,
                        Map<String, Object> body,
                        Map<String, String> query
  ) {
  }

  public record Response(int status,
                         String message,
                         Map<String, String> headers,
                         Map<String, Object> body
  ) {
  }


  @SuppressWarnings("all")
  public Map<String, Object> buildContextMap() {
    Map<String, Object> context = new HashMap<>();

    // 将 Request record 转换为 Map
    if (request != null) {
      Map<String, Object> requestMap = new HashMap<>();
      requestMap.put("method", request.method());
      requestMap.put("url", request.url());
      requestMap.put("headers", request.headers());
      requestMap.put("body", request.body());
      requestMap.put("query", request.query());
      context.put("request", requestMap);
    }

    // 将 Response record 转换为 Map，使用可变的 HashMap 以便 JavaScript 修改后能同步
    if (response != null) {
      Map<String, Object> responseMap = new HashMap<>();
      responseMap.put("status", response.status());
      responseMap.put("message", response.message());
      responseMap.put("headers", response.headers());
      // 直接使用原始 body Map 的引用，这样修改会反映回去
      responseMap.put("body", response.body());
      context.put("response", responseMap);
    }

    // 工具类
    context.put("utils", new ScriptUtils());
    // 日志
    context.put("log", new Logger(projectId));

    if (sessionFactory != null) {
      Session session = sessionFactory.createSession(projectId);
      List<Datasource> datasources = session.dsl().selectFrom(Datasource.class).execute();
      Map<String, ScriptExecutionDB> dbs = new HashMap<>();
      for (Datasource datasource : datasources) {
        dbs.put(datasource.getName(), new ScriptExecutionDB(datasource.getName(), sessionFactory));
      }
      context.put("dbs", dbs);
    }
    return context;
  }

  /**
   * 从 Map 中同步回 Response 对象
   * 用于在 JavaScript 执行后，将修改后的值同步回原始的 Response
   *
   * @param contextMap JavaScript 执行后的 context Map
   */
  @SuppressWarnings("all")
  public void syncFromMap(Map<String, Object> contextMap) {
    JsonUtils.updateValue(this, contextMap);
  }


  public static class ScriptUtils {
    public String uuid() {
      return UUID.randomUUID().toString();
    }

  }

  public static class Logger {

    private final String projectId;

    public Logger(String projectId) {
      this.projectId = projectId;
    }

    public void info(String msg, Object... args) {
      LoggerFactory.getLogger(Constants.APP_LOG_CATEGORY_NAME + "_" + projectId).info(msg, args);
    }

    public void error(String msg, Object... args) {
      LoggerFactory.getLogger(Constants.APP_LOG_CATEGORY_NAME + "_" + projectId).error(msg, args);
    }

    public void debug(String msg, Object... args) {
      LoggerFactory.getLogger(Constants.APP_LOG_CATEGORY_NAME + "_" + projectId).debug(msg, args);
    }

    public void warn(String msg, Object... args) {
      LoggerFactory.getLogger(Constants.APP_LOG_CATEGORY_NAME + "_" + projectId).warn(msg, args);
    }

  }

  public static class ScriptExecutionDB {

    private final String datasourceName;
    private final SessionFactory sessionFactory;

    public ScriptExecutionDB(String datasourceName, SessionFactory sessionFactory) {
      this.datasourceName = datasourceName;
      this.sessionFactory = sessionFactory;
    }

    public Map<String, Object> findById(String modelName, String id) {
      try (Session session = sessionFactory.createSession(datasourceName)) {
        return session.data().findById(modelName, id);
      }
    }

    public List<Map<String, Object>> find(String modelName) {
      try (Session session = sessionFactory.createSession(datasourceName)) {
        return session.data().find(modelName, p -> p);
      }
    }

    public List<Map<String, Object>> find(String modelName, Map<String, Object> queryMap) {
      try (Session session = sessionFactory.createSession(datasourceName)) {
        Query query = new Query();
        if (queryMap.containsKey("filter")) {
          query.setFilter(dev.flexmodel.shared.utils.JsonUtils.getInstance().stringify(queryMap.get("filter")));
        }
        return session.data().find(modelName, query);
      }
    }

  }

}
