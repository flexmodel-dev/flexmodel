package dev.flexmodel.graphql;

import graphql.schema.DataFetcher;
import dev.flexmodel.codegen.StringUtils;
import dev.flexmodel.session.SessionFactory;

import java.util.function.BiFunction;

/**
 * @author cjbi
 */
public enum DataFetchers {
  FIND((schema, model) -> model, FlexmodelListDataFetcher::new, true),
  BY_ID((schema, model) -> model + "ById", FlexmodelByIdDataFetcher::new, true),
  AGGREGATE((schema, model) -> model + "Aggregate", FlexmodelAggregateDataFetcher::new, true),
  MUTATION_DELETE((schema, model) -> "delete" + StringUtils.capitalize(model), FlexmodelMutationDeleteDataFetcher::new, false),
  MUTATION_DELETE_BY_ID((schema, model) -> "delete" + StringUtils.capitalize(model) + "ById", FlexmodelMutationDeleteByIdDataFetcher::new, false),
  MUTATION_CREATE((schema, model) -> "create" + StringUtils.capitalize(model), FlexmodelMutationCreateDataFetcher::new, false),
  MUTATION_UPDATE((schema, model) -> "update" + StringUtils.capitalize(model), FlexmodelMutationUpdateDataFetcher::new, false),
  MUTATION_UPDATE_BY_ID((schema, model) -> "update" + StringUtils.capitalize(model) + "ById", FlexmodelMutationUpdateByIdDataFetcher::new, false);

  private final BiFunction<String, String, String> keyFunc;
  private final DataFetcherFunc dataFetcherFunc;
  private final boolean query;

  DataFetchers(BiFunction<String, String, String> keyFunc, DataFetcherFunc dataFetcherFunc, boolean query) {
    this.keyFunc = keyFunc;
    this.dataFetcherFunc = dataFetcherFunc;
    this.query = query;
  }

  public BiFunction<String, String, String> getKeyFunc() {
    return keyFunc;
  }

  public DataFetcherFunc getDataFetcherFunc() {
    return dataFetcherFunc;
  }

  public boolean isQuery() {
    return query;
  }

  public boolean isMutation() {
    return !query;
  }

  @FunctionalInterface
  public interface DataFetcherFunc {
    DataFetcher<?> apply(String t, String u, SessionFactory sf);
  }
}
