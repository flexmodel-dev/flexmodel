package dev.flexmodel.apps;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.function.Supplier;

/**
 * 审批数据节点执行期间的历史上下文：以系统身份记录，并保存发起人等关联信息。
 * 上下文由服务端设置，不接受客户端提交。
 */
@ApplicationScoped
public class AppRecordHistoryContext {

  public record Snapshot(String approvalInstanceId, String nodeId, String initiator) {
  }

  private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

  public <T> T as(Snapshot snapshot, Supplier<T> action) {
    Snapshot previous = CURRENT.get();
    CURRENT.set(snapshot);
    try {
      return action.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  public Snapshot current() {
    return CURRENT.get();
  }
}
