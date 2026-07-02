---
sidebar_position: 30
---

# flexmodel-graphql

Flexmodel的GraphQL模块，自动为数据模型生成GraphQL Schema和查询接口。

## 功能特性

* 自动生成GraphQL Schema
* 支持查询、变更、订阅操作
* 支持分页、排序、过滤
* 支持关联查询和嵌套查询
* 支持聚合查询
* 类型安全的查询构建

## 引入依赖

```xml
<dependency>
    <groupId>dev.flexmodel</groupId>
    <artifactId>flexmodel-graphql</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 基本使用

### 初始化GraphQL

```java
// 创建GraphQL提供者
GraphQLProvider graphQLProvider = new GraphQLProvider(sessionFactory);
graphQLProvider.init();

// 获取GraphQL实例
GraphQL graphQL = graphQLProvider.getGraphQL();
```

### 执行查询

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
                studentClass {
                    id
                    className
                }
            }
        }
    }
    """;

// 设置变量
Map<String, Object> variables = new HashMap<>();
variables.put("minAge", 18);
variables.put("pageSize", 10);

// 执行查询
ExecutionInput executionInput = ExecutionInput.newExecutionInput()
    .query(query)
    .variables(variables)
    .build();

ExecutionResult result = graphQL.execute(executionInput);

// 处理结果
if (result.getErrors().isEmpty()) {
    Map<String, Object> data = result.getData();
    // 处理查询结果
} else {
    // 处理错误
    result.getErrors().forEach(error -> {
        System.err.println("GraphQL Error: " + error.getMessage());
    });
}
```

### 执行变更

```java
// 创建学生
String mutation = """
    mutation CreateStudent($data: StudentInsertInput!) {
        createStudent(data: $data) {
            id
            studentName
            age
            gender
            createdAt
        }
    }
    """;

Map<String, Object> variables = new HashMap<>();
variables.put("data", Map.of(
    "studentName", "张三",
    "age", 18,
    "gender", "MALE"
));

ExecutionInput executionInput = ExecutionInput.newExecutionInput()
    .query(mutation)
    .variables(variables)
    .build();

ExecutionResult result = graphQL.execute(executionInput);
```

## 高级功能

### 自定义DataFetcher

```java
// 创建自定义DataFetcher
public class CustomStudentDataFetcher extends FlexmodelAbstractDataFetcher {
    
    public CustomStudentDataFetcher(SessionFactory sessionFactory) {
        super(sessionFactory);
    }
    
    @Override
    public Object get(DataFetchingEnvironment environment) {
        // 自定义查询逻辑
        String studentId = environment.getArgument("id");
        return session.data().findById("Student", studentId).orElse(null);
    }
}

// 注册自定义DataFetcher
graphQLProvider.registerDataFetcher("Query", "customStudent", 
    new CustomStudentDataFetcher(sessionFactory));
```

### 自定义指令

```java
// 创建自定义指令
public class AuthDirective implements GraphQLDirective {
    
    @Override
    public String getName() {
        return "auth";
    }
    
    @Override
    public String getDescription() {
        return "Authentication directive";
    }
    
    @Override
    public List<GraphQLArgument> getArguments() {
        return Arrays.asList(
            GraphQLArgument.newArgument()
                .name("required")
                .type(Scalars.GraphQLBoolean)
                .defaultValue(true)
                .build()
        );
    }
}

// 注册指令
graphQLProvider.registerDirective(new AuthDirective());
```

### 错误处理

```java
// 自定义错误处理
graphQLProvider.setErrorHandler(new GraphQLErrorHandler() {
    @Override
    public List<GraphQLError> processErrors(List<GraphQLError> errors) {
        return errors.stream()
            .map(error -> {
                if (error instanceof ExceptionWhileDataFetching) {
                    ExceptionWhileDataFetching exceptionError = (ExceptionWhileDataFetching) error;
                    return GraphqlErrorBuilder.newError()
                        .message("数据获取错误: " + exceptionError.getException().getMessage())
                        .errorType(ErrorType.DataFetchingException)
                        .build();
                }
                return error;
            })
            .collect(Collectors.toList());
    }
});
```

## 查询示例

### 基础查询

```graphql
# 获取所有学生
query {
  student {
    id
    studentName
    age
    gender
  }
}

# 获取单个学生
query {
  studentById(id: "1") {
    id
    studentName
    age
    gender
  }
}
```

### 条件查询

```graphql
# 年龄大于18的学生
query {
  student(where: {age: {_gte: 18}}) {
    id
    studentName
    age
  }
}

# 姓张的学生
query {
  student(where: {studentName: {_contains: "张"}}) {
    id
    studentName
  }
}
```

### 分页查询

```graphql
query {
  student(
    page: 1
    size: 10
    orderBy: {age: desc}
  ) {
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
      classCode
    }
  }
}
```

### 聚合查询

```graphql
query {
  studentAggregate(where: {age: {_gte: 18}}) {
    _count
    _avg {
      age
    }
    _sum {
      age
    }
    _min {
      age
    }
    _max {
      age
    }
  }
}
```

## 变更示例

### 创建记录

```graphql
mutation {
  createStudent(data: {
    studentName: "张三"
    age: 18
    gender: MALE
    classId: 1
  }) {
    id
    studentName
    age
    gender
    createdAt
  }
}
```

### 更新记录

```graphql
mutation {
  updateStudentById(
    id: "1"
    set: {
      studentName: "张三（已更新）"
      age: 19
    }
  ) {
    id
    studentName
    age
    updatedAt
  }
}
```

### 删除记录

```graphql
mutation {
  deleteStudentById(id: "1") {
    id
    studentName
  }
}
```

## 配置选项

### 启用内省

```java
graphQLProvider.setIntrospectionEnabled(true);
```

### 设置最大查询深度

```java
graphQLProvider.setMaxQueryDepth(10);
```

### 设置最大查询复杂度

```java
graphQLProvider.setMaxQueryComplexity(100);
```

### 启用查询缓存

```java
graphQLProvider.setQueryCacheEnabled(true);
```

## 性能优化

### 查询优化

1. **字段选择**: 只选择需要的字段
2. **分页查询**: 使用分页避免大量数据查询
3. **索引使用**: 为常用查询字段创建索引
4. **关联查询**: 合理使用关联查询，避免N+1问题

### 缓存策略

```java
// 启用查询缓存
graphQLProvider.setQueryCacheEnabled(true);

// 设置缓存大小
graphQLProvider.setQueryCacheSize(1000);

// 设置缓存过期时间
graphQLProvider.setQueryCacheExpireAfterWrite(Duration.ofMinutes(30));
```

## 最佳实践

1. **Schema设计**: 合理设计GraphQL Schema
2. **查询优化**: 使用合适的查询条件和分页
3. **错误处理**: 妥善处理GraphQL错误
4. **安全性**: 实现适当的认证和授权
5. **监控**: 监控GraphQL查询性能

## 工具支持

### GraphiQL

访问 `/graphiql` 可以使用GraphiQL交互式查询工具。

### 客户端库

推荐使用以下GraphQL客户端库：

- **JavaScript/TypeScript**: Apollo Client, Relay
- **Java**: Apollo Android, GraphQL Java
- **Python**: GQL, Python GraphQL Client
- **Go**: Machinebox GraphQL
