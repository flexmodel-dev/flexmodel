package dev.flexmodel.apps.form;

import dev.flexmodel.codegen.entity.AppForm;

import java.util.List;

/**
 * 应用表单仓储，f_app_form 按 app_id 直查，每个表单一行（name 唯一）。
 *
 * @author cjbi
 */
public interface AppFormRepository {

  List<AppForm> findByAppId(String appId);

  AppForm save(AppForm form);

  void deleteByAppId(String appId);
}
