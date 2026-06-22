package dev.flexmodel.session;

import dev.flexmodel.query.DSL;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.SchemaService;
import dev.flexmodel.ModelImportBundle;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.parser.impl.ParseException;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author cjbi
 */
public interface Session extends Closeable {

  /**
   * 开启事务
   */
  void startTransaction();

  /**
   * 提交事务
   */
  void commit();

  /**
   * 回滚事务
   */
  void rollback();

  /**
   * 关闭连接
   */
  void close();

  boolean isClosed();

  SessionFactory getFactory();

  String getName();

  /**
   * 获取数据操作对象
   *
   * @return 数据操作对象
   */
  DataService data();

  /**
   * 获取模型操作对象
   *
   * @return 模型操作对象
   */
  SchemaService schema();

  DSL dsl();

  /**
   * 应用 FML 字符串，创建/更新模型、枚举，并写入种子数据
   * 一条 FML 可包含多个 model、enum、seed 混合声明
   *
   * @param fmlString FML 字符串
   */
  default boolean applyFML(String fmlString) {
    if (fmlString == null || fmlString.trim().isEmpty()) {
      throw new RuntimeException("FML cannot be empty");
    }
    try {
      ASTNodeConverter.FMLParseResult result = ASTNodeConverter.parseFML(fmlString);
      Map<String, SchemaObject> existing = schema().listModels().stream()
        .collect(Collectors.toMap(SchemaObject::getName, m -> m));
      for (SchemaObject obj : result.getModels()) {
        SchemaObject older = existing.get(obj.getName());
        if (older != null && Objects.equals(older.getFml(), obj.getFml())) {
          continue;
        }
        if (obj instanceof EntityDefinition newer) {
          try {
            schema().createEntity(newer.clone());
          } catch (Exception e) {
            if (older instanceof EntityDefinition olderEntity) {
              for (var field : newer.getFields()) {
                try {
                  if (olderEntity.getField(field.getName()) == null) {
                    schema().createField(field);
                  } else if (!field.equals(olderEntity.getField(field.getName()))) {
                    schema().modifyField(field);
                  }
                } catch (Exception ignored) {
                }
              }
            }
          }
        } else if (obj instanceof EnumDefinition newer) {
          try {
            schema().dropModel(newer.getName());
          } catch (Exception ignored) {
          }
          schema().createEnum(newer);
        }
      }
      for (ModelImportBundle.ImportData seed : result.getSeeds()) {
        try {
          data().insertAll(seed.getModelName(), seed.getValues());
        } catch (Exception ignored) {
        }
      }
      return true;
    } catch (ParseException e) {
      throw new RuntimeException("FML parsing failed: " + e.getMessage(), e);
    }
  }

}
