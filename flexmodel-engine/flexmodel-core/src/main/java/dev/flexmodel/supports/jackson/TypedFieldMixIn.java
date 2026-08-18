package dev.flexmodel.supports.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.flexmodel.model.field.*;

/**
 * @author cjbi
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StringField.class, name = ScalarType.STRING_TYPE),
  @JsonSubTypes.Type(value = IntField.class, name = ScalarType.INT_TYPE),
  @JsonSubTypes.Type(value = LongField.class, name = ScalarType.LONG_TYPE),
  @JsonSubTypes.Type(value = FloatField.class, name = ScalarType.FLOAT_TYPE),
  @JsonSubTypes.Type(value = DateField.class, name = ScalarType.DATE_TYPE),
  @JsonSubTypes.Type(value = DateTimeField.class, name = ScalarType.DATETIME_TYPE),
  @JsonSubTypes.Type(value = TimeField.class, name = ScalarType.TIME_TYPE),
  @JsonSubTypes.Type(value = BooleanField.class, name = ScalarType.BOOLEAN_TYPE),
  @JsonSubTypes.Type(value = JSONField.class, name = ScalarType.JSON_TYPE),
  @JsonSubTypes.Type(value = ModelRefField.class, name = ScalarType.MODEL_REF_TYPE),
  @JsonSubTypes.Type(value = EnumRefField.class, name = ScalarType.ENUM_REF_TYPE),
})
public abstract class TypedFieldMixIn {

  @JsonCreator
  public TypedFieldMixIn(@JsonProperty("name") String name) {
  }
}
