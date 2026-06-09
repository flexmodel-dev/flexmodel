package dev.flexmodel.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 存储操作接口
 * @author cjbi
 */
public interface StorageOperations {

  List<FileItem> listFiles(String path);

  FileItem getFile(String path);

  void uploadFile(String path, InputStream inputStream, long size);

  void deleteFile(String path);

  /**
   * 获取文件输入流（用于下载）
   */
  InputStream getInputStream(String path);
}
