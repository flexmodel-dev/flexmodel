# Flexmodel Server

> **Flexmodel Server** 是基于 Quarkus 的后端服务实现，作为 Flexmodel 项目的服务端核心，遵循 DDD（领域驱动设计）架构，集成了数据访问、服务编排、任务调度、文件存储和身份认证等核心功能。

## 🚀 核心特性

- **一站式后端能力**: 集成了身份认证 (Auth)、文件存储 (Storage)、任务调度 (Job) 及服务编排 (Flow)。
- **统一数据接口**: 抽象底层数据源差异，提供标准化的 REST 和 GraphQL 接口。
- **AI 赋能**: 内置 LangChain4j，提供流式对话能力 (SSE)，支持构建智能应用。
- **动态逻辑**: 支持基于 Groovy 的代码模板生成，实现灵活的业务逻辑扩展。
- **DDD 架构**: 严格的分层架构（接口层、应用层、领域层、基础设施层），易于演进和维护。

## 🛠️ 技术栈

- **主框架**: Quarkus 3 (REST, Jackson, Hibernate Validator, Scheduler, Cache)
- **AI 能力**: LangChain4j (OpenAI 兼容接口, StreamingChatModel)
- **API 协议**: REST API, GraphQL, OpenAPI/Swagger UI
- **持久化**: SQLite (默认演示) / 支持扩展多种关系型与 NoSQL 数据库
- **其他**: Lombok, Groovy, Docker, Maven

## 🏗️ 分层架构

项目采用 DDD 分层与按职责划分的包结构：

- **接口层 (Interfaces)**: `dev.flexmodel.interfaces` - 提供 REST API 及协议适配。
- **应用层 (Application)**: `dev.flexmodel.application` - 编排领域用例，处理 DTO、事务及业务流程。
- **领域层 (Domain)**: `dev.flexmodel.domain` - 核心领域模型与领域服务，包含仓储接口。
- **基础设施层 (Infrastructure)**: `dev.flexmodel.infrastructure` - 外部资源适配（数据库、调度、存储、AI）。
- **公共与工具 (Shared)**: `dev.flexmodel.shared` - 通用工具类。

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
