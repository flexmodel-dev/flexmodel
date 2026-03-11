package dev.flexmodel.application;

import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.domain.model.auth.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.domain.model.modeling.ModelService;
import dev.flexmodel.model.*;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.parser.impl.ParseException;

import java.util.List;
import java.util.Set;

/**
 * @author cjbi
 */
@ApplicationScoped
public class ModelingApplicationService {

  @Inject
  ModelService modelService;

  @Inject
  ProjectService projectService;

  public List<SchemaObject> findModels(String projectId) {
    Project project = projectService.findProject(projectId);
    return modelService.findAll(projectId, project.getDatabaseName());
  }

  public SchemaObject createModel(String projectId, SchemaObject model) {
    Project project = projectService.findProject(projectId);
    return modelService.createModel(projectId, project.getDatabaseName(), model);
  }

  public void dropModel(String projectId, String modelName) {
    Project project = projectService.findProject(projectId);
    modelService.dropModel(projectId, project.getDatabaseName(), modelName);
  }

  public TypedField<?, ?> createField(String projectId, TypedField<?, ?> field) {
    Project project = projectService.findProject(projectId);
    return modelService.createField(projectId, project.getDatabaseName(), field);
  }

  public TypedField<?, ?> modifyField(String projectId, TypedField<?, ?> field) {
    Project project = projectService.findProject(projectId);
    return modelService.modifyField(projectId, project.getDatabaseName(), field);
  }

  public void dropField(String projectId, String modelName, String fieldName) {
    Project project = projectService.findProject(projectId);
    modelService.dropField(projectId, project.getDatabaseName(), modelName, fieldName);
  }

  public IndexDefinition createIndex(String projectId, IndexDefinition index) {
    Project project = projectService.findProject(projectId);
    return modelService.createIndex(projectId, project.getDatabaseName(), index);
  }

  public IndexDefinition modifyIndex(String projectId, IndexDefinition index) {
    Project project = projectService.findProject(projectId);
    return modelService.modifyIndex(projectId, project.getDatabaseName(), index);
  }

  public void dropIndex(String projectId, String modelName, String indexName) {
    Project project = projectService.findProject(projectId);
    modelService.dropIndex(projectId, project.getDatabaseName(), modelName, indexName);
  }

  public List<SchemaObject> syncModels(String projectId, Set<String> models) {
    Project project = projectService.findProject(projectId);
    return modelService.syncModels(projectId, project.getDatabaseName(), models);
  }

  public void importModels(String projectId, String script, String type) {
    Project project = projectService.findProject(projectId);
    modelService.importModels(projectId, project.getDatabaseName(), script, type);
  }

  public SchemaObject modifyModel(String projectId, String modelName, SchemaObject model) {
    Project project = projectService.findProject(projectId);
    if (model instanceof EntityDefinition) {
      throw new RuntimeException("Unsupported model type");
    }
    if (model instanceof NativeQueryDefinition nativeQueryModel) {
      nativeQueryModel.setName(modelName);
    }
    if (model instanceof EnumDefinition anEnum) {
      anEnum.setName(modelName);
    }
    modelService.dropModel(projectId, project.getDatabaseName(), modelName);
    modelService.createModel(projectId, project.getDatabaseName(), model);
    return model;
  }

  public SchemaObject findModel(String projectId, String modelName) {
    Project project = projectService.findProject(projectId);
    return modelService.findModel(projectId, project.getDatabaseName(), modelName).orElseThrow(() -> new RuntimeException("Model not found"));
  }

  public List<SchemaObject> executeIdl(String projectId, String idl) throws ParseException {
    Project project = projectService.findProject(projectId);
    return modelService.executeIdl(projectId, project.getDatabaseName(), idl);
  }
}
