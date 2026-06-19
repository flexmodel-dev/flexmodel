# Flexmodel

[![License](https://img.shields.io/badge/License-Non--Commercial-orange.svg)](LICENSE)

> **⚠️ 声明：本源码仅供学习研究使用，禁止用于商业用途。如需合作定制开发，请联系作者。**

> **Flexmodel** 是面向下一代应用程序的统一数据访问层解决方案。提供一站式的数据建模、服务编排、任务调度、对象存储和身份认证能力。

## 🌟 项目简介

Flexmodel 旨在为开发者提供一个开源、灵活且高度可定制的后端基础架构。它通过模型驱动的设计理念，屏蔽了底层基础设施的复杂性，让开发者能够专注于业务逻辑的实现。

## 🚀 核心特性

- 📊 **数据管理 (Data)** - 运行时动态建模，深度适配 10+ 种主流及国产数据库。
- ⚡ **服务编排 (Flow)** - 基于可视化流程的业务逻辑编排，实现低代码后端逻辑扩展。
- ⏰ **任务调度 (Job)** - 内置分布式任务触发与调度系统，支持 Cron、延时及事件驱动。
- 📂 **对象存储 (Storage)** - 抽象化的对象存储接口，支持 S3、OSS 及本地存储。
- 🔐 **身份认证 (Auth)** - 完整的 RBAC 权限体系、多租户支持及 JWT 安全验证。
- 🤖 **AI 工具集成 (MCP)** - 基于 MCP 协议开放核心工具，让 AI 客户端直接管理数据。
- ☁️ **边缘函数 (Functions)** - Deno Sidecar 隔离执行的边缘函数能力（实验性）。

## 🏗️ 项目结构

本项目采用多模块架构，各模块职责清晰：

- **[flexmodel-engine](./flexmodel-engine)**: 核心 DSL 引擎。负责模型定义、查询解析、代码生成及多数据库适配。
- **[flexmodel-server](./flexmodel-server)**: 基于 Quarkus 的服务端实现。提供 REST/GraphQL/MCP API、流程编排及任务调度。
- **[flexmodel-ui](./flexmodel-ui)**: 基于 React + Ant Design v6 的前端管理后台。负责模型可视化设计、数据管理及系统配置。
- **[flexmodel-website](./flexmodel-website)**: 项目官方文档网站。基于 Docusaurus 构建，提供完整的技术文档和快速上手指南。
- **[flexmodel-sidecar](./flexmodel-sidecar)**: 边缘函数运行时。基于 Deno + Hono.js，提供 Worker 隔离执行环境。

## 🛠️ 技术栈

- **后端**: Java 25, Quarkus 3, Maven, GraalVM JavaScript, HikariCP.
- **前端**: React, TypeScript, Ant Design v6, Tailwind CSS, Vite, @xyflow/react, ECharts, Monaco Editor.
- **边缘函数**: Deno, Hono.js.
- **协议**: REST API, GraphQL, MCP, WebSocket, SSE.

## 📖 快速开始

请参考各模块的 `README.md` 获取详细的安装和使用指南：

1. [Flexmodel Engine 快速开始](./flexmodel-engine/README.md)
2. [Flexmodel Server 快速开始](./flexmodel-server/README.md)
3. [Flexmodel UI 快速开始](./flexmodel-ui/README.md)
4. [Flexmodel Website 快速开始](./flexmodel-website/README.md)

## 🤝 贡献指南

我们欢迎所有形式的贡献！请查看各模块的贡献指南。

## 📄 许可证

本项目源码仅供学习研究使用，**禁止用于商业用途**。如需合作定制开发，请联系作者。

如需了解商业授权或定制开发合作，请通过项目仓库中的联系方式与作者沟通。
