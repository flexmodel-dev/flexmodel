package dev.flexmodel.graphql;

import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.persistence.ServerSessions;
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
  ServerSessions sessions;
  @Inject
  ProjectUnitOfWork transactions;

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
      GraphQL generated = fg.generateGraphQLWithSchemaObject(sf, databaseName);
      if (!(sf.getSchemaProvider(databaseName) instanceof dev.flexmodel.sql.JdbcSchemaProvider)) return generated;
      var schema = generated.getGraphQLSchema();
      var registry = graphql.schema.GraphQLCodeRegistry.newCodeRegistry(schema.getCodeRegistry());
      for (var model : sf.getModels(databaseName)) {
        if (!(model instanceof dev.flexmodel.model.EntityDefinition entity) || entity.getAdditionalProperties().containsKey("system"))
          continue;
        String camelName = dev.flexmodel.codegen.StringUtils.uncapitalize(dev.flexmodel.codegen.StringUtils.capitalize(dev.flexmodel.codegen.StringUtils.snakeToCamel(model.getName())));
        for (var operation : DataFetchers.values())
          if (operation.isMutation()) {
            String fieldName = operation.getKeyFunc().apply(databaseName, camelName);
            registry.dataFetcher(graphql.schema.FieldCoordinates.coordinates("Mutation", fieldName),
              new ServerMutationDataFetcher(pid, databaseName, model.getName(), sf, operation, sessions, transactions));
          }
      }
      return generated.transform(builder -> builder.schema(schema.transform(s -> s.codeRegistry(registry.build()))));
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
