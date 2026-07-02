---
sidebar_position: 10
---

# flexmodel-core

作为Flexmodel的核心建模引擎，不仅集成了强大的建模功能，还能够独立运作为一款高效的ORM框架，它特别支持国产化数据库的深度适配，无需更改任何代码，为开发者提供了更加灵活与广泛的数据库操作解决方案。

## 功能一览

* 提供开发者友好的API，统一封装，屏蔽数据库差异，一招吃遍天下
* 支持数据库表、视图、字段、序列、索引的动态创建和删除，保证足够灵活
* 支持多达十几种数据库的适配，且已经拥有大量集成测试用例覆盖，以确保适用性和可靠性
* 支持自定义业务字段类型、数据验证器、值计算逻辑、建模的持久化方式
* 支持灵活的查询条件构建和DSL查询语法
* 支持关联查询和嵌套查询
* 支持分页、排序、聚合等高级查询功能
* 支持原生SQL查询和MongoDB查询
* 支持事务管理和批量操作
* 支持缓存机制和性能优化

## 数据库支持

以下为已经适配并且通过大量集成测试用例覆盖过的数据库

**关系型数据库**

| 数据库名称      | 兼容版本/已验证版本    | 连接参数                   | 状态 |
|------------|---------------|------------------------|------|
| MySQL      | 8.0           |                        | ✅ 完整支持 |
| MariaDB    | 10.3.6        |                        | ✅ 完整支持 |
| Oracle     | 21c           |                        | ✅ 完整支持 |
| SQL Server | 2017-CU12     |                        | ✅ 完整支持 |
| PostgreSQL | 16-3.4-alpine |                        | ✅ 完整支持 |
| DB2        | 11.5.0.0a     | progressiveStreaming=2 | ✅ 基础支持 |
| SQLite     | 3.45.3        |                        | ✅ 完整支持 |
| GBase      | 8s            | DELIMIDENT=y;          | ✅ 基础支持 |
| 达梦         | DM8           |                        | ✅ 基础支持 |
| TiDB       | v7.1.5        |                        | ✅ 完整支持 |

**文档型数据库**

| 数据库名称   | 兼容版本 | 连接参数 | 状态 |
|---------|------|------|------|
| MongoDB | 5.0  |      | ✅ 完整支持 |

## 基本概念

关于建模引擎你需要知道的一些事

### 实体（Entity）

实体（Entity）是指现实中有形的事物，在关系型数据库中对应数据表（Table），在MongoDB中对应集合（Collection）

### 字段（Field）

字段即实体的属性，目前已经内置一些常用的字段类型，开发者也可根据业务情况进行扩展

#### 内置的字段类型

| 类型       | 名称   | Java类型映射                | 描述 |
|----------|------|-------------------------|------|
| String   | 字符串  | java.lang.String        | 可变长度字符串，支持设置长度 |
| Int      | 整型   | java.lang.Integer       | 32位整数 |
| Long     | 长整型  | java.lang.Long          | 64位整数 |
| Float    | 浮点数  | java.lang.Double        | 双精度浮点数，支持设置精度(precision)和刻度(scale) |
| Boolean  | 布尔   | java.lang.Boolean       | 布尔值 |
| DateTime | 日期时间 | java.time.LocalDateTime | 日期时间 |
| Date     | 日期   | java.time.LocalDate     | 日期 |
| Time     | 时间   | java.time.LocalTime     | 时间 |
| JSON     | JSON | java.util.Map           | JSON数据 |
| EnumRef  | 枚举引用 | java.lang.String        | 引用枚举类型的值 |
| Relation | 关系   | 关联对象                 | 实体关联，支持设置关联字段和级联删除 |

### 视图（View）

视图是一个虚拟的表，其内容由查询定义，视图本身不支持存储数据，而是保存了查询语句。

### 索引（Index）

索引相当于一本书的目录，用于提高查询效率。

### 序列（Sequence）

序列是指按照一定规律增加的数字，一般作为唯一标识符使用，可以与年月日或特定字符组合使用。

## 核心功能

### 查询构建器

Flexmodel提供了强大的查询构建器，支持多种查询方式：

```java
// 基础查询 - 使用Query.Builder（推荐）
Query query = Query.Builder.create()
    .select("id", "name", "age")
    .where(Expressions.field("age").gte(18))
    .orderBy(orderBy -> orderBy.asc("name"))
    .page(1, 10)
    .build();

List<Map<String, Object>> results = session.data().find("users", query);

// 关联查询
Query joinQuery = Query.Builder.create()
    .select(select -> select
        .field("studentName", "studentName")
        .field("description", "studentDetail.description")
    )
    .leftJoin(join -> join
        .model("studentDetail")
    )
    .where(Expressions.field("id").eq("1"))
    .build();

// 聚合查询
Query aggregateQuery = Query.Builder.create()
    .select(projection -> projection
        .field("teacher_name", field("name"))
        .field("course_count", count(field("courses.teacher_id")))
        .field("course_score_sum", sum(field("courses.c_score")))
    )
    .innerJoin(joiners -> joiners
        .model("courses")
        .where("""
            {
              "teacher_id": {
                "_ne": 999
              }
            }
        """))
    .groupBy(groupBy -> groupBy
        .field("teacher_name")
    )
    .where(Expressions.field("name").eq("李四"))
    .build();
```

### 表达式构建

支持丰富的表达式操作：

```java
// 比较操作
Expressions.field("age").eq(18)
Expressions.field("age").gt(18)
Expressions.field("age").between(18, 25)

// 字符串操作
Expressions.field("name").contains("张")
Expressions.field("name").startsWith("张")
Expressions.field("name").endsWith("三")

// 逻辑操作
Expressions.field("age").gte(18).and(Expressions.field("status").eq("ACTIVE"))
Expressions.field("status").eq("ACTIVE").or(Expressions.field("status").eq("PENDING"))

// 集合操作
Expressions.field("status").in("ACTIVE", "PENDING")
Expressions.field("status").nin("DELETED")

// 方法引用支持
Expressions.field(User::getName).eq("张三")
```

### 模型定义

支持灵活的模型定义：

```java
// 实体定义
session.schema().createEntity("Student", entity -> entity
    .addField(new StringField("id").asIdentity().setDefaultValue(UUID))
    .addField(new StringField("studentName").setLength(255))
    .addField(new IntField("age"))
    .addField(new FloatField("score").setPrecision(5).setScale(2))
    .addField(new EnumRefField("gender").setFrom("UserGender"))
    .addField(new RelationField("studentClass")
        .setFrom("Classes")
        .setLocalField("classId")
        .setForeignField("id"))
    .addIndex(new IndexDefinition("Student")
        .setName("IDX_studentName")
        .addField("studentName", Direction.ASC)
        .setUnique(false))
);

// 枚举定义
session.schema().createEnum("UserGender", enumDef -> enumDef
    .setComment("用户性别")
    .addElement("UNKNOWN")
    .addElement("MALE")
    .addElement("FEMALE")
);
```

### 数据操作

```java
// 插入操作
Map<String, Object> userData = Map.of(
    "name", "张三",
    "age", 25,
    "email", "zhangsan@example.com"
);
int affectedRows = session.data().insert("users", userData);

// 批量插入
List<Map<String, Object>> users = Arrays.asList(user1, user2, user3);
session.data().insertBatch("users", users);

// 更新操作
Map<String, Object> updateData = Map.of("age", 26);
int updatedRows = session.data().update("users", updateData, 
    Expressions.field("id").eq("1"));

// 删除操作
int deletedRows = session.data().delete("users", 
    Expressions.field("id").eq("1"));

// 查询操作
Optional<Map<String, Object>> user = session.data().findById("users", "1");
List<Map<String, Object>> users = session.data().findMany("users", query);
long count = session.data().count("users", query);
boolean exists = session.data().exists("users", query);
```

### 原生查询支持

```java
// SQL原生查询
List<Map<String, Object>> results = session.data().executeNative(
    "SELECT * FROM users WHERE age > ${minAge} AND status = ${status}",
    Map.of("minAge", 18, "status", "ACTIVE")
);

// MongoDB原生查询
List<Map<String, Object>> results = session.data().executeNative(
    """
    {
        "find": "users",
        "filter": { "age": { "$gt": "${minAge}" }, "status": "${status}" },
        "sort": {"age": -1},
        "limit": 10
    }
    """,
    Map.of("minAge", 18, "status", "ACTIVE")
);

// 原生查询模型
NativeQueryDefinition queryModel = new NativeQueryDefinition("getActiveUsers");
queryModel.setStatement("SELECT * FROM users WHERE status = 'ACTIVE'");
session.schema().createNativeQuery(queryModel);

List<Map<String, Object>> results = session.data().executeNativeQuery(
    "getActiveUsers", Map.of());
```

## 使用

### 开发集成

为了确保最佳的兼容性和利用最新的Java平台特性，要求最低Java版本为Java 25，低于Java 21的版本不被支持

*引入maven依赖*

基本依赖

```xml
<dependencies>
  <groupId>dev.flexmodel</groupId>
  <artifactId>flexmodel-core</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependencies>
```

FlexModel本身不提供数据库的驱动连接的支持，所以还需要引入数据库厂商依赖，以MySQL为例

```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>8.0.33</version>
</dependency>
```

### 基本使用

```java
// 创建SessionFactory
SessionFactory sessionFactory = SessionFactory.builder()
    .setDefaultDataSourceProvider(new JdbcDataSourceProvider("default", dataSource))
    .build();

// 创建Session
Session session = sessionFactory.createSession("default");

// 查询操作
List<Map<String, Object>> users = session.data().findMany("users", 
    Query.Builder.create()
        .where(Expressions.field("age").gte(18))
        .build());

// 插入操作
Map<String, Object> userData = Map.of("name", "张三", "age", 18);
int affectedRows = session.data().insert("users", userData);

// 更新操作
session.data().update("users", 
    Map.of("age", 19), 
    Expressions.field("id").eq("1"));

// 删除操作
session.data().delete("users", 
    Expressions.field("id").eq("1"));
```

### 高级功能

#### 事务支持

```java
session.startTransaction();
try {
    session.data().insert("users", user1);
    session.data().insert("users", user2);
    session.commit();
} catch (Exception e) {
    session.rollback();
    throw e;
}
```

#### 批量操作

```java
// 批量插入
List<Map<String, Object>> users = Arrays.asList(user1, user2, user3);
session.data().insertBatch("users", users);

// 批量更新
session.data().update("users", 
    Map.of("status", "INACTIVE"), 
    Expressions.field("lastLoginTime").lt(LocalDateTime.now().minusDays(30)));
```

#### DSL操作

```java
// DSL插入
session.dsl()
    .insertInto("users")
    .values(userData)
    .execute();

// DSL更新
session.dsl()
    .update("users")
    .set("age", 19)
    .where(Expressions.field("id").eq("1"))
    .execute();

// DSL删除
session.dsl()
    .deleteFrom("users")
    .where(Expressions.field("id").eq("1"))
    .execute();
```

### Quarkus集成

FlexModel提供了与Quarkus框架的深度集成：

```java
// 配置SessionFactory
@ApplicationScoped
public class SessionConfig {
    @Produces
    @ApplicationScoped
    public SessionFactory sessionFactory() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:file::memory:?cache=shared");

        return SessionFactory.builder()
            .setDefaultDataSourceProvider(new JdbcDataSourceProvider("system", dataSource))
            .build();
    }
}

// 在REST资源中使用
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@SessionManaged // 类级别Session管理
public class UserResource {
    @Inject
    private Session session; // 自动注入当前请求的Session

    @GET
    public Response getAllUsers() {
        List<Map<String, Object>> users = session.data().findMany("users", 
            Query.Builder.create().build());
        return Response.ok(users).build();
    }
}

// 在服务类中使用
@ApplicationScoped
@SessionManaged
public class UserService {
    @Inject
    private Session session;

    public List<Map<String, Object>> getAllUsers() {
        return session.data().find("users", Query.Builder.create().build());
    }
}
```

## 扩展功能

### 自定义字段类型

```java
public class CustomField extends TypedField<String, CustomField> {
    public CustomField(String name) {
        super(name, "Custom");
    }
    
    // 自定义逻辑
}
```

### 自定义验证器

```java
public class AgeValidator implements FieldValidator {
    @Override
    public void validate(Object value) {
        if (value instanceof Integer) {
            int age = (Integer) value;
            if (age < 0 || age > 150) {
                throw new ValidationException("年龄必须在0-150之间");
            }
        }
    }
}
```

### 自定义计算器

```java
public class FullNameCalculator implements FieldCalculator {
    @Override
    public Object calculate(Map<String, Object> context) {
        String firstName = (String) context.get("firstName");
        String lastName = (String) context.get("lastName");
        return firstName + " " + lastName;
    }
}
```

## 性能优化

### 查询优化

1. **索引使用**: 为常用查询字段创建索引
2. **分页查询**: 使用分页避免大量数据查询
3. **字段选择**: 只选择需要的字段
4. **关联查询**: 合理使用关联查询，避免N+1问题

### 连接池配置

```java
// 配置连接池
DataSource datasource = new HikariDataSource();
((HikariDataSource) datasource).setMaximumPoolSize(20);
((HikariDataSource) datasource).setMinimumIdle(5);
```

### 缓存机制

```java
// 配置缓存
SessionFactory sessionFactory = SessionFactory.builder()
    .setDefaultDataSourceProvider(new JdbcDataSourceProvider("default", dataSource))
    .setCache(new ConcurrentHashMapCache()) // 使用内存缓存
    .build();
```

## 最佳实践

1. **模型设计**: 合理设计实体关系，避免过度复杂
2. **查询优化**: 使用合适的查询条件和索引
3. **事务管理**: 合理使用事务，避免长事务
4. **错误处理**: 妥善处理异常情况
5. **日志记录**: 记录关键操作的日志
6. **Session管理**: 在Web应用中合理使用Session生命周期管理
7. **批量操作**: 对于大量数据操作，使用批量接口提高性能

## 测试支持

FlexModel提供了完整的测试支持，包括：

- 单元测试框架
- 集成测试支持
- 多数据库测试容器
- 测试数据管理

```java
@Testcontainers
public class MySQLIntegrationTests extends AbstractSessionTests {
    @Container
    public static MySQLContainer container = new MySQLContainer("mysql:8.0");

    @BeforeAll
    public static void beforeAll() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(container.getJdbcUrl());
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        initSession(new JdbcDataSourceProvider("default", dataSource));
    }
}
```
