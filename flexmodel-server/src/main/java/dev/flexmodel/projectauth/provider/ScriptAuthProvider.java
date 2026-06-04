package dev.flexmodel.projectauth.provider;

import dev.flexmodel.flow.common.util.JavaScriptUtil;
import dev.flexmodel.flow.common.util.RequestScriptContext;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 脚本认证提供商。
 * 执行 JavaScript 脚本进行自定义认证。
 * 脚本约定：通过 context.response.body 返回 {success, caller, scopes}。
 */
@Getter
@Setter
@Slf4j
public class ScriptAuthProvider implements AuthProvider {

  private String script;

  @Override
  public String getType() {
    return "script";
  }

  @Override
  @SuppressWarnings("all")
  public AuthResult authenticate(AuthContext context) {
    try {
      RequestScriptContext scriptContext = new RequestScriptContext(context.getProjectId());
      RequestScriptContext.Request req = new RequestScriptContext.Request(
        context.getMethod(), context.getUrl(), context.getHeaders(), null, context.getQuery()
      );
      scriptContext.setRequest(req);
      scriptContext.setResponse(new RequestScriptContext.Response(200, "success", null, null));
      SessionFactory sessionFactory = CDI.current().select(SessionFactory.class).get();
      scriptContext.setSessionFactory(sessionFactory);

      Map<String, Object> contextMap = scriptContext.buildContextMap();
      JavaScriptUtil.execute(script, Map.of(RequestScriptContext.SCRIPT_CONTEXT_KEY, contextMap));
      scriptContext.syncFromMap(contextMap);

      Map<String, Object> body = (Map<String, Object>) scriptContext.getResponse().body();
      if (body == null) {
        return AuthResult.fail("Script returned no body");
      }

      boolean success = Boolean.TRUE.equals(body.get("success"));
      String caller = Objects.toString(body.get("caller"), "script-user");
      Set<String> scopes = parseScopes(body.get("scopes"));

      if (success) {
        return AuthResult.ok(caller, scopes);
      } else {
        String message = Objects.toString(body.get("message"), "Script authentication failed");
        return AuthResult.fail(message);
      }
    } catch (Exception e) {
      log.error("Script auth error: {}", e.getMessage(), e);
      return AuthResult.fail("Script error: " + e.getMessage());
    } finally {
      JavaScriptUtil.cleanup();
    }
  }

  private Set<String> parseScopes(Object scopeObj) {
    if (scopeObj == null) return Set.of("read");
    if (scopeObj instanceof String s) {
      return Set.of(s.split(","));
    }
    if (scopeObj instanceof List<?> list) {
      return new LinkedHashSet<>(list.stream().map(Object::toString).toList());
    }
    return Set.of("read");
  }
}
