package dev.flexmodel.domain.model.connect;

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

}
