# @flexmodel/sdk

Flexmodel TypeScript SDK — 模型命名空间 + 选项对象 API，通过 `client.data` 对数据层进行 CRUD 操作，链式构建器用于复杂查询场景。

## 安装

```bash
npm install @flexmodel/sdk
# 或
yarn add @flexmodel/sdk
# 或
pnpm add @flexmodel/sdk
```

## 快速开始

```typescript
import { FlexmodelClient } from '@flexmodel/sdk'

const client = new FlexmodelClient({
  apiKey: 'fm_ak_xxxxx',
  projectId: 'my-project',
})

// 查询
const { list, total } = await client.data.from('Student').findMany({
  where: { classId: { _eq: 1 }, age: { _gt: 15 } },
  orderBy: 'name',
  page: 1,
  size: 20,
})

// 获取单条
const student = await client.data.from('Student').findOne('001', { expand: ['classId'] })

// 创建
const created = await client.data.from('Student').create({ name: 'Alice', age: 16, classId: 1 })

// 批量创建
const batch = await client.data.from('Student').createMany([
  { name: 'Alice', age: 16 },
  { name: 'Bob', age: 17 },
])

// 更新（全量替换）
await client.data.from('Student').update(1, { data: { name: 'Alicia' } })

// 批量更新（每条记录必须包含 id 字段）
await client.data.from('Student').updateMany({ data: [
  { id: 1, name: 'Alicia' },
  { id: 2, name: 'Bob Updated' },
] })

// 更新（部分合并）
await client.data.from('Student').merge(1, { data: { name: 'Alicia' } })

// 删除
await client.data.from('Student').delete(1)

// 批量删除
await client.data.from('Student').deleteMany({ ids: [1, 2, 3] })

// 计数
const count = await client.data.from('Student').count({ where: { age: { _gt: 18 } } })
```

## 认证

V1 仅支持 **API Key** 认证：

```typescript
const client = new FlexmodelClient({
  apiKey: 'fm_ak_xxxxx',  // fm_ak_ 前缀
  projectId: 'my-project',
})
```

SDK 自动将 API Key 注入为 `Authorization: Bearer fm_ak_xxxxx` 请求头。

## 客户端初始化

```typescript
new FlexmodelClient(options?: FlexmodelClientOptions)
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `baseURL` | `string` | 否 | API 地址，浏览器默认同源（`window.location.origin`），Node/Deno 需提供 |
| `apiKey` | `string` | 否 | API Key，提供后所有请求自动注入认证头 |
| `projectId` | `string` | 否 | 数据 API 的默认项目 ID，可在 per-call 时通过 `.project()` 覆盖 |

```typescript
// 最简用法（浏览器同源）
const client = new FlexmodelClient({ apiKey: 'fm_ak_xxx', projectId: 'demo' })

// 跨域场景
const client = new FlexmodelClient({
  baseURL: 'https://api.example.com',
  apiKey: 'fm_ak_xxx',
  projectId: 'demo',
})
```

## 数据操作命名空间

所有数据 CRUD 通过 `client.data` 命名空间访问：

### `client.data.from(model)`

显式选择目标模型，返回 `ModelHandle`：

```typescript
const handle = client.data.from('Student')
await handle.findMany({ where: { age: { _eq: 18 } } })
```

### `client.data.Student`（Proxy 属性访问）

Proxy 拦截属性访问，运行时等价于 `from()`：

```typescript
// 等价于 client.data.from('Student')
await client.data.Student.findMany({ where: { age: { _eq: 18 } } })
```

V2+ 提供 schema 定义后，IDE 将自动补全模型名和字段名。

### `project()` 覆盖 projectId

```typescript
await client.data.from('Student').project('other-project').findMany({})
```

## ModelHandle 便捷方法

| 方法 | HTTP | 说明 |
|------|------|------|
| `.findMany(opts?)` | `GET` | 分页查询，返回 `PageDTO<T>` |
| `.findOne(id, opts?)` | `GET` | 按 ID 获取单条记录 |
| `.create(data)` | `POST` | 创建单条记录 |
| `.create(data[])` | `POST` | 批量创建记录（调用 /batch 端点） |
| `.createMany(data[])` | `POST` | 批量创建记录，返回 `T[]` |
| `.update(id, { data })` | `PUT` | 全量更新 |
| `.updateMany({ data })` | `PUT` | 批量更新，每条记录必须含 id |
| `.merge(id, { data })` | `PATCH` | 部分更新 |
| `.delete(id)` | `DELETE` | 删除记录 |
| `.deleteMany({ ids })` | `DELETE` | 批量删除，返回删除数量 |
| `.count(opts?)` | `GET` | 计数，返回 `number` |
| `.query()` | — | 返回链式构建器（高级路径） |

### findMany 选项

```typescript
interface FindManyOptions<T> {
  where?: FilterNode           // 过滤条件
  orderBy?: SortInput          // 排序
  page?: number                // 页码（默认 1）
  size?: number                // 每页条数（默认 15）
  expand?: FieldSelection      // 关联加载
  select?: FieldSelection      // 投影字段
}
```

**排序简写**：`'name'` → ASC，`'name:DESC'` → DESC，也支持 `SortItem` 对象和数组。

```typescript
await client.data.from('Student').findMany({
  where: { age: { _gte: 18 } },
  orderBy: 'name:DESC',
  page: 1,
  size: 20,
  expand: ['classId', 'courseIds'],
})
```

**关联加载简写**：`'class,teacher'` 或 `['class', 'teacher']`。

### findOne 选项

```typescript
await client.data.from('Student').findOne('001', { expand: ['classId'] })
```

### 创建

```typescript
// 单条
const created = await client.data.from('Student').create({ name: 'Alice', age: 16 })

// 批量（传入数组自动调用 /batch 端点）
const batch = await client.data.from('Student').create([
  { name: 'Alice', age: 16 },
  { name: 'Bob', age: 17 },
])

// 显式批量创建
const batch2 = await client.data.from('Student').createMany([
  { name: 'Alice', age: 16 },
  { name: 'Bob', age: 17 },
])
```

### 批量更新

每条记录必须包含 `id` 字段：

```typescript
const updated = await client.data.from('Student').updateMany({
  data: [
    { id: 1, name: 'Alicia' },
    { id: 2, name: 'Bob Updated' },
  ],
})
```

### 批量删除

```typescript
const deletedCount = await client.data.from('Student').deleteMany({ ids: [1, 2, 3] })
```

> 批量操作上限为 **200 条**记录，超出将返回 HTTP 400 错误。

### 计数

```typescript
const total = await client.data.from('Student').count({ where: { age: { _gt: 18 } } })
```

## 过滤器 DSL

`where` 选项使用 JSON 过滤器 DSL，直接对应后端 `ConditionOperator`：

### 字段操作符

| 操作符 | 后端 operator | 示例 |
|--------|-------------|------|
| `_eq` | `EQ` | `{ age: { _eq: 18 } }` |
| `_ne` | `NE` | `{ status: { _ne: 'disabled' } }` |
| `_gt` | `GT` | `{ age: { _gt: 15 } }` |
| `_gte` | `GTE` | `{ score: { _gte: 60 } }` |
| `_lt` | `LT` | `{ age: { _lt: 18 } }` |
| `_lte` | `LTE` | `{ price: { _lte: 100 } }` |
| `_in` | `IN` | `{ role: { _in: ['admin', 'user'] } }` |
| `_nin` | `NIN` | `{ status: { _nin: ['deleted'] } }` |
| `_between` | `BETWEEN` | `{ age: { _between: [10, 20] } }` |
| `_contains` | `CONTAINS` | `{ name: { _contains: 'li' } }` |
| `_not_contains` | `NOT_CONTAINS` | `{ bio: { _not_contains: 'spam' } }` |
| `_starts_with` | `STARTS_WITH` | `{ email: { _starts_with: 'a@' } }` |
| `_ends_with` | `ENDS_WITH` | `{ email: { _ends_with: '.com' } }` |

### 逻辑组合

多字段自动 **AND**（并列字段）：

```typescript
{ classId: { _eq: 1 }, age: { _gt: 15 } }
// → 两个条件同时满足
```

显式逻辑操作符：

```typescript
// OR
{ _or: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }] }

// AND
{ _and: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }] }

// 嵌套组合
{
  _or: [
    { _and: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }] },
    { _and: [{ classId: { _eq: 2 } }, { age: { _lt: 12 } }] },
  ]
}
```

### 便捷函数式构造

SDK 提供 `filter-builder` 中的独立函数，用于在代码中动态构建过滤条件：

```typescript
import { filterEq, filterGt, filterOr, filterAnd } from '@flexmodel/sdk'

const where = filterOr(
  filterAnd(filterEq('classId', 1), filterGt('age', 15)),
  filterAnd(filterEq('classId', 2), filterLt('age', 12)),
)

await client.data.from('Student').findMany({ where })
```

## 链式构建器（高级路径）

复杂查询场景可使用 `ModelHandle.query()` 创建链式构建器：

```typescript
const result = await client.data.from('Student').query()
  .eq('age', 18)
  .gt('score', 60)
  .where((f) => f.or(f.eq('classId', 1), f.eq('classId', 2)))
  .orderBy('name')
  .expand('class', 'teacher')
  .page(1, 20)
  .execute()
```

### 链式构建器方法

| 类别 | 方法 | 说明 |
|------|------|------|
| **操作入口** | `.select(...fields)` | 查询（可选投影） |
| | `.insert(data)` | 插入 |
| | `.update(id)` | 全量更新 |
| | `.merge(id)` | 部分更新 |
| | `.delete(id?)` | 删除 |
| | `.count()` | 计数 |
| **过滤器** | `.eq/.ne/.gt/.gte/.lt/.lte/.in/.nin/.between/.contains/.notContains/.startsWith/.endsWith` | 链式追加过滤条件 |
| **逻辑组合** | `.where(fn)` | 函数式复杂条件 |
| | `.filter(raw)` | 原始 filter 对象 |
| **排序** | `.orderBy(field, dir)` | 排序 |
| **关联加载** | `.expand(...fields)` | 关联加载 |
| **分页** | `.page(num, size)` | 分页 |
| **数据设置** | `.set(data)` | update/merge 的数据 |
| **终端方法** | `.execute()` | 执行，返回取决于操作类型 |
| | `.single()` | 获取第一条，无匹配返回 null |

### where() 回调

链式构建器中的 `where()` 接收 `FilterFn` 对象，提供与 `filter-builder` 相同的函数：

```typescript
client.data.from('Student').query().where((f) =>
  f.or(
    f.and(f.eq('classId', 1), f.gt('age', 15)),
    f.and(f.eq('classId', 2), f.lt('age', 12)),
  )
).execute()
```

## 类型安全

通过 `schema<T>()` 获得模型级类型推断：

```typescript
interface Student {
  id: number
  name: string
  age: number
  classId: number
}

interface MySchema {
  Student: Student
}

const db = client.schema<MySchema>()

// db.data.Student 有类型推断
db.data.Student.findMany({ where: { age: { _eq: 18 } } })
db.data.Student.findOne('001')

// db.data.from('Student') 同样有类型推断
db.data.from('Student').findMany({ where: { age: { _eq: 18 } } })
```

不使用 schema 也可用（字段名为 `string`，模型为 `Record<string, unknown>`）：

```typescript
client.data.from('Student').findMany({ where: { age: { _eq: 18 } } })
```

## 错误处理

```typescript
import { FlexmodelApiError, FlexmodelAuthError } from '@flexmodel/sdk'

try {
  await client.data.from('Student').findOne(999)
} catch (err) {
  if (err instanceof FlexmodelApiError) {
    // { status: 404, code: -1, message: 'Record not found', details: ... }
    console.log(err.status, err.code, err.message)
  }
  if (err instanceof FlexmodelAuthError) {
    // API Key 无效或无权限访问该项目
    console.log(err.message)
  }
}
```

错误类层级：
- `FlexmodelError` — 基类
- `FlexmodelApiError` — 业务错误（非 2xx 且非 401）
- `FlexmodelAuthError` — 认证错误（401）

## 跨环境支持

SDK 零外部依赖，仅使用 `fetch` 等标准 API：
- **浏览器** — 直接可用
- **Node.js** — Node 18+ 已内置 fetch
- **Deno** — 直接可用

## 模块结构

```
flexmodel-sdks/
├── src/
│   ├── index.ts              # 主入口，导出所有公共 API
│   ├── client.ts             # FlexmodelClient — 泛型客户端
│   ├── data-namespace.ts     # DataNamespace — Proxy 命名空间
│   ├── model-handle.ts       # ModelHandle — 便捷 CRUD 方法
│   ├── query-builder.ts      # FluentQueryBuilder — 链式构建器（高级路径）
│   ├── filter-builder.ts     # 过滤器构造函数 + FilterFn
│   ├── filter-serializer.ts  # 过滤器/排序序列化
│   ├── types.ts              # 公共类型定义
│   ├── errors.ts             # 错误类
│   ├── http.ts               # HTTP 传输层（fetch wrapper）
│   └── type-helpers.ts       # Schema<T>, RelationToOne<T>, RelationToMany<T>
├── tests/
├── package.json
├── tsconfig.json
└── vitest.config.ts
```

## 开发

```bash
npm install
npm test          # 运行测试
npm run typecheck # 类型检查
npm run build     # 构建
```

## License

MIT
