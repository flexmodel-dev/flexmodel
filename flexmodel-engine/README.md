# Flexmodel Engine

[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](https://github.com/flexmodel-dev/flexmodel-engine)

> **Flexmodel** 是面向下一代应用程序的统一数据访问层解决方案。它以 `flexmodel-engine` 为核心，提供了一站式的数据建模、服务编排、任务调度、文件存储和身份认证能力，旨在为开发者提供开源、灵活且高度可定制的后端基础架构。

## 🚀 核心支柱

Flexmodel 实现了 Supabase 核心功能的中国化适配与增强：

- 📊 **统一数据管理 (Data)** - 运行时动态建模，支持 10+ 种主流及国产数据库，屏蔽底层差异。
- ⚡ **服务编排 (Service Orchestration)** - 基于可视化流程的业务逻辑编排，实现低代码后端逻辑扩展。
- ⏰ **任务调度 (Task Scheduling)** - 内置分布式任务触发与调度系统，支持 Cron、延迟任务及事件触发。
- 📂 **文件存储 (File Storage)** - 抽象化的文件存储接口，无缝集成 S3、OSS 及本地存储系统。
- 🔐 **身份认证 (Identity Authentication)** - 完整的 RBAC 权限体系、多租户支持及基于 JWT 的安全验证。

## ✨ 引擎特性

作为 Flexmodel 的核心引擎，`flexmodel-engine` 提供以下关键能力：

- **统一 DSL** - 使用统一的领域特定语言进行跨数据库操作，降低开发成本。
- **动态数据建模** - 运行时动态创建和修改数据模型，无需重启服务或修改代码。
- **智能代码生成** - 自动生成 DAO、DSL、Entity 及 API 模板，加速开发流程。
- **GraphQL 支持** - 内置 GraphQL 查询和变更支持，提供灵活、高效的数据访问接口。
- **国产化适配** - 深度支持达梦 (DM)、金仓 (GBase) 等国产数据库，满足信创要求。

## 🏗️ 架构设计

![Architecture_Design](docs/Architecture_Design.png)

## 🔧 核心模块

- **flexmodel-core**: 核心引擎模块，提供基础的数据访问、模型管理及多数据源适配功能。
- **flexmodel-codegen**: 代码生成模块，自动生成 DAO、DSL、Entity 等代码。
- **flexmodel-graphql**: GraphQL 支持模块，提供标准的 GraphQL 查询和变更功能。
- **flexmodel-maven-plugin**: Maven 插件，将代码生成能力集成到 Maven 构建流程中。

## 🚀 快速开始

### 环境要求
- Java 21+
- Maven 3.6+
- 支持的数据库（MySQL、PostgreSQL、SQLite 等）

### Maven 依赖

```xml
<dependency>
    <groupId>dev.flexmodel</groupId>
    <artifactId>flexmodel-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 基本使用

```java
// 创建 SessionFactory
SessionFactory sessionFactory = SessionFactory.builder()
    .setDefaultDataSourceProvider(new JdbcDataSourceProvider(dataSource))
    .setCache(new ConcurrentHashMapCache())
    .build();

// 创建 Session
try (Session session = sessionFactory.createSession("mySchema")) {
    // 加载模型定义
    sessionFactory.loadJSONString("mySchema", jsonSchema);
    
    // 执行数据操作
    DataOperations operations = session.getDataOperations();
    List<Map<String, Object>> results = operations.query("SELECT * FROM users");
}
```

## 📖 使用指南

### 1. 数据模型定义

使用 JSON 格式定义数据模型：

```json
{
  "objects": [
    {
      "type": "entity",
      "name": "User",
      "comment": "用户表",
      "fields": [
        {
          "type": "INT",
          "name": "id",
          "identity": true,
          "autoIncrement": true
        },
        {
          "type": "STRING",
          "name": "name",
          "length": 100,
          "nullable": false
        }
      ]
    }
  ]
}
```

### 2. JSON 逻辑表达式

支持复杂的逻辑过滤条件：

```json
{
  "and": [
    { ">": [{"var": "age"}, 18] },
    { "==": [{"var": "status"}, "active"] }
  ]
}
```

### 3. GraphQL 查询

```graphql
query {
  users {
    id
    name
    email
  }
}
```

## 🔌 数据库支持

| 数据库 | 状态 | 特性支持 |
|--------|------|----------|
| **MySQL / MariaDB / TiDB** | ✅ | 完整支持 |
| **PostgreSQL** | ✅ | 完整支持 |
| **Oracle / SQL Server** | ✅ | 完整支持 |
| **SQLite** | ✅ | 完整支持 |
| **MongoDB** | ✅ | 完整支持 |
| **达梦 (DM) / 金仓 (GBase)** | ✅ | 深度支持 |
| **DB2** | ✅ | 基础支持 |

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
mvn test -pl flexmodel-core

# 运行集成测试
mvn test -pl integration-tests
```

## 🤝 贡献指南

我们欢迎所有形式的贡献！请查看我们的 [贡献指南](CONTRIBUTING.md)。

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

---

**FlexModel** - 让后端开发更简单、更高效、更灵活！
