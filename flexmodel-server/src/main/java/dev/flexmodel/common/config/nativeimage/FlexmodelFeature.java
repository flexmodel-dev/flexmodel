package dev.flexmodel.common.config.nativeimage;

import com.oracle.svm.core.annotate.AutomaticFeature;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * GraalVM Feature：自动注册 Quartz Scheduler RMI bind 的 Substitution。
 * <p>
 * 通过 {@link AutomaticFeature} 机制，GraalVM 会自动扫描
 * 同包下的 {@link QuartzSchedulerBindSubstitution} 并在构建期替换
 * {@code org.quartz.core.QuartzScheduler.bind()} 为空操作，
 * 切断 RMI 调用链的静态可达性，避免 ObjID/SecureRandom 进入 image heap。
 *
 * @author cjbi
 */
@AutomaticFeature
public class FlexmodelFeature implements Feature {
}
