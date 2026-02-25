package dev.flexmodel.mongodb;

import com.mongodb.client.MongoDatabase;
import dev.flexmodel.DataSourceProvider;

/**
 * @author cjbi
 * @deprecated Use {@link MongoSchemaProvider} instead
 */
@Deprecated
public record MongoDataSourceProvider(String id, MongoDatabase mongoDatabase) implements DataSourceProvider {
  @Override
  public String getId() {
    return id;
  }

  public MongoSchemaProvider toSchemaProvider() {
    return new MongoSchemaProvider(id, mongoDatabase);
  }
}
