package dev.flexmodel.apps;

import dev.flexmodel.codegen.entity.AppWorkbench;

import java.util.List;

public interface WorkbenchRepository {

  List<AppWorkbench> findAll();

  List<AppWorkbench> findByProjectId(String projectId);

  AppWorkbench findByProjectIdAndId(String projectId, String workbenchId);

  AppWorkbench findByName(String projectId, String name);

  AppWorkbench save(AppWorkbench workbench);

  long countByProjectId(String projectId);

  void deleteByProjectIdAndId(String projectId, String workbenchId);

  void deleteByProjectId(String projectId);
}
