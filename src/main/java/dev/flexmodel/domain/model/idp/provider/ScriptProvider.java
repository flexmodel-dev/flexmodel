package dev.flexmodel.domain.model.idp.provider;

import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.domain.model.flow.shared.util.JavaScriptUtil;
import dev.flexmodel.domain.model.flow.shared.util.RequestScriptContext;

import java.util.Map;

/**
 * @author cjbi
 */
@Getter
@Setter
@Slf4j
public class ScriptProvider implements Provider {

  private String script;

  @Override
  public String getType() {
    return "script";
  }

  @Override
  public ValidateResult validate(String projectId, ValidateParam param) {
    try {
      RequestScriptContext scriptContext = new RequestScriptContext(projectId);
      scriptContext.setRequest(new RequestScriptContext.Request(param.getMethod(), param.getUrl(), param.getHeaders(), null, param.getQuery()));
      scriptContext.setResponse(new RequestScriptContext.Response(200, "success", null, null));
      SessionFactory sessionFactory = CDI.current().select(SessionFactory.class).get();
      scriptContext.setSessionFactory(sessionFactory);
      Map<String, Object> contextMap = scriptContext.buildContextMap();
      JavaScriptUtil.execute(script, Map.of(RequestScriptContext.SCRIPT_CONTEXT_KEY, contextMap));
      scriptContext.syncFromMap(contextMap);
      String message = (String) scriptContext.getResponse().body().get("message");
      boolean succcess = (boolean) scriptContext.getResponse().body().get("success");
      return new ValidateResult(succcess, message);
    } catch (Exception e) {
      return new ValidateResult(false, e.getMessage());
    } finally {
      JavaScriptUtil.cleanup();
    }
  }
}
