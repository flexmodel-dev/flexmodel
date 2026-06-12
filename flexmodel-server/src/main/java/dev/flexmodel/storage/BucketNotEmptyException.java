package dev.flexmodel.storage;

/**
 * Bucket 非空异常
 * <p>
 * 当尝试删除非空 Bucket 且 force=false 时抛出。
 *
 * @author cjbi
 */
public class BucketNotEmptyException extends RuntimeException {

  public BucketNotEmptyException(String bucketName) {
    super("Bucket '" + bucketName + "' is not empty. Use force=true to delete all contents.");
  }
}
