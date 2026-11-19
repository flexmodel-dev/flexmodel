package dev.flexmodel.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 枚举元素定义，支持可选的落库编码与展示注释。
 *
 * @author cjbi
 */
public class EnumElement implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private String name;

  /**
   * 落库编码，可选；未声明时以元素名作为编码。
   */
  private Object code;

  private String comment;

  protected Map<String, Object> additionalProperties = new HashMap<>();

  public EnumElement() {
  }

  public EnumElement(String name) {
    this.name = name;
  }

  public EnumElement(String name, Object code, String comment) {
    this.name = name;
    this.code = code;
    this.comment = comment;
  }

  public String getName() {
    return name;
  }

  public EnumElement setName(String name) {
    this.name = name;
    return this;
  }

  public Object getCode() {
    return code;
  }

  public EnumElement setCode(Object code) {
    this.code = code;
    return this;
  }

  public String getComment() {
    return comment;
  }

  public EnumElement setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  public EnumElement addAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
    return this;
  }
}
