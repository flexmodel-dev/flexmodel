package dev.flexmodel.common.config.nativeimage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * GraalVM Substitution：阻止 Quartz Scheduler 在 native-image 构建期触发 RMI export。
 * <p>
 * {@code org.quartz.core.QuartzScheduler.bind()} 会调用
 * {@code UnicastRemoteObject.exportObject()}，其静态可达性分析会追踪到
 * {@code java.rmi.server.ObjID}（含 static SecureRandom）和
 * {@code sun.rmi.transport.DGCClient$EndpointEntry}（含 ObjID 实例字段），
 * 导致这些对象进入 image heap，与 GraalVM 的 Random/SplittableRandom 安全约束冲突。
 * <p>
 * Flexmodel 不使用 Quartz 的 RMI 远程管理功能，因此将 {@code bind()} 替换为空操作。
 * 这从根本上切断了 RMI 调用链的静态可达性，避免了 ObjID/SecureRandom 进入 image heap。
 * <p>
 * 注意：仅靠 Quartz 运行时配置（如 {@code org.quartz.scheduler.rmi.export=false}）
 * 无法解决问题，因为 native-image 的静态分析不看运行时配置，只看字节码可达性。
 * 之前尝试的 {@code --initialize-at-run-time=java.rmi.server.ObjID} 也不可行，
 * 因为 GraalVM 不允许"运行时初始化的类"的对象出现在 image heap 中（矛盾）。
 *
 * @author cjbi
 */
@TargetClass(org.quartz.core.QuartzScheduler.class)
final class QuartzSchedulerBindSubstitution {

  /**
   * 替换 {@code QuartzScheduler.bind()}：原方法会 export RMI remote object，
   * 替换后为空操作，彻底切断 RMI 调用链的静态可达性。
   */
  @Substitute
  void bind() throws Exception {
    // Flexmodel 不使用 Quartz RMI 远程管理，跳过 export
  }
}
