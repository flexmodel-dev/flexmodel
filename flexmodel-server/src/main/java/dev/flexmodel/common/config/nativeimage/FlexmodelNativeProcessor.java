package dev.flexmodel.common.config.nativeimage;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

/**
 * Flexmodel 原生镜像反射配置处理器。
 * <p>
 * 替代传统的 reachability-metadata.json，通过 Quarkus BuildStep
 * 在构建时自动注册所需类的反射信息，无需维护庞大的 JSON 文件。
 * <p>
 * 分类策略：
 * <ul>
 *   <li>Codegen 实体和枚举 — 运行时数据映射需要全部方法和字段</li>
 *   <li>DTO 和模型对象 — Jackson 序列化需要全部方法和字段</li>
 *   <li>Jackson MixIn 类 — 多态反序列化需要构造函数</li>
 *   <li>第三方库 — 最小化反射配置</li>
 * </ul>
 *
 * @author cjbi
 */
public class FlexmodelNativeProcessor {

  /**
   * 注册 Codegen 生成的实体和枚举。
   * 这些类在运行时被 Flexmodel 数据引擎通过反射进行属性映射。
   */
  @BuildStep
  ReflectiveClassBuildItem registerCodegenEntities() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.codegen.entity.**",
        "dev.flexmodel.codegen.enumeration.**",
        "dev.flexmodel.test.codegen.entity.**",
        "dev.flexmodel.test.codegen.enumeration.**"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册 BuildItem SPI 实现类。
   * 这些类通过 ServiceLoader 加载，在原生镜像中需要显式注册
   * 以支持反射实例化（含无参构造函数）和 Java 序列化（ObjectUtils.deserialize）。
   * <p>
   * <b>注意</b>：必须显式调用 {@code .constructors()}，因为 {@code .methods()} 仅注册
   * 已声明方法（通过 {@link Class#getDeclaredMethods()}），不包含构造函数。
   * ServiceLoader 在原生镜像中需要无参构造函数的反射访问权限才能实例化提供者类。
   */
  @BuildStep
  ReflectiveClassBuildItem registerBuildItemImplementations() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.codegen.System",
        "dev.flexmodel.test.codegen.DevTest",
        "dev.flexmodel.codegen.ObjectUtils"
      )
      .constructors()
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册 BuildItem SPI 服务，使得 ServiceLoader.load(BuildItem.class)
   * 在原生镜像中能正确发现并实例化所有实现类。
   * <p>
   * ServiceProviderBuildItem 同时处理两件事：
   * <ul>
   *   <li>将 META-INF/services/dev.flexmodel.BuildItem 描述符嵌入原生镜像</li>
   *   <li>将提供者类注册为反射可实例化</li>
   * </ul>
   * <p>
   * <b>注意</b>：使用构造函数 {@code new ServiceProviderBuildItem(serviceInterface, providerClass...)}
   * 显式指定提供者类名，而非依赖 {@code allProvidersFromClassPath} 的类路径扫描。
   * 因为 SPI 描述符文件由 codegen 动态生成到 {@code target/classes/} 目录下，
   * 在 Quarkus 构建阶段的类加载器中可能无法被扫描到。
   */
  @BuildStep
  ServiceProviderBuildItem registerBuildItemServiceProvider() {
    return new ServiceProviderBuildItem("dev.flexmodel.BuildItem",
      "dev.flexmodel.codegen.System",
      "dev.flexmodel.test.codegen.DevTest");
  }

  /**
   * 注册所有 DTO 类。
   * DTO 用于 REST 端点，Jackson 序列化/反序列化需要反射访问。
   */
  @BuildStep
  ReflectiveClassBuildItem registerDtos() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.auth.dto.**",
        "dev.flexmodel.project.dto.**",
        "dev.flexmodel.scheduling.dto.**",
        "dev.flexmodel.functions.dto.**",
        "dev.flexmodel.api.dto.**",
        "dev.flexmodel.flow.dto.**",
        "dev.flexmodel.common.dto.**",
        "dev.flexmodel.metrics.dto.**",
        "dev.flexmodel.storage.dto.**"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册引擎模块中的模型定义类。
   * 这些类使用 Jackson 多态反序列化（@JsonSubTypes / @JsonTypeInfo），
   * 并通过 ObjectUtils.deserialize() 在运行时从 JSON 反序列化。
   * <p>
   * <b>注意</b>：必须包含 {@code .constructors()}，因为 Jackson 在原生镜像中
   * 需要通过反射调用无参构造函数来实例化模型对象。
   */
  @BuildStep
  ReflectiveClassBuildItem registerModelDefinitions() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.model.**",
        "dev.flexmodel.model.field.**",
        "dev.flexmodel.condition.**",
        "dev.flexmodel.event.**",
        "dev.flexmodel.event.impl.**",
        "dev.flexmodel.ModelImportBundle",
        "dev.flexmodel.ModelImportBundle$ImportData"
      )
      .constructors()
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册 Jackson MixIn 和序列化支持类。
   * 包含 TypedFieldMixIn、ModelMixIn、IndexMixIn 等类型映射配置。
   */
  @BuildStep
  ReflectiveClassBuildItem registerJacksonSupport() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.supports.jackson.**",
        "dev.flexmodel.common.config.web.json.jackson.**",
        "dev.flexmodel.common.config.web.json.jackson.mixin.**"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册流程引擎核心类。
   * 包含执行器、验证器、服务类等运行时通过 CDI/反射实例化的组件。
   */
  @BuildStep
  ReflectiveClassBuildItem registerFlowEngineClasses() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.flow.executor.**",
        "dev.flexmodel.flow.validator.**",
        "dev.flexmodel.flow.service.**",
        "dev.flexmodel.flow.common.**",
        "dev.flexmodel.flow.config.**",
        "dev.flexmodel.flow.plugin.**",
        "dev.flexmodel.flow.processor.**"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册业务组件类。
   * 包含调度配置、认证提供者等含 @JsonSubTypes 多态类型的类。
   */
  @BuildStep
  ReflectiveClassBuildItem registerBusinessComponents() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.scheduling.config.**",
        "dev.flexmodel.scheduling.job.**",
        "dev.flexmodel.projectauth.provider.**",
        "dev.flexmodel.storage.config.**",
        "dev.flexmodel.sql.dialect.**"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册数据层和服务层类。
   * Repository、Service、Resource 等运行时组件。
   */
  @BuildStep
  ReflectiveClassBuildItem registerDataAndServiceClasses() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.auth.repository.**",
        "dev.flexmodel.auth.service.**",
        "dev.flexmodel.flow.repository.**",
        "dev.flexmodel.data.**",
        "dev.flexmodel.scheduling.**",
        "dev.flexmodel.functions.**",
        "dev.flexmodel.settings.**",
        "dev.flexmodel.storage.**",
        "dev.flexmodel.modeling.**",
        "dev.flexmodel.api.**",
        "dev.flexmodel.project.**",
        "dev.flexmodel.projectauth.**",
        "dev.flexmodel.mcp.**",
        "dev.flexmodel.metrics.**",
        "dev.flexmodel.realtime.**",
        "dev.flexmodel.audit.**"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册第三方库中不含 Quarkus 扩展的类。
   * 这些类在原生镜像中需要显式声明反射访问。
   */
  @BuildStep
  ReflectiveClassBuildItem registerThirdPartyClasses() {
    return ReflectiveClassBuildItem.builder(
        // Auth0 JWT — 无 Quarkus 扩展
        "com.auth0.jwt.exceptions.JWTVerificationException",
        // MySQL JDBC 反射由 quarkus-jdbc-mysql 扩展自动处理
        // SQLite JDBC — 原生驱动需要手动注册 DataSource 反射
        "org.sqlite.SQLiteDataSource",
        // Agroal 连接池 — 原生镜像需要反射访问 DataSource 接口实现
        "io.agroal.pool.DataSource",
        // Caffeine 缓存 — 内部类需反射
        "com.github.benmanes.caffeine.cache.UnboundedLocalCache",
        // GraphQL — 执行结果类
        "graphql.ExecutionResult",
        "graphql.ExecutionResultImpl",
        // Import map
        "io.mvnpm.importmap.model.Imports"
      )
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册核心基础设施类。
   * 包含缓存、通用工具、配置、过滤器、Session、SQL、MongoDB、查询、解析器等。
   */
  @BuildStep
  ReflectiveClassBuildItem registerCoreInfrastructure() {
    return ReflectiveClassBuildItem.builder(
        "dev.flexmodel.cache.**",
        "dev.flexmodel.common.**",
        "dev.flexmodel.mongodb.**",
        "dev.flexmodel.parser.**",
        "dev.flexmodel.query.**",
        "dev.flexmodel.session.**",
        "dev.flexmodel.sql.**",
        "dev.flexmodel.ModelImportBundle",
        "dev.flexmodel.ModelImportBundle$ImportData",
        "dev.flexmodel.ModelRegistry",
        "dev.flexmodel.SchemaProvider"
      )
      .constructors()
      .methods()
      .fields()
      .build();
  }

  /**
   * 注册无 Quarkus 扩展覆盖的第三方库类。
   * 包括 Gson、Guice、JZlib、MongoDB Driver、GraphQL 内部类等。
   * 已有 Quarkus 扩展处理的库（MySQL JDBC、Agroal、crypto）由扩展自行注册。
   */
  @BuildStep
  ReflectiveClassBuildItem registerAdditionalThirdParty() {
    return ReflectiveClassBuildItem.builder(
        // Gson
        "com.google.gson.Gson",
        // Guice
        "com.google.inject.AbstractModule",
        "com.google.inject.Module",
        // JZlib 压缩
        "com.jcraft.jzlib.JZlib",
        // MongoDB Driver
        "com.mongodb.client.MongoDatabase",
        // GraphQL Java 内部类（不可变集合）
        "graphql.com.google.common.collect.ImmutableCollection",
        "graphql.com.google.common.collect.ImmutableList",
        "graphql.com.google.common.collect.RegularImmutableList"
      )
      .methods()
      .fields()
      .build();
  }

}
