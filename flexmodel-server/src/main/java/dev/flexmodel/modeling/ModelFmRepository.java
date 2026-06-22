package dev.flexmodel.modeling;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.model.*;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.parser.impl.ModelParser;
import dev.flexmodel.parser.impl.ParseException;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author cjbi
 */
@ApplicationScoped
public class ModelFmRepository implements ModelRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  @SuppressWarnings("all")
  public List<SchemaObject> findAll(String projectId, String datasourceName) {
    return sessionFactory.getModels(datasourceName);
  }


  @Override
  public Optional<SchemaObject> findModel(String projectId, String datasourceName, String modelName) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      return Optional.ofNullable(session.schema().getModel(modelName));
    }
  }

  @Override
  public SchemaObject createModel(String projectId, String datasourceName, SchemaObject model) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      if (model instanceof EntityDefinition entity) {
        return session.schema().createEntity(entity);
      }
      if (model instanceof NativeQueryDefinition nativeQueryModelDefinition) {
        return session.schema().createNativeQuery(nativeQueryModelDefinition);
      }
      if (model instanceof EnumDefinition anEnumDefinition) {
        return session.schema().createEnum(anEnumDefinition);
      }
    }
    throw new RuntimeException("Unsupported model type");
  }

  @Override
  public void dropModel(String projectId, String datasourceName, String modelName) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      session.schema().dropModel(modelName);
    }
  }

  @Override
  public TypedField<?, ?> createField(String projectId, String datasourceName, TypedField<?, ?> field) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      session.schema().createField(field);
      return field;
    }
  }

  @Override
  public TypedField<?, ?> modifyField(String projectId, String datasourceName, TypedField<?, ?> field) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      session.schema().modifyField(field);
      return field;
    }
  }

  @Override
  public void dropField(String projectId, String datasourceName, String modelName, String fieldName) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      session.schema().dropField(modelName, fieldName);
    }
  }

  @Override
  public IndexDefinition createIndex(String projectId, String datasourceName, IndexDefinition index) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      session.schema().createIndex(index);
      return index;
    }
  }

  @Override
  public void dropIndex(String projectId, String datasourceName, String modelName, String indexName) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      session.schema().dropIndex(modelName, indexName);
    }
  }

  @Override
  public List<SchemaObject> syncModels(String projectId, String datasourceName, Set<String> modelNames) {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      return session.schema().loadModels(modelNames);
    }
  }

  @Override
  public Boolean executeFml(String projectId, String datasourceName, String fmlString) throws ParseException {
    try (Session session = this.sessionFactory.createSession(datasourceName)) {
      return session.applyFML(fmlString);
    }
  }

  @Override
  public Integer count(String projectId) {
    return findAll(projectId, projectId).size();
  }
}
