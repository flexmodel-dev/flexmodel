package dev.flexmodel.model.field;

import java.util.Objects;

/**
 * @author cjbi
 */
public class ModelRefField extends TypedField<Long, ModelRefField> {

  /**
   * 多选
   */
  private boolean multiple;
  /**
   * 来源模型
   */
  private String from;
  /**
   * 本地字段
   */
  private String localField;
  /**
   * 外键字段
   */
  private String foreignField;
  /**
   * 级联删除，此功能依赖外键约束
   */
  private boolean cascadeDelete;

  public ModelRefField(String name) {
    super(name, ScalarType.MODEL_REF.getType());
  }

  public boolean isCascadeDelete() {
    return cascadeDelete;
  }

  public ModelRefField setCascadeDelete(boolean cascadeDelete) {
    this.cascadeDelete = cascadeDelete;
    return this;
  }

  public String getFrom() {
    return from;
  }

  public ModelRefField setFrom(String from) {
    this.from = from;
    return this;
  }

  public String getLocalField() {
    return localField;
  }

  public ModelRefField setLocalField(String localField) {
    this.localField = localField;
    return this;
  }

  public String getForeignField() {
    return foreignField;
  }

  public ModelRefField setForeignField(String foreignField) {
    this.foreignField = foreignField;
    return this;
  }

  public boolean isMultiple() {
    return multiple;
  }

  public ModelRefField setMultiple(boolean multiple) {
    this.multiple = multiple;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ModelRefField that)) return false;
    if (!super.equals(o)) return false;
    return isMultiple() == that.isMultiple() &&
           isCascadeDelete() == that.isCascadeDelete() &&
           Objects.equals(getFrom(), that.getFrom()) &&
           Objects.equals(getForeignField(), that.getForeignField());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), isMultiple(), getFrom(), getForeignField(), isCascadeDelete());
  }

  @Override
  public String getConcreteType() {
    return from + (multiple ? "[]" : "");
  }
}
