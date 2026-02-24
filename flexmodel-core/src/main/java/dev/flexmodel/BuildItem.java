package dev.flexmodel;

import dev.flexmodel.model.SchemaObject;

import java.util.List;

/**
 * @author cjbi
 */
public interface BuildItem {

  String getSchemaName();

  /**
   * 获取模型
   *
   * @return
   */
  List<SchemaObject> getSchema();

  List<ModelImportBundle.ImportData> getData();
}
