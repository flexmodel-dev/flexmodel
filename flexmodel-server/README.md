# Flexmodel Server

> **Flexmodel Server** 是基于 Quarkus 的后端服务实现，作为 Flexmodel 项目的服务端核心，采用特性垂直分包架构，集成了数据访问、服务编排、任务调度、对象存储和身份认证等核心功能。

## 🚀 核心特性

- **一站式后端能力**: 集成了身份认证 (Auth)、对象存储 (Storage)、任务调度 (Job) 及服务编排 (Flow)。
- **统一数据接口**: 抽象底层数据源差异，提供标准化的 REST 和 GraphQL 接口。
- **AI 赋能**: 内置 LangChain4j，提供流式对话能力 (SSE)，支持构建智能应用。
- **动态逻辑**: 支持基于 JavaScript 的脚本执行，实现灵活的业务逻辑扩展。
- **特性垂直分包**: 按业务特性垂直分包，每个特性包内聚 Resource、Service、Repository、DTO，易于演进和维护。

## 🛠️ 技术栈

- **主框架**: Quarkus 3 (REST, Jackson, Hibernate Validator, Scheduler, Cache)
- **AI 能力**: LangChain4j (OpenAI 兼容接口, StreamingChatModel)
- **API 协议**: REST API, GraphQL, OpenAPI/Swagger UI
- **持久化**: SQLite (默认演示) / 支持扩展多种关系型与 NoSQL 数据库
- **其他**: Lombok, GraalVM JavaScript, Docker, Maven

## 🏗️ 架构设计

项目采用特性垂直分包 (Feature-Based Packaging) 架构，按业务特性组织代码，每个特性包包含该特性所需的全部组件：

- **`api/`**: API 管理 — 定义、运行时执行、文档生成、GraphQL
- **`flow/`**: 流程编排 — 流程定义、实例管理、执行引擎、插件、校验
- **`auth/`**: 认证授权 — 用户、角色、资源、权限
- **`modeling/`**: 数据建模 — 模型定义、字段管理
- **`data/`**: 数据访问 — 记录 CRUD、查询
- **`scheduling/`**: 任务调度 — 触发器、作业执行
- **`storage/`**: 对象存储 — 本地、S3
- **`ai/`**: AI 聊天 — LLM 集成、对话管理
- **`settings/`**: 系统设置
- **`metrics/`**: 监控指标
- **`project/`**: 项目管理
- **`idp/`**: 身份提供商
- **`connect/`**: 数据源连接
- **`common/`**: 共享基础设施 — 配置、工具类、全局异常、安全过滤器

## 🚀 快速开始

### 环境要求
- Java 21+
- Maven 3.9+

### 启动项目
```bash
./mvnw quarkus:dev
```
项目将在开发模式下启动，支持热加载。

### 访问入口
- **Swagger UI**: `http://localhost:8080/q/swagger-ui`
- **GraphQL 界面**: `http://localhost:8080/graphiql` (如果已启用)
- **API 基础路径**: `/api`

## ⚙️ 配置说明

核心配置位于 `src/main/resources/application.properties`。
- 数据库配置: 默认使用 SQLite，可根据需要切换。
- AI 模型配置: 配置 `base-url` 和 `api-key` 以集成大模型。

## 📦 构建与部署

```bash
# 打包项目
./mvnw clean package

# 运行可执行 JAR
java -jar target/flexmodel-server-dev.jar
```

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。
