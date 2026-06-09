package dev.flexmodel.storage.config;

/**
 * 存储只读模式异常
 * <p>
 * 当存储后端配置为只读模式时，写操作抛出此异常。
 *
 * @author cjbi
 */
public class StorageReadOnlyException extends RuntimeException {

  public StorageReadOnlyException() {
    super("Storage is configured in read-only mode. Write operations are not allowed.");
  }

  public StorageReadOnlyException(String operation) {
    super("Storage is configured in read-only mode. Operation '" + operation + "' is not allowed.");
  }
}
