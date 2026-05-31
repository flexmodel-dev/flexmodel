package dev.flexmodel.modeling;

import dev.flexmodel.project.ProjectService;
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

  public List<SchemaObject> findModels(String projectId) {
    return modelService.findAll(projectId, projectService.resolveDatabaseName(projectId));
  }

  public SchemaObject createModel(String projectId, SchemaObject model) {
    return modelService.createModel(projectId, projectService.resolveDatabaseName(projectId), model);
  }

  public void dropModel(String projectId, String modelName) {
    modelService.dropModel(projectId, projectService.resolveDatabaseName(projectId), modelName);
  }

  public TypedField<?, ?> createField(String projectId, TypedField<?, ?> field) {
    return modelService.createField(projectId, projectService.resolveDatabaseName(projectId), field);
  }

  public TypedField<?, ?> modifyField(String projectId, TypedField<?, ?> field) {
    return modelService.modifyField(projectId, projectService.resolveDatabaseName(projectId), field);
  }

  public void dropField(String projectId, String modelName, String fieldName) {
    modelService.dropField(projectId, projectService.resolveDatabaseName(projectId), modelName, fieldName);
  }

  public IndexDefinition createIndex(String projectId, IndexDefinition index) {
    return modelService.createIndex(projectId, projectService.resolveDatabaseName(projectId), index);
  }

  public IndexDefinition modifyIndex(String projectId, IndexDefinition index) {
    return modelService.modifyIndex(projectId, projectService.resolveDatabaseName(projectId), index);
  }

  public void dropIndex(String projectId, String modelName, String indexName) {
    modelService.dropIndex(projectId, projectService.resolveDatabaseName(projectId), modelName, indexName);
  }

  public List<SchemaObject> syncModels(String projectId, Set<String> models) {
    return modelService.syncModels(projectId, projectService.resolveDatabaseName(projectId), models);
  }

  public void importModels(String projectId, String script, String type) {
    modelService.importModels(projectId, projectService.resolveDatabaseName(projectId), script, type);
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
    return model;
  }

  public SchemaObject findModel(String projectId, String modelName) {
    return modelService.findModel(projectId, projectService.resolveDatabaseName(projectId), modelName).orElseThrow(() -> new RuntimeException("Model not found"));
  }

  public List<SchemaObject> executeFml(String projectId, String fml) throws ParseException {
    return modelService.executeFml(projectId, projectService.resolveDatabaseName(projectId), fml);
  }
}
