---
sidebar_position: 20
---

# 查询条件

Flexmodel 支持灵活的查询条件语法，用于过滤和查询数据记录。

## 基础语法

查询条件使用 JSON 格式，核心规则：

- **同层字段隐式 AND**：同一层级的多个字段条件自动以 AND 组合，无需显式使用 `_and`
- **快捷等于写法**：`{"字段名": 值}` 等价于 `{"字段名": {"_eq": 值}}`
- **点号路径**：嵌套字段或关联字段可以用点号表示，如 `"address.city"` 或 `"studentClass.className"`

### 比较操作符

| 操作符 | 描述 | 示例 |
|--------|------|------|
| `_eq` | 等于 | `{"name": {"_eq": "张三"}}` |
| `_ne` | 不等于 | `{"age": {"_ne": 18}}` |
| `_gt` | 大于 | `{"age": {"_gt": 18}}` |
| `_gte` | 大于等于 | `{"age": {"_gte": 18}}` |
| `_lt` | 小于 | `{"age": {"_lt": 65}}` |
| `_lte` | 小于等于 | `{"age": {"_lte": 65}}` |

### 字符串操作符

| 操作符 | 描述 | 示例 |
|--------|------|------|
| `_contains` | 包含 | `{"name": {"_contains": "张"}}` |
| `_not_contains` | 不包含 | `{"name": {"_not_contains": "李"}}` |
| `_starts_with` | 以...开始 | `{"name": {"_starts_with": "张"}}` |
| `_ends_with` | 以...结束 | `{"name": {"_ends_with": "三"}}` |

### 集合操作符

| 操作符 | 描述 | 示例 |
|--------|------|------|
| `_in` | 在...中 | `{"status": {"_in": ["active", "pending"]}}` |
| `_nin` | 不在...中 | `{"status": {"_nin": ["deleted"]}}` |

### 范围操作符

| 操作符 | 描述 | 示例 |
|--------|------|------|
| `_between` | 在...之间 | `{"age": {"_between": [18, 65]}}` |

### 逻辑操作符

| 操作符 | 描述 | 示例 |
|--------|------|------|
| `_and` | 与 | `{"_and": [{"age": {"_gte": 18}}, {"status": {"_eq": "active"}}]}` |
| `_or` | 或 | `{"_or": [{"status": {"_eq": "active"}}, {"status": {"_eq": "pending"}}]}` |

> 大多数场景下，同层字段已经隐式 AND，只有需要 OR 逻辑或复杂嵌套组合时才需要使用 `_and` / `_or`。

## 查询示例

### 单个条件

```json
{"username": {"_eq": "john_doe"}}
```

快捷写法：

```json
{"username": "john_doe"}
```

### 多个条件（隐式 AND）

同层多个字段自动 AND，这是最推荐的写法：

```json
{
  "username": {"_eq": "john_doe"},
  "age": {"_gte": 18},
  "status": {"_eq": "active"}
}
```

等价于 SQL：`WHERE username = 'john_doe' AND age >= 18 AND status = 'active'`

### 多个条件（OR）

需要 OR 逻辑时使用 `_or`：

```json
{
  "_or": [
    {"status": {"_eq": "active"}},
    {"status": {"_eq": "pending"}}
  ]
}
```

### 混合条件（同层 AND + OR）

同层字段可以与 `_or` / `_and` 混合使用：

```json
{
  "age": {"_gte": 18},
  "createdAt": {"_between": ["2023-01-01 00:00:00", "2023-12-31 23:59:59"]},
  "_or": [
    {"gender": {"_eq": "MALE"}},
    {"gender": {"_eq": "FEMALE"}}
  ]
}
```

等价于 SQL：`WHERE age >= 18 AND createdAt BETWEEN '...' AND '...' AND (gender = 'MALE' OR gender = 'FEMALE')`

### 字符串搜索

```json
{
  "_or": [
    {"name": {"_contains": "张"}},
    {"email": {"_contains": "zhang"}}
  ]
}
```

### 枚举值查询

```json
{"status": {"_in": ["ACTIVE", "PENDING", "APPROVED"]}}
```

### 日期时间查询

```json
{
  "createdAt": {"_gte": "2023-01-01 00:00:00"},
  "updatedAt": {"_lte": "2023-12-31 23:59:59"}
}
```

### 同一字段多个条件

同一个字段也可以写多个操作符，自动 AND：

```json
{"age": {"_gte": 18, "_lt": 65}}
```

等价于 SQL：`WHERE age >= 18 AND age < 65`

## 在 API 中使用

### REST API

在查询记录时，通过 `filter` 查询参数传递条件（JSON 字符串需要 URL 编码）：

```bash
GET /api/projects/{projectId}/models/{modelName}/records?filter=%7B%22status%22%3A%7B%22_eq%22%3A%22active%22%7D%7D
```

原始 JSON：

```json
{"status": {"_eq": "active"}}
```

### GraphQL API

在 GraphQL 查询中使用 `where` 参数：

```graphql
query {
  students(where: {age: {_gte: 18}}) {
    id
    name
    age
  }
}
```

## 高级用法

### 关联查询

支持通过嵌套对象查询关联数据：

```json
{
  "studentClass": {
    "className": {"_contains": "计算机"}
  }
}
```

也可以使用点号路径的扁平写法：

```json
{"studentClass.className": {"_contains": "计算机"}}
```

### 多层关联查询

```json
{
  "studentClass": {
    "teacher": {
      "name": {"_contains": "李"}
    }
  }
}
```

点号路径写法：

```json
{"studentClass.teacher.name": {"_contains": "李"}}
```

### 空值查询

查询空值或非空值：

```json
{"description": {"_eq": null}}
```

```json
{"description": {"_ne": null}}
```

### 复杂嵌套组合

当需要多层 OR 与 AND 组合时，可以使用 `_and` / `_or` 嵌套：

```json
{
  "age": {"_gte": 18},
  "_or": [
    {
      "status": {"_eq": "active"},
      "role": {"_nin": ["banned"]}
    },
    {
      "role": {"_eq": "admin"}
    }
  ]
}
```

等价于 SQL：`WHERE age >= 18 AND ((status = 'active' AND role NOT IN ('banned')) OR role = 'admin')`

## 注意事项

1. **数据类型匹配**: 确保查询条件的值与字段类型匹配
2. **日期格式**: 日期时间字段使用格式：`YYYY-MM-DD HH:mm:ss`
3. **字符串转义**: 在 URL 中传递 JSON 时需要正确 URL 编码
4. **性能考虑**: 复杂查询可能影响性能，建议在相关字段上创建索引
5. **大小写敏感**: 字符串比较默认区分大小写
6. **渲染机制**: Flexmodel-Core 会先将 DSL 解析为内部条件语法树，再分别渲染为 SQL 或 Mongo 查询语句
