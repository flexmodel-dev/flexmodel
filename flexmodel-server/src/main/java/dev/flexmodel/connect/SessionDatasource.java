package dev.flexmodel.connect;

import dev.flexmodel.codegen.entity.Project;

import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
public interface SessionDatasource {

  List<String> getPhysicsModelNames(Project project);

  void add(Project project);

  void delete(Project project);

  NativeQueryResult executeNativeQuery(Project project, String statement, Map<String, Object> parameters);

  /**
   * 注册指定 Schema 的 SchemaProvider 到 SessionFactory。
   *
   * @param schemaName Schema 名称
   */
  void registerSchema(String schemaName);

  /**
   * 取消注册指定 Schema 的 SchemaProvider。
   *
   * @param schemaName Schema 名称
   */
  void unregisterSchema(String schemaName);

}
