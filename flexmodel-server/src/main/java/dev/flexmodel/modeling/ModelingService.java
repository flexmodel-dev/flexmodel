package dev.flexmodel.modeling;

import dev.flexmodel.api.dto.GraphQLRefreshEvent;
import dev.flexmodel.project.ProjectService;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.model.*;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.parser.impl.ParseException;

import java.util.List;
import java.util.Set;

/**
 * @author cjbi
 */
@ApplicationScoped
public class ModelingService {

  @Inject
  ModelService modelService;

  @Inject
  ProjectService projectService;

  @Inject
  EventBus eventBus;

  public List<SchemaObject> findModels(String projectId) {
    return modelService.findAll(projectId, projectService.resolveDatabaseName(projectId));
  }

  public SchemaObject createModel(String projectId, SchemaObject model) {
    SchemaObject created = modelService.createModel(projectId, projectService.resolveDatabaseName(projectId), model);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return created;
  }

  public void dropModel(String projectId, String modelName) {
    modelService.dropModel(projectId, projectService.resolveDatabaseName(projectId), modelName);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  public TypedField<?, ?> createField(String projectId, TypedField<?, ?> field) {
    TypedField<?, ?> created = modelService.createField(projectId, projectService.resolveDatabaseName(projectId), field);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return created;
  }

  public TypedField<?, ?> modifyField(String projectId, TypedField<?, ?> field) {
    TypedField<?, ?> modified = modelService.modifyField(projectId, projectService.resolveDatabaseName(projectId), field);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return modified;
  }

  public void dropField(String projectId, String modelName, String fieldName) {
    modelService.dropField(projectId, projectService.resolveDatabaseName(projectId), modelName, fieldName);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  public IndexDefinition createIndex(String projectId, IndexDefinition index) {
    IndexDefinition created = modelService.createIndex(projectId, projectService.resolveDatabaseName(projectId), index);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return created;
  }

  public IndexDefinition modifyIndex(String projectId, IndexDefinition index) {
    IndexDefinition modified = modelService.modifyIndex(projectId, projectService.resolveDatabaseName(projectId), index);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return modified;
  }

  public void dropIndex(String projectId, String modelName, String indexName) {
    modelService.dropIndex(projectId, projectService.resolveDatabaseName(projectId), modelName, indexName);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  public List<SchemaObject> syncModels(String projectId, Set<String> models) {
    return modelService.syncModels(projectId, projectService.resolveDatabaseName(projectId), models);
  }

  public SchemaObject modifyModel(String projectId, String modelName, SchemaObject model) {
    String databaseName = projectService.resolveDatabaseName(projectId);
    if (model instanceof EntityDefinition) {
      throw new RuntimeException("Unsupported model type");
    }
    if (model instanceof NativeQueryDefinition nativeQueryModel) {
      nativeQueryModel.setName(modelName);
    }
    if (model instanceof EnumDefinition anEnum) {
      anEnum.setName(modelName);
    }
    modelService.dropModel(projectId, databaseName, modelName);
    modelService.createModel(projectId, databaseName, model);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return model;
  }

  public SchemaObject findModel(String projectId, String modelName) {
    return modelService.findModel(projectId, projectService.resolveDatabaseName(projectId), modelName).orElseThrow(() -> new RuntimeException("Model not found"));
  }

  public Boolean executeFml(String projectId, String fml) throws ParseException {
    Boolean result = modelService.executeFml(projectId, projectService.resolveDatabaseName(projectId), fml);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return result;
  }
}
