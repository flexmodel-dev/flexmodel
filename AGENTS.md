# Flexmodel Server Agent 指南

本文档旨在为协助开发的 AI Agent 提供核心上下文、代码规范和开发指南。请在进行任何代码修改或生成之前仔细阅读。

## 1. 项目概况

- **项目名称**: Flexmodel Server
- **核心定位**: 基于 Quarkus 3 的 Java 21 微服务项目，提供统一数据访问层和 API 设计平台。
- **架构模式**: 领域驱动设计 (DDD) 分层架构。

## 2. 技术栈

| 类别 | 技术选型 | 备注 |
| :--- | :--- | :--- |
| **语言** | Java 21 | |
| **框架** | Quarkus 3 | |
| **构建工具** | Maven | |
| **数据库** | SQLite (默认) | 设计上支持可扩展 |
| **依赖注入** | CDI | Contexts and Dependency Injection |
| **API 文档** | OpenAPI / Swagger | |
| **测试** | JUnit 5 + REST Assured | |
| **工具库** | Lombok, GraphQL Java | |

## 3. 架构与目录结构

遵循 DDD 分层架构，包路径 `dev.flexmodel` 下包含：

- `interfaces/`: **接口层**。处理 REST API、协议适配。
- `application/`: **应用层**。负责用例编排、DTO 转换、事务控制。
- `domain/`: **领域层**。包含核心业务模型、领域服务。
- `infrastructure/`: **基础设施层**。实现持久化、外部接口适配。
- `shared/`: **共享层**。通用工具、基础类。

## 4. 核心代码规范

### 4.1 命名规范
- **类名**: `PascalCase` (如 `ChatResource`, `BusinessException`)
- **方法名**: `camelCase` (如 `sendMessage`, `getSettings`)
- **常量**: `UPPER_SNAKE_CASE` (如 `ROOT_PATH`)
- **包名**: 全小写 (如 `dev.flexmodel.interfaces.rest`)

### 4.2 注解使用
- **日志**: `@Slf4j`
- **依赖注入**: `@ApplicationScoped`, `@Inject`
- **REST API**: `@Path`, `@GET`, `@POST`, `@Produces`, `@Consumes`
- **配置**: `@ConfigMapping`, `@WithName`, `@WithDefault`
- **测试**: `@QuarkusTest`, `@Test`

### 4.3 格式化
- **缩进**: 使用 **2 个空格**。
- **空行**: 类和方法之间空一行。
- **导入**: 导入语句按字母顺序排列。
- **简化**: 充分利用 Lombok 简化代码。

### 4.4 异常处理
- 必须继承 `BusinessException` 创建业务异常。
- 异常类设计为抽象类。
- 提供带消息和原因的构造函数。

## 5. 开发指南

### 5.1 创建 REST 资源
- 基础路径通常为 `/api`。
- 使用 HTTP 标准方法 (GET, POST, PUT, PATCH, DELETE)。
- 必须支持 JSON (`MediaType.APPLICATION_JSON`)。
- 适用时支持 SSE (Server-Sent Events) 流式输出。

### 5.2 编写测试
- 使用 `@QuarkusTest` 进行集成测试。
- 使用 `@QuarkusTestResource` 配置测试资源。
- 使用 REST Assured 测试 API 端点。
- 测试方法名应具有描述性。
- 测试数据和场景需包含中文注释说明。

### 5.3 依赖管理
- 使用 Maven 进行管理。
- 版本号统一在 `pom.xml` 中管理，优先使用 Quarkus BOM。
- 第三方依赖需明确指定版本。

### 5.4 文档规范
- 类和方法添加 JavaDoc 注释。
- 使用 `@author` 标记作者。
- 配置说明需详细完整。

## 6. Agent 行为准则

- **代码生成**: 始终遵循上述 DDD 结构和命名规范。
- **语言**: 代码注释、文档和提交信息推荐使用中文。
- **验证**: 生成代码后，应优先考虑通过编写测试用例来验证功能。
- **一致性**: 保持与现有代码风格一致。
