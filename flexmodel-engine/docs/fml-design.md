# FML (FlexModel Language) 设计文档

FML 是 Flexmodel 的统一数据定义语言，将模型定义（Schema）和种子数据（Seed）合并到一个 `.fml` 文件中，替代之前 `.idl` + `.json` 分离的方式。

## 概述

| 特性 | IDL (.idl) | JSON (.json) | FML (.fml) |
|---|---|---|---|
| 模型定义 (model/enum) | ✅ | ✅ | ✅ |
| 种子数据 (seed) | ❌ | ✅ (ImportData) | ✅ |
| 可读性 | 高 | 低 | 高 |
| 单文件完整定义 | ❌ | ✅ | ✅ |

FML 是 IDL 的超集：所有合法的 `.idl` 文件都是合法的 `.fml` 文件。

## 文件扩展名

- `.fml` — 完整 FML 语言（model + enum + seed）
- `.idl` — 仅模型定义部分（向后兼容）
- `.json` — JSON 格式导入（向后兼容）

## 语法规范

### 模型定义

与 IDL 完全兼容，语法不变：

```fml
model User {
  id: String @id @default(uuid()),
  name: String @length(255),
  email: String @unique,
  role: UserRole,
  profile?: JSON,
  createdAt?: DateTime @default(now()),
}

enum UserRole {
  ADMIN,
  EDITOR,
  VIEWER
}
```

### 种子数据 (seed)

`seed` 关键字用于声明模型的初始数据：

```fml
seed UserRole {
  { code: "ADMIN", label: "管理员" },
  { code: "EDITOR", label: "编辑" },
  { code: "VIEWER", label: "访客" },
}
```

#### 基本语法

```
seed ModelName [@annotation(...)]* {
  { key: value, ... },
  ...
}
```

#### 值类型

| 类型 | 语法 | 示例 |
|---|---|---|
| 字符串 | `"..."` | `"张三"` |
| 整数 | `123` 或 `-123` | `25`, `-1000` |
| 浮点数 | `1.5` 或 `-3.14` | `95.5`, `-0.5` |
| 布尔 | `true` / `false` | `true` |
| null | `null` | `null` |
| 数组 | `[...]` | `["Java", "Python"]` |
| 嵌套对象 | `{ key: value }` | `{ brand: "华为", weight: 200 }` |
| 函数调用 | `func()` | `uuid()`, `now()` |

字符串支持转义序列：`\"` `\\` `\n` `\r` `\t` `\b` `\f` `\/`。

#### 键名

seed 记录中的键名支持两种形式：
- **标识符**：`name: "张三"` （以字母或下划线开头，只含字母、数字、下划线）
- **字符串字面量**：`"$schema": "http://..."` （用于包含特殊字符的键名，如 `$`、`-` 等）

#### 注解

seed 块支持可选注解（当前阶段解析但运行时仅执行 insert）：

```fml
seed User @strategy("skip_exists") @match("email") {
  { name: "管理员", email: "admin@system.local", role: "ADMIN" },
}
```

- `@strategy` — 写入策略：`insert`（默认）、`skip_exists`、`upsert`
- `@match` — 匹配字段名，用于 skip_exists / upsert 场景

### 完整示例

```fml
// ==============================
// 模型定义
// ==============================

model User {
  id: String @id @default(uuid()),
  name: String @length(255),
  email: String @unique,
  role: UserRole,
  active?: Boolean @default("true"),
  profile?: JSON,
  createdAt?: DateTime @default(now()),
}

enum UserRole {
  ADMIN, EDITOR, VIEWER
}

// ==============================
// 种子数据
// ==============================

seed UserRole {
  { code: "ADMIN", label: "管理员" },
  { code: "EDITOR", label: "编辑" },
  { code: "VIEWER", label: "访客" },
}

seed User @strategy("skip_exists") @match("email") {
  { name: "管理员", email: "admin@system.local", role: "ADMIN" },
  {
    name: "测试用户",
    email: "test@example.com",
    role: "VIEWER",
    profile: {
      department: "技术部",
      skills: ["Java", "Python"],
    },
  },
}
```

## 运行时集成

### SessionFactory API

```java
// 加载 FML 字符串（同时处理模型和种子数据）
sessionFactory.loadFMLString("schemaName", fmlString);

// 从文件加载（自动识别 .fml / .json / .idl）
sessionFactory.loadScript("schemaName", "import.fml");
```

### Codegen 集成

在 `SchemaConfig` 的 `importScript` 中使用 `.fml` 文件：

```xml
<schema>
  <name>system</name>
  <import-script>src/main/resources/system.fml</import-script>
  <packageName>dev.flexmodel.codegen</packageName>
</schema>
```

### AST 解析 API

```java
// 解析 FML 字符串，获取模型和种子数据
ASTNodeConverter.FMLParseResult result = ASTNodeConverter.parseFML(fmlString);
List<SchemaObject> models = result.getModels();
List<ModelImportBundle.ImportData> seeds = result.getSeeds();
```

## 迁移指南

### 从 IDL + JSON 迁移到 FML

之前需要两个文件：

```
platform_schema.idl  — 模型定义
platform_data.json   — 种子数据
```

合并为一个文件：

```
platform.fml — 模型定义 + 种子数据
```

JSON 中的 `data` 数组：

```json
{
  "data": [
    {
      "modelName": "Course",
      "values": [
        { "courseNo": "Math", "courseName": "数学" }
      ]
    }
  ]
}
```

转换为 FML 的 `seed` 块：

```fml
seed Course {
  { courseNo: "Math", courseName: "数学" },
}
```

### 向后兼容

- `.idl` 文件继续正常工作，`loadIDLString()` 和 `loadScript()` 中的 `.idl` 分支不变
- `.json` 文件继续正常工作，`loadJSONString()` 不变
- `ModelImportBundle.ImportData` 类型不变，seed 解析结果转换为同样的结构

## 实现细节

### 解析器

FML 解析器基于 JavaCC，语法定义在 `flexmodel-core/src/main/javacc/ModelParser.jj`。

关键产生式：
- `CompilationUnit` — 入口，支持 model / enum / seed 混合声明
- `SeedDeclaration` — seed 块解析
- `SeedRecord` / `SeedPairList` / `SeedValue` — 记录数据解析
- `SeedArray` / `SeedObject` — 嵌套结构解析

### 关键字

`seed` 和 `null` 是 FML 新增的保留关键字，定义在 `IDENTIFIER` 之前以确保优先匹配。现有 IDL 关键字（`model`、`enum`）同步移到 `IDENTIFIER` 之前。

### LOOKAHEAD 策略

`SeedRecordList` 和 `SeedPairList` 使用 `LOOKAHEAD` 避免贪婪消费逗号：
- 记录列表：`LOOKAHEAD(<COMMA> <LBRACE>)` — 逗号后必须是 `{` 才继续
- 键值对列表：`LOOKAHEAD(<COMMA> <IDENTIFIER> <COLON>)` — 逗号后必须是 `key:` 才继续

这确保了尾随逗号（trailing comma）的正确处理。
