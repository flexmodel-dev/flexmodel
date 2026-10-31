package dev.flexmodel.apps;

import dev.flexmodel.codegen.entity.App;

import java.util.List;

/**
 * 应用仓储，f_app 与 f_project 一对一（共享主键）。
 *
 * @author cjbi
 */
public interface AppRepository {

  List<App> findApps();

  App findApp(String appId);

  App save(App app);

  void delete(String appId);
}
