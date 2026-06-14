package dev.flexmodel.api;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.values.InputInterceptor;
import graphql.schema.GraphQLScalarType;
import dev.flexmodel.graphql.FlexmodelGraphQL;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.project.ProjectRepository;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static graphql.ExecutionInput.newExecutionInput;

/**
 * @author cjbi
 */
@ApplicationScoped
public class GraphQLManager {

  private static final Logger log = LoggerFactory.getLogger(GraphQLManager.class);

  private final Map<String, GraphQL> tenantGraphqlMap = new ConcurrentHashMap<>();

  @Inject
  SessionFactory sf;

  @Inject
  ProjectRepository projectRepository;

  public GraphQL getGraphQL(String projectId) {
    return tenantGraphqlMap.get(projectId);
  }

  /**
   * 懒加载获取 GraphQL 实例，首次访问时按需生成 Schema。
   */
  public GraphQL getOrLoadGraphQL(String projectId) {
    return tenantGraphqlMap.computeIfAbsent(projectId, pid -> {
      Project project = projectRepository.findProject(pid);
      if (project == null) {
        throw new IllegalArgumentException("项目不存在: " + pid);
      }
      String databaseName = project.getDatabaseName();
      log.info("Lazy-loading GraphQL schema for project '{}', database='{}'", pid, databaseName);
      FlexmodelGraphQL fg = new FlexmodelGraphQL();
      return fg.generateGraphQLWithSchemaObject(sf, databaseName);
    });
  }

  public void addGraphQL(String projectId, GraphQL graphQL) {
    tenantGraphqlMap.put(projectId, graphQL);
  }

  public void removeGraphQL(String projectId) {
    tenantGraphqlMap.remove(projectId);
  }

  public void clearAll() {
    tenantGraphqlMap.clear();
    log.info("GraphQL cache cleared, schemas will be lazy-loaded on next access");
  }

  public ExecutionResult execute(String projectId, String operationName, String query, Map<String, Object> variables) {
    GraphQL graphQL = getOrLoadGraphQL(projectId);
    if (variables == null) {
      variables = new HashMap<>();
    }
    ExecutionInput executionInput = newExecutionInput()
      .operationName(operationName)
      .query(query)
      .variables(variables)
      .graphQLContext(Map.of(InputInterceptor.class, (InputInterceptor) (value, graphQLType, graphqlContext, locale) -> {
        boolean isNumeric = graphQLType instanceof GraphQLScalarType graphQLScalarType
                            && (graphQLScalarType.getName().equals("Int") ||
                                graphQLScalarType.getName().equals("Float") ||
                                graphQLScalarType.getName().equals("Long")
                            );
        if (isNumeric && value instanceof String valueString) {
          return Double.valueOf(valueString);
        }
        return value;
      }))
      .build();
    return graphQL.execute(executionInput);
  }


}
