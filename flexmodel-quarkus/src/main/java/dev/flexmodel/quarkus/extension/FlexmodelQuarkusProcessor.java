package dev.flexmodel.quarkus.extension;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * FlexModel Quarkus扩展的构建时处理器
 *
 * 该处理器在构建时注册必要的Bean和功能
 *
 * @author cjbi
 */
public class FlexmodelQuarkusProcessor {

  private static final String FEATURE = "flexmodel-quarkus";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep
  AdditionalBeanBuildItem registerBeans() {
    // 注册Session相关的Bean
    return AdditionalBeanBuildItem.builder()
        .addBeanClasses(
            "dev.flexmodel.quarkus.session.QuarkusSessionManager",
            "dev.flexmodel.quarkus.session.SessionInterceptor",
//            "dev.flexmodel.quarkus.session.TransactionalInterceptor",
            "dev.flexmodel.quarkus.session.SessionProvider"
        )
        .setUnremovable()
        .build();
  }
}
