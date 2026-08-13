package dev.flexmodel.service;

import dev.flexmodel.model.*;
import dev.flexmodel.model.*;
import dev.flexmodel.model.field.TypedField;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Schema服务
 *
 * @author cjbi
 */
public interface SchemaService {

  /**
   * 同步模型变更
   */
  List<SchemaObject> loadModels();

  List<SchemaObject> loadModels(Set<String> modelNames);

  /**
   * 获取所有模型
   *
   * @return 模型列表
   */
  List<SchemaObject> listModels();

  /**
   * 获取模型
   *
   * @param modelName 模型名称
   * @return 实体
   */
  SchemaObject getModel(String modelName);

  /**
   * 删除模型
   *
   * @param modelName 模型名称
   */
  void dropModel(String modelName);

  /**
   * 创建实体
   *
   * @param entity
   * @return
   */
  EntityDefinition createEntity(EntityDefinition entity);

  /**
   * 迁移实体定义到数据库，保证 schema 与模型同步。
   * <p>
   * 表不存在时等价于 {@link #createEntity}；表已存在（older 不为 null）时按旧定义做字段级 diff，
   * 新增字段调用 {@link #createField}，变更字段调用 {@link #modifyField}。
   * 兼容 failsafe 模式——createTable 失败被吞掉时仍能补列，不依赖 createEntity 抛异常。
   *
   * @param newer 新的实体定义
   * @param older 已存在的旧实体定义（可为 null）
   */
  default void migrateEntity(EntityDefinition newer, SchemaObject older) {
    try {
      createEntity(newer.clone());
    } catch (Exception e) {
      // 表已存在（非 failsafe 模式会抛出），继续做字段级 diff
    }
    if (older instanceof EntityDefinition olderEntity) {
      for (TypedField<?, ?> field : newer.getFields()) {
        field.setModelName(newer.getName());
        try {
          TypedField<?, ?> oldField = olderEntity.getField(field.getName());
          if (oldField == null) {
            createField(field);
          } else if (!field.equals(oldField)) {
            modifyField(field);
          }
        } catch (Exception ignored) {
        }
      }
    }
  }

  /**
   * 创建本地查询
   *
   * @param nq
   * @return
   */
  NativeQueryDefinition createNativeQuery(NativeQueryDefinition nq);

  /**
   * 创建枚举
   *
   * @param anEnum
   * @return
   */
  EnumDefinition createEnum(EnumDefinition anEnum);

  /**
   * 创建字段
   *
   * @param field
   */
  TypedField<?, ?> createField(TypedField<?, ?> field);

  TypedField<?, ?> modifyField(TypedField<?, ?> field);

  /**
   * 删除字段
   *
   * @param modelName 模型名称
   * @param fieldName 字段名称
   */
  void dropField(String modelName, String fieldName);

  /**
   * 创建索引
   *
   * @param index
   */
  IndexDefinition createIndex(IndexDefinition index);

  /**
   * 删除索引
   *
   * @param entityName 模型名称
   * @param indexName  索引名称
   */
  void dropIndex(String entityName, String indexName);

  /**
   * 创建序列
   *
   * @param sequenceName
   * @param initialValue
   * @param incrementSize
   */
  void createSequence(String sequenceName, int initialValue, int incrementSize);

  /**
   * 删除序列
   *
   * @param sequenceName
   */
  void dropSequence(String sequenceName);

  /**
   * 获取序列下一个值
   *
   * @param sequenceName 序列名称
   * @return 序列值
   */
  long getSequenceNextVal(String sequenceName);

  /**
   * 创建实体
   *
   * @param modelName
   * @param entityUnaryOperator
   * @return
   */
  default EntityDefinition createEntity(String modelName, UnaryOperator<EntityDefinition> entityUnaryOperator) {
    EntityDefinition entity = new EntityDefinition(modelName);
    entityUnaryOperator.apply(entity);
    return createEntity(entity);
  }

  default NativeQueryDefinition createNativeQuery(String modelName, UnaryOperator<NativeQueryDefinition> modelUnaryOperator) {
    NativeQueryDefinition model = new NativeQueryDefinition(modelName);
    modelUnaryOperator.apply(model);
    return createNativeQuery(model);
  }

  default EnumDefinition createEnum(String name, UnaryOperator<EnumDefinition> enumUnaryOperator) {
    EnumDefinition anEnum = new EnumDefinition(name);
    enumUnaryOperator.apply(anEnum);
    return createEnum(anEnum);
  }

}
