package dev.flexmodel.domain.model.api.execution;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.ApiDefinition;
import dev.flexmodel.domain.model.api.ApiDefinitionMeta;
import dev.flexmodel.domain.model.flow.shared.util.RequestScriptContext;
import dev.flexmodel.domain.model.flow.shared.util.JavaScriptUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class ScriptExecutionStrategy extends AbstractExecutionStrategy {

  @Override
  protected Map<String, Object> doExecute(ApiDefinition apiDefinition, ApiDefinitionMeta.Execution execution, Map<String, String> pathParameters, RequestScriptContext httpScriptContext) {
    httpScriptContext.setResponse(new RequestScriptContext.Response(200, "OK", new HashMap<>(), new HashMap<>()));
    try {
      Map<String, Object> contextMap = httpScriptContext.buildContextMap();
      JavaScriptUtil.execute(execution.getExecutionScript(), Map.of(RequestScriptContext.SCRIPT_CONTEXT_KEY, contextMap));
      httpScriptContext.syncFromMap(contextMap);
      return httpScriptContext.getResponse().body();
    } catch (Exception e) {
      log.error("Execute script error: {}", e.getMessage(), e);
      throw new IllegalArgumentException("Execute script error: " + e.getMessage());
    } finally {
      JavaScriptUtil.cleanup();
    }
  }

  @Override
  public String getExecutionType() {
    return "script";
  }
}
