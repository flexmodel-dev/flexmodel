package dev.flexmodel.api.execution;

import dev.flexmodel.codegen.entity.ApiDefinition;
import dev.flexmodel.api.ApiDefinitionMeta;
import dev.flexmodel.flow.common.util.RequestScriptContext;

import java.util.Map;

public interface ExecutionStrategy {
  /**
   * Execute the specific execution type
   *
   * @param execution The execution configuration
   * @param httpScriptContext The HTTP script context containing all necessary data
   */
  void execute(ApiDefinition apiDefinition, ApiDefinitionMeta.Execution execution,
               Map<String, String> pathParameters, RequestScriptContext httpScriptContext);

  /**
   * Get the execution type
   * @return The execution type
   */
  String getExecutionType();

}
