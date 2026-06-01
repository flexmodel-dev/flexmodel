package dev.flexmodel.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.RelationField;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.query.Expressions.field;

import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Query;

/**
 * @author cjbi
 */
public class FlexmodelByIdDataFetcher extends FlexmodelAbstractDataFetcher<Map<String, Object>> {

  public FlexmodelByIdDataFetcher(String schemaName, String modelName, SessionFactory sessionFactory) {
    super(schemaName, modelName, sessionFactory);
  }

  @Override
  public Map<String, Object> get(DataFetchingEnvironment env) {
    Object id = getArgument(env, ID);
    List<SelectedField> selectedFields = env.getSelectionSet().getImmediateFields();
    try (Session session = sessionFactory.createSession(schemaName)) {
      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      TypedField<?, ?> idField = entity.findIdField().orElseThrow();

      List<RelationField> relationFields = new ArrayList<>();

      List<Map<String, Object>> list = session.dsl()
        .select(projection -> {
          projection.field(idField.getName(), Query.field(idField.getName()));
          for (SelectedField selectedField : selectedFields) {
            TypedField<?, ?> flexModelField = entity.getField(selectedField.getName());
            if (flexModelField == null) {
              continue;
            }
            if (flexModelField instanceof RelationField secondaryRelationField) {
              relationFields.add(secondaryRelationField);
              continue;
            }
            projection.field(selectedField.getName(), Query.field(flexModelField.getName()));
          }
          return projection;
        })
        .from(entity.getName())
        .where(Expressions.field(idField.getName()).eq(id))
        .page(1, 1)
        .execute();

      if (list.isEmpty()) {
        return null;
      }
      Map<String, Object> resultData = new HashMap<>(list.stream().findFirst().orElseThrow());
      for (RelationField secondaryRelationField : relationFields) {
        Object secondaryId = resultData.get(idField.getName());
        List<Map<String, Object>> relationDataList = findRelationDataList(session, env, null, secondaryRelationField.getFrom(), secondaryRelationField, secondaryId);
        resultData.put(secondaryRelationField.getName(), secondaryRelationField.isMultiple() ? relationDataList : relationDataList.stream().findFirst().orElse(null));
      }
      return resultData;
    }
  }

}
