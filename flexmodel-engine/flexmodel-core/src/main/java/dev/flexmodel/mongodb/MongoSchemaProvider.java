package dev.flexmodel.mongodb;

import com.mongodb.client.MongoDatabase;
import dev.flexmodel.SchemaProvider;

/**
 * MongoDB Schema Provider
 * Provides MongoDB-based schema implementation
 *
 * @author cjbi
 */
public record MongoSchemaProvider(String id, MongoDatabase mongoDatabase) implements SchemaProvider {
  @Override
  public String getName() {
    return id;
  }
}
