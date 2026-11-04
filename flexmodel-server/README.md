# Flexmodel Server

> **Flexmodel Server** 是基于 Quarkus 的后端服务实现，作为 Flexmodel 项目的服务端核心，采用特性垂直分包架构，集成了数据访问、服务编排、任务调度、对象存储和身份认证等核心功能。

## 🚀 核心特性

- **一站式后端能力**: 集成了身份认证 (Auth)、对象存储 (Storage)、任务调度 (Job)、服务编排 (Flow) 及 AI 工具集成 (MCP)。
- **统一数据接口**: 抽象底层数据源差异，提供标准化的 REST 和 GraphQL 接口。
- **AI 赋能**: 基于 MCP 协议开放核心工具，让 AI 客户端直接管理项目与数据。
- **动态逻辑**: 支持基于 JavaScript 的脚本执行，实现灵活的业务逻辑扩展。
- **特性垂直分包**: 按业务特性垂直分包，每个特性包内聚 Resource、Service、Repository、DTO，易于演进和维护。

## 🛠️ 技术栈

- **主框架**: Quarkus 3 (REST, Jackson, Hibernate Validator, Scheduler, Cache)
- **AI 能力**: MCP Server（Quarkus MCP 扩展，项目、模型、数据、函数、应用及通用人员目录工具）
- **边缘函数**: Deno Functions Runtime (Hono.js, Worker 隔离执行)
- **API 协议**: REST API, GraphQL, OpenAPI/Swagger UI
- **持久化**: SQLite (默认演示) / 支持扩展多种关系型与 NoSQL 数据库
- **其他**: Lombok, QuickJS, Docker, Maven

## 🏗️ 架构设计

项目采用特性垂直分包 (Feature-Based Packaging) 架构，按业务特性组织代码，每个特性包包含该特性所需的全部组件：

- **`api/`**: API 管理 — 定义、运行时执行、文档生成、GraphQL
- **`flow/`**: 流程编排 — 流程定义、实例管理、执行引擎、插件、校验
- **`auth/`**: 认证授权 — 用户、角色、资源、权限
- **`modeling/`**: 数据建模 — 模型定义、字段管理
- **`data/`**: 数据访问 — 记录 CRUD、查询
- **`scheduling/`**: 任务调度 — 触发器、作业执行
- **`storage/`**: 对象存储 — 本地、S3
- **`mcp/`**: MCP Server — AI 工具集成；AppTools 包含应用审批，DirectoryTools 提供通用人员目录与解析
- **`directory/`**: 通用人员、角色、组织架构目录与人员规则解析，供审批和 MCP 复用
- **`functions/`**: 边缘函数 — Deno Functions Runtime 隔离执行
- **`projectauth/`**: 身份提供商 — OIDC、LDAP、Script
- **`settings/`**: 系统设置
- **`metrics/`**: 监控指标
- **`project/`**: 项目管理
- **`realtime/`**: 实时推送 — WebSocket 数据变更广播
- **`connect/`**: 数据源连接
- **`common/`**: 共享基础设施 — 配置、工具类、全局异常、安全过滤器

## 🚀 快速开始

### 环境要求
- Java 25+
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

## 📦 构建与部署

```bash
# 打包项目
./mvnw clean package

# 运行可执行 JAR
java -jar target/flexmodel-server-dev.jar
```

审批流程的架构、可靠投递、执行人配置与验证方式见 [表单流程与审批接入](docs/approvals.md)。

Apps 的个人收件箱、SSE 实时通知与浏览器提醒见 [实时站内通知](docs/notifications.md)。

AI 创建或修改应用后的缓存失效、页面同步与编辑保护见 [AI 写操作后的页面同步](docs/ai-refresh.md)。

AI 会话的原生推理与摘要接入、流式协议和折叠展示设计见 [AI 推理内容展示技术方案](../docs/plans/ai-reasoning-display.md)
（技术设计，尚未实施）。

应用表单字段的默认值、输入校验与显示格式见 [字段类型属性](docs/field-properties.md)。

应用表单的授权配置与所有者字段见 [数据权限技术方案](docs/data-permissions.md)（当前仅保留配置，运行时授权未接入）。

应用多表单 Excel 模板、导入预览、关系重映射、事务提交与导出接口见 [多表单 Excel 导入导出](docs/excel-transfers.md)。

Apps 记录操作历史的采集规则、事件类型、存储模型与查询接口见 [记录操作历史](docs/record-history.md)（轻量日志，历史失败不影响业务，不支持恢复）。

Apps 表单高级查询的气泡框、条件结构与筛选导出见 [表单高级查询](docs/record-query.md)。

应用表单的字段状态矩阵、选项联动与条件规则设计见 [表单显示控制技术方案](docs/form-display-control.md)（设计与取舍依据，尚未实现）。

应用整包导入导出见 [应用包导入导出](../docs/plans/app-import-export.md)
；从内置模板创建应用见 [应用模板](../docs/plans/app-templates.md)。

## 📄 许可证

本项目源码仅供学习研究使用，**禁止用于商业用途**。如需合作定制开发，请联系作者。详见 [LICENSE](../LICENSE)。
