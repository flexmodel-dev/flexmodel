# FlexModel GraphQL Module

[![GraphQL](https://img.shields.io/badge/GraphQL-22.3+-purple.svg)](https://graphql.org/)
[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)

> FlexModel GraphQL 模块为 FlexModel 引擎提供完整的 GraphQL 支持，包括查询、变更和类型系统。

## 🚀 特性

- **自动 Schema 生成** — 根据数据模型自动生成 GraphQL Schema
- **类型映射** — 完整的 Java 类型到 GraphQL 类型映射
- **查询优化** — 智能查询优化和 N+1 问题解决
- **实时订阅** — 支持数据变更事件的 GraphQL Subscription
- **自定义标量** — 支持 JSON 等自定义 GraphQL 标量类型
- **指令支持** — 支持 @export、@transform、@internal 等自定义指令
- **惰性加载** — GraphQL 管理器支持惰性加载和缓存清理

## 📦 安装

### Maven 依赖

```xml
<dependency>
    <groupId>dev.flexmodel</groupId>
    <artifactId>flexmodel-graphql</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 🚀 快速开始

### 1. 基本配置

```java
// 创建 GraphQL Provider
GraphQLProvider graphQLProvider = new GraphQLProvider(sessionFactory);
graphQLProvider.init();

// 获取 GraphQL 实例
GraphQL graphQL = graphQLProvider.getGraphQL();
```

### 2. 执行查询

```java
// 定义查询
String query = """
    query GetStudents($minAge: Int, $size: Int = 10, $page: Int = 1) {
        student(where: {age: {_gte: $minAge}}, size: $size, page: $page) {
            total
            list {
                id
                studentName
                age
                gender
            }
        }
    }
    """;

Map<String, Object> variables = new HashMap<>();
variables.put("minAge", 18);

ExecutionInput input = ExecutionInput.newExecutionInput()
    .query(query)
    .variables(variables)
    .build();

ExecutionResult result = graphQL.execute(input);
```

### 3. 执行变更

```java
String mutation = """
    mutation CreateStudent($data: StudentInsertInput!) {
        createStudent(data: $data) {
            id
            studentName
            age
        }
    }
    """;

Map<String, Object> variables = new HashMap<>();
variables.put("data", Map.of(
    "studentName", "张三",
    "age", 18,
    "gender", "MALE"
));

ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
    .query(mutation)
    .variables(variables)
    .build());
```

## 📖 查询示例

### 基础查询

```graphql
query {
  student {
    id
    studentName
    age
    gender
  }
}
```

### 条件查询

```graphql
query {
  student(where: {age: {_gte: 18}}) {
    id
    studentName
    age
  }
}
```

### 分页查询

```graphql
query {
  student(page: 1, size: 10, orderBy: {age: desc}) {
    total
    list {
      id
      studentName
      age
    }
  }
}
```

### 关联查询

```graphql
query {
  student {
    id
    studentName
    studentClass {
      id
      className
    }
  }
}
```

### 聚合查询

```graphql
query {
  studentAggregate(where: {age: {_gte: 18}}) {
    _count
    _avg { age }
    _sum { age }
  }
}
```

### 跨源关联查询

```graphql
query MyQuery($studentId: Int @internal) {
  courses: system_list_Student(where: {studentName: {_in: ["李四", "王五"]}}) {
    id @export(as: "studentId")
    studentName
    _join {
      detail: system_find_one_StudentDetail(where: {studentId: {_eq: $studentId}}) {
        description
      }
    }
  }
}
```

### 转换结果

```graphql
query MyQuery {
  total: system_aggregate_Student @transform(get: "_count") {
    _count
  }
  maxAge: system_aggregate_Student @transform(get: "_max.age") {
    _max { age }
  }
}
```

## 🔧 配置选项

| 配置 | 说明 |
|------|------|
| 内省 (Introspection) | 启用/禁用 GraphQL 内省 |
| 最大查询深度 | 限制查询嵌套深度 |
| 最大查询复杂度 | 限制查询复杂度 |
| 查询缓存 | 启用/禁用查询缓存 |

## 📄 许可证

本项目采用 [Apache License 2.0](../../LICENSE) 许可证。
