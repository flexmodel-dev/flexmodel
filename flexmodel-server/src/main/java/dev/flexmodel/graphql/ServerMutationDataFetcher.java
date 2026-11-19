package dev.flexmodel.graphql;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.session.SessionFactory;
import graphql.schema.DataFetchingEnvironment;

import java.util.*;

/**
 * Keep GraphQL argument/export handling while executing writes through server-owned sessions.
 */
final class ServerMutationDataFetcher extends FlexmodelAbstractDataFetcher<Map<String, Object>> {
  private final String projectId;
  private final DataFetchers operation;
  private final ServerSessions sessions;
  private final ProjectUnitOfWork transactions;

  ServerMutationDataFetcher(String projectId, String schema, String model, SessionFactory factory,
                            DataFetchers operation, ServerSessions sessions, ProjectUnitOfWork transactions) {
    super(schema, model, factory);
    this.projectId = projectId;
    this.operation = operation;
    this.sessions = sessions;
    this.transactions = transactions;
  }

  @Override
  public Map<String, Object> get(DataFetchingEnvironment environment) {
    Map<String, Object> args = getArguments(environment);
    return transactions.execute(projectId, () -> {
      try (var session = sessions.open(schemaName)) {
        var data = session.data();
        String filter = args.get(WHERE) == null ? null : JsonUtils.toJsonString(args.get(WHERE));
        return switch (operation) {
          case MUTATION_CREATE -> {
            if (!(args.get("data") instanceof Map<?, ?>)) yield null;
            Map<String, Object> record = new LinkedHashMap<>((Map<String, Object>) args.get("data"));
            data.insert(modelName, record);
            yield record;
          }
          case MUTATION_DELETE_BY_ID -> {
            var previous = data.findById(modelName, args.get(ID)).orElse(null);
            data.deleteById(modelName, args.get(ID));
            yield previous;
          }
          case MUTATION_UPDATE_BY_ID -> {
            var previous = data.findById(modelName, args.get(ID)).orElse(null);
            if (previous == null) yield null;
            var changes = new LinkedHashMap<>((Map<String, Object>) args.get("set"));
            data.updateById(modelName, changes, args.get(ID));
            var updated = new LinkedHashMap<>(previous);
            updated.putAll(changes);
            yield updated;
          }
          case MUTATION_DELETE -> Map.of(AFFECTED_ROWS, session.dsl().deleteFrom(modelName).where(filter).execute());
          case MUTATION_UPDATE ->
            Map.of(AFFECTED_ROWS, session.dsl().update(modelName).values(new LinkedHashMap<>((Map<String, Object>) args.get("set"))).where(filter).execute());
          default -> throw new IllegalArgumentException("仅支持 GraphQL 写操作");
        };
      }
    });
  }
}
