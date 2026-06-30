package dev.flexmodel.model;

import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.parser.impl.ModelParser;

import java.io.Serializable;

/**
 * @author cjbi
 */
public interface SchemaObject extends Serializable {

  /**
   * 名称
   *
   * @return
   */
  String getName();

  /**
   * 类型
   *
   * @return
   */
  String getType();

  default String getFml() {
    ModelParser.ASTNode astNode = ASTNodeConverter.fromSchemaObject(this);
    if (astNode == null) {
      return null;
    }
    return astNode.toString();
  }

  /**
   * 是否为系统预置模型/枚举
   */
  default boolean isSystem() {
    return false;
  }
}
