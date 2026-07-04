package dev.flexmodel.common.config.nativeimage;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Method;

/**
 * GraalVM Feature：在构建期为所有实现 SFunction 的 lambda 代理类
 * 注册 writeReplace() 方法的反射访问。
 *
 * <p>背景：Expressions.field(User::getName) 使用 serializable lambda
 * (SFunction extends Serializable)，在 JVM 模式下通过 writeReplace() →
 * SerializedLambda 提取方法名。但在 native image 中，lambda 类的
 * writeReplace() 默认不注册到反射元数据中，导致 NoSuchMethodException。
 *
 * <p>该 Feature 在构建期的分析阶段扫描所有类，找到 SFunction 的实现类
 * （即 lambda 代理类），并在反射元数据中注册其 writeReplace() 方法。
 *
 * @author cjbi
 */
public class LambdaWriteReplaceFeature implements Feature {

  private static final String SFUNCTION_CLASS = "dev.flexmodel.query.Expressions$SFunction";

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    // 找到 SFunction 接口
    Class<?> sfunctionClass = access.findClassByName(SFUNCTION_CLASS);
    if (sfunctionClass == null) {
      System.err.println("[LambdaWriteReplaceFeature] WARNING: SFunction class not found");
      return;
    }
    System.out.println("[LambdaWriteReplaceFeature] Found SFunction interface: " + sfunctionClass.getName());

    // 注册子类型可达性处理器：当任何 SFunction 实现类变为可达时，
    // 自动注册其 writeReplace() 方法
    access.registerSubtypeReachabilityHandler(
      this::registerWriteReplace,
      sfunctionClass
    );
    System.out.println("[LambdaWriteReplaceFeature] Registered subtype reachability handler for SFunction");
  }

  /**
   * 当某个 SFunction 实现类（lambda 代理类）变为可达时调用。
   * 注册该类的 writeReplace() 方法到反射元数据。
   */
  private void registerWriteReplace(DuringAnalysisAccess access, Class<?> lambdaClass) {
    String className = lambdaClass.getName();
    // 只处理 lambda 代理类（类名包含 $$Lambda）
    if (!className.contains("$$Lambda")) {
      return;
    }

    try {
      // 构建期 getDeclaredMethod 可以找到 writeReplace
      Method writeReplace = lambdaClass.getDeclaredMethod("writeReplace");
      RuntimeReflection.register(lambdaClass);
      RuntimeReflection.register(writeReplace);
      System.out.println("[LambdaWriteReplaceFeature] Registered writeReplace() on: " + className);
    } catch (NoSuchMethodException e) {
      System.err.println("[LambdaWriteReplaceFeature] WARNING: No writeReplace() on " + className + ": " + e.getMessage());
    } catch (Exception e) {
      System.err.println("[LambdaWriteReplaceFeature] ERROR registering " + className + ": " + e.getMessage());
    }
  }
}
