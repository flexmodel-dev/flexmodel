package dev.flexmodel.apps.workbench;

import dev.flexmodel.codegen.entity.AppWorkbench;

import java.util.List;

public interface AppWorkbenchRepository {

  List<AppWorkbench> findAll();

  List<AppWorkbench> findByAppId(String appId);

  AppWorkbench findByAppIdAndId(String appId, String workbenchId);

  AppWorkbench findByName(String appId, String name);

  AppWorkbench save(AppWorkbench workbench);

  long countByAppId(String appId);

  void deleteByAppIdAndId(String appId, String workbenchId);

  void deleteByAppId(String appId);
}
