package dev.flexmodel.apps;

import dev.flexmodel.codegen.entity.Workbench;

import java.util.List;

public interface WorkbenchRepository {

  List<Workbench> findAll();

  List<Workbench> findByProjectId(String projectId);

  Workbench findByProjectIdAndId(String projectId, String workbenchId);

  Workbench findByName(String projectId, String name);

  Workbench save(Workbench workbench);

  long countByProjectId(String projectId);

  void deleteByProjectIdAndId(String projectId, String workbenchId);

  void deleteByProjectId(String projectId);
}
