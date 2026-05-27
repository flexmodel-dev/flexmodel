package dev.flexmodel.api.execution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.api.GraphQLManager;
import dev.flexmodel.codegen.entity.ApiDefinition;
import dev.flexmodel.api.ApiDefinitionMeta;
import dev.flexmodel.flow.common.util.RequestScriptContext;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class GraphQLExecutionStrategy extends AbstractExecutionStrategy {

    @Inject
    GraphQLManager graphQLManger;

  @Override
    protected Map<String, Object> doExecute(ApiDefinition apiDefinition, ApiDefinitionMeta.Execution execution,
                                       Map<String, String> pathParameters, RequestScriptContext httpScriptContext) {
        String projectId = apiDefinition.getProjectId();
        String method = httpScriptContext.getRequest().method();
        String operationName = execution.getOperationName();
        String query = execution.getQuery();
        Map<String, Object> defaultVariables = execution.getVariables();

        Map<String, Object> executionData = new HashMap<>();
        if (defaultVariables != null) {
            executionData.putAll(defaultVariables);
        }
        if ("GET".equals(method)) {
            if (httpScriptContext.getRequest().query() != null && !httpScriptContext.getRequest().query().isEmpty()) {
                executionData.putAll(httpScriptContext.getRequest().query());
            }
        } else {
            // Request body
            if (httpScriptContext.getRequest().body() != null && !httpScriptContext.getRequest().body().isEmpty()) {
                executionData.putAll(httpScriptContext.getRequest().body());
            }
        }
        // Path parameters
        if (pathParameters != null) {
            executionData.putAll(pathParameters);
        }

        graphql.ExecutionResult result = graphQLManger.execute(projectId, operationName, query, executionData);

        Map<String, Object> resMap = new HashMap<>();
        resMap.put("data", result.getData());
        return resMap;
    }

  @Override
  public String getExecutionType() {
    return "graphql";
  }
}
