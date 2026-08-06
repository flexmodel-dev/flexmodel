package dev.flexmodel.modeling;

import dev.flexmodel.api.dto.GraphQLRefreshEvent;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.project.ProjectService;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.model.*;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.parser.impl.ParseException;

import java.util.Map;
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
  SessionContext sessionContext;

  @Inject
  EventBus eventBus;

  private String resolveDatabaseName(String projectId) {
    String databaseName = sessionContext.getProjectDatabaseName();
    return databaseName != null ? databaseName : projectService.resolveDatabaseName(projectId);
  }

  public List<SchemaObject> findModels(String projectId) {
    boolean showSystemModels = isShowSystemModels(projectId);
    return modelService.findAll(projectId, resolveDatabaseName(projectId))
      .stream()
      .filter(s -> showSystemModels || !ModelService.isSystemModel(s))
      .toList();
  }

  /**
   * 读取项目元数据中的 {@code showSystemModels} 标志，默认关闭（不展示系统模型）。
   */
  private boolean isShowSystemModels(String projectId) {
    Project project = projectService.findProject(projectId);
    if (project == null || project.getMetadata() == null) {
      return false;
    }
    Object metadata = project.getMetadata();
    Object value = null;
    if (metadata instanceof Map<?, ?> map) {
      value = map.get("showSystemModels");
    } else {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = dev.flexmodel.JsonUtils.convertValue(metadata, Map.class);
        if (map != null) {
          value = map.get("showSystemModels");
        }
      } catch (Exception ignored) {
      }
    }
    return Boolean.TRUE.equals(value);
  }

  public SchemaObject createModel(String projectId, SchemaObject model) {
    SchemaObject created = modelService.createModel(projectId, resolveDatabaseName(projectId), model);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return created;
  }

  public void dropModel(String projectId, String modelName) {
    modelService.dropModel(projectId, resolveDatabaseName(projectId), modelName);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  public TypedField<?, ?> createField(String projectId, TypedField<?, ?> field) {
    TypedField<?, ?> created = modelService.createField(projectId, resolveDatabaseName(projectId), field);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return created;
  }

  public TypedField<?, ?> modifyField(String projectId, TypedField<?, ?> field) {
    TypedField<?, ?> modified = modelService.modifyField(projectId, resolveDatabaseName(projectId), field);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return modified;
  }

  public void dropField(String projectId, String modelName, String fieldName) {
    modelService.dropField(projectId, resolveDatabaseName(projectId), modelName, fieldName);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  public IndexDefinition createIndex(String projectId, IndexDefinition index) {
    IndexDefinition created = modelService.createIndex(projectId, resolveDatabaseName(projectId), index);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return created;
  }

  public IndexDefinition modifyIndex(String projectId, IndexDefinition index) {
    IndexDefinition modified = modelService.modifyIndex(projectId, resolveDatabaseName(projectId), index);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return modified;
  }

  public void dropIndex(String projectId, String modelName, String indexName) {
    modelService.dropIndex(projectId, resolveDatabaseName(projectId), modelName, indexName);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
  }

  public List<SchemaObject> syncModels(String projectId, Set<String> models) {
    return modelService.syncModels(projectId, resolveDatabaseName(projectId), models);
  }

  public SchemaObject modifyModel(String projectId, String modelName, SchemaObject model) {
    String databaseName = resolveDatabaseName(projectId);
    if (model instanceof EntityDefinition) {
      throw new ValidationException("Unsupported model type");
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
    return modelService.findModel(projectId, resolveDatabaseName(projectId), modelName).orElseThrow(() -> new NotFoundException("Model not found"));
  }

  public Boolean executeFml(String projectId, String fml) throws ParseException {
    Boolean result = modelService.executeFml(projectId, resolveDatabaseName(projectId), fml);
    eventBus.publish("graphql.refresh", new GraphQLRefreshEvent());
    return result;
  }
}
