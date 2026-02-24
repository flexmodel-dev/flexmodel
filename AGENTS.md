# FlexModel Project Agents Guide

本文档为 FlexModel 项目的 AI 代理（Agents）提供全局指导。FlexModel 是一个高度灵活的、模型驱动的开发平台，包含引擎、服务端和前端 UI。

## 1. 项目全景

FlexModel 采用多模块结构，每个模块都有其特定的职责和规范：

- **[flexmodel-engine](./flexmodel-engine/AGENTS.md)**: 核心 DSL 引擎。负责模型定义、查询解析、代码生成及多数据库适配。
- **[flexmodel-server](./flexmodel-server/AGENTS.md)**: 基于 Quarkus 的服务端实现。遵循 DDD 架构，提供 REST/GraphQL API、AI 聊天及任务调度。
- **[flexmodel-ui](./flexmodel-ui/AGENTS.md)**: 基于 React + Ant Design 的前端管理后台。负责模型可视化设计、数据管理及系统配置。

## 2. 核心设计哲学

- **模型驱动 (Model-Driven)**: 核心是 `Model` 而不是 `Table`。一切操作应基于模型抽象。
- **DSL 优先**: 推荐使用 `QueryDSL` 进行数据操作，确保持久层的灵活性和类型安全。
- **领域驱动设计 (DDD)**: 服务端代码严格遵循领域层、应用层、接口层的分层结构。
- **组件化 UI**: 前端优先使用标准组件，保持视觉和交互的一致性。

## 3. Agent 行为准则

### 通用要求
- **上下文识别**: 在修改代码前，先确认所属模块（Engine/Server/UI）并查阅对应的 `AGENTS.md` 指南。
- **语言规范**: 代码注释、文档、Git 提交信息及 AI 回复应使用 **中文**。
- **一致性**: 保持与现有代码风格、命名规范和目录结构高度一致。
- **验证驱动**: 完成代码修改后，必须通过单元测试或集成测试进行验证。

### 技术栈速查
- **后端**: Java 21, Quarkus 3, Maven, SQLite/MySQL/PostgreSQL, MongoDB.
- **前端**: React, TypeScript, Ant Design v5, Tailwind CSS, Vite.
- **协议**: REST API, GraphQL, WebSocket, SSE.

## 4. 模块详细指南

请根据你当前的工作任务，跳转到对应模块的详细指南：

1.  **引擎开发**: 参考 [flexmodel-engine/AGENTS.md](./flexmodel-engine/AGENTS.md)
    - 关注：DSL 语法、模型解析、数据库适配器。
2.  **服务端开发**: 参考 [flexmodel-server/AGENTS.md](./flexmodel-server/AGENTS.md)
    - 关注：DDD 分层、REST 资源、AI 集成、业务逻辑。
3.  **前端开发**: 参考 [flexmodel-ui/AGENTS.md](./flexmodel-ui/AGENTS.md)
    - 关注：React 组件、Ant Design 规范、状态管理、国际化。

---

*注意：此文档应随项目演进而持续更新。*
