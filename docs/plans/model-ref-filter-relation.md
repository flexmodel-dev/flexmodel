# ModelRef Filter 关联技术方案

> 状态：v1 已实现 FML 元数据、SQL join、MongoDB `$lookup`、REST 嵌套展开与懒加载；GraphQL 关联 resolver
> 的统一接入仍在后续迭代中。本文同时作为设计说明与当前实现状态的对照。

## 背景

`ModelRef` 当前只能通过 `localField` / `foreignField` 表达键值关联。这个模型覆盖了一对一、一对多和多对一的核心场景，但缺少两类常见能力：

1. **键关联后的进一步过滤**，例如“班级下的活跃学生”。
2. **不依赖外键的条件关联**，例如“状态为 ACTIVE 且归属当前班级的学生”。

如果继续把这两类需求硬塞进 `localField` / `foreignField`，会导致语义混杂：外键是存储与完整性概念，而过滤是运行时查询概念。因此需要把关联策略显式化，同时保留现有用法。

## 目标

- 在 `@relation` 中引入 `filter` 参数，用于键关联后的附加过滤。
- 支持不提供 `localField` / `foreignField` 的 condition 关联，此时 `filter` 独立承担关联谓词。
- 保证 SQL、MongoDB、GraphQL、codegen 使用同一份关系元数据和过滤 DSL。
- Relation filter 的字段路径与现有查询接口 / GraphQL `where` 的路径风格保持一致，不引入新的 `source.*` / `target.*` 语法。
- 当同一模型存在多个关系字段时，通过关系字段名区分目标路径，避免 `source` / `target` 这类二元角色无法表达多关系的问题。
- 不改变现有 `@relation(localField, foreignField)` 的兼容性。
- 不把 `filter` 实现为原生 SQL 字符串，避免数据库方言绑定和权限绕过。

## 非目标

- 本方案不引入新的物理字段类型。
- 不在 condition 关联上生成外键或 DDL。
- 不在 v1 支持 `cascadeDelete` 与 `filter` 组合。
- 不支持任意 SQL 子查询、聚合表达式或跨库函数。
- 不改变查询接口和 GraphQL `where` 的公开入参结构。

## 总体设计

### 1. 关联策略

内部将 `ModelRefField` 分为两种策略：

| 策略          | 触发条件                                | 语义                    | 存储             |
|---------------|-----------------------------------------|-------------------------|------------------|
| `FOREIGN_KEY` | 同时提供 `localField` 和 `foreignField` | 键关联，`filter` 可选   | 可生成外键/索引  |
| `CONDITION`   | 不提供键字段，只提供 `filter`           | 条件关联，`filter` 必填 | 不生成外键或 DDL |

策略由解析器根据参数自动推断，FML 语法不要求显式声明；UI 可显式选择策略并在提交时固化到元数据。内部元数据保存 `strategy`
，避免各执行器重复判断。

### 2. 语法

#### 键关联 + 附加过滤

```fml
activeStudents: Student[] @relation(
  localField: "id",
  foreignField: "classId",
  filter: {
    "activeStudents.status": { "_eq": "ACTIVE" }
  }
)
```

语义：

- `localField` / `foreignField` 决定 join 条件。
- `filter` 在 join 结果上追加过滤。
- 不改变底层物理关系和完整性约束。

#### 纯条件关联

```fml
activeStudents: Student[] @relation(
  filter: {
    "_and": [
      { "activeStudents.classId": { "_eq": { "_field": "id" } } },
      { "activeStudents.status": { "_eq": "ACTIVE" } }
    ]
  }
)
```

语义：

- `filter` 是完整关联谓词。
- 必须至少包含一个当前表字段与目标模型字段的比较，否则视为无效条件关联。
- 不生成外键、不参与级联删除。

条件也可以反向书写，当前表字段作为条件键、目标字段作为 `_field` 引用：

```fml
activeStudents: Student[] @relation(
  filter: {
    "id": { "_eq": { "_field": "activeStudents.classId" } }
  }
)
```

两种写法语义等价，都表示 `activeStudents.classId = 当前记录.id`。

### 3. Filter DSL

`filter` 与查询接口 / GraphQL `where` 使用同一条件 DSL 与字段路径风格：

- `<relationField>.xxx`：目标模型字段，例如 `activeStudents.status`。
- 裸路径：当前表字段，例如 `id`。
- `{ "_field": "xxx" }`：显式引用当前表字段，避免与普通字符串值混淆。
- 不使用 `source.*` / `target.*` 这类关系专用前缀。

说明：`@relation(filter)` 是模型元数据，目标路径使用关系字段名；查询接口中手动传入的 `join.where` 仍使用现有 join alias
语法，不因本方案改变。

调用方 `join.where` 与模型声明的 `filter` 是叠加关系，不是覆盖关系。SQL join 与 MongoDB `$lookup` 必须同时应用两者；MongoDB
中的手动 `join.where` 继续按目标文档普通匹配条件渲染，不进入 relation filter 的源字段上下文，以保持既有行为。

路径解析规则：

| Filter 路径                              | 归属     | 说明                                                                     |
|------------------------------------------|----------|--------------------------------------------------------------------------|
| `activeStudents.status`                  | 目标模型 | 去掉关系字段前缀后，在 `Student` 上解析 `status`                         |
| `{ "_field": "id" }`                     | 当前模型 | 在当前记录上解析 `id`，执行时替换为具体值或查询变量                      |
| `{ "_field": "activeStudents.classId" }` | 目标模型 | 去掉关系字段前缀后，在 `Student` 上解析 `classId`                        |
| `id` 作为条件键                          | 当前模型 | 只允许与目标字段的 `_field` 引用比较；纯当前表常量条件不是有效的关联谓词 |

当前表字段作为条件键时必须通过 `_field` 指向目标字段，例如 `{ "id": { "_eq": { "_field": "activeStudents.classId" } } }`
。这样可以保证 condition 关联始终是“当前记录 × 目标记录”的关系谓词，而不是只过滤当前表或目标表的单边条件。

v1 支持的操作符：

- `_eq`
- `_ne`
- `_gt`
- `_gte`
- `_lt`
- `_lte`
- `_in`
- `_nin`
- `_between`
- `_contains`
- `_not_contains`
- `_starts_with`
- `_ends_with`
- `_and`
- `_or`

v1 不支持：

- 原生 SQL。
- 跨模型子查询。
- 聚合函数。
- 动态函数调用。

### 4. Cardinality

关系字段的类型决定 cardinality：

- `Student`：单数，期望最多匹配一条。
- `Student[]`：复数，允许匹配 0..n 条。

规则：

- 单数 condition 关联如果匹配多条，直接抛出运行时错误，不做隐式取第一条。
- 复数关系匹配 0 条时返回空数组，不返回目标字段全为 `null` 的占位记录。
- 键关联加 `filter` 不会改变底层 cardinality，但会把匹配范围收窄。
- condition 关联必须显式或通过字段类型推导出 cardinality。

### 5. 生命周期与 DDL

| 组合                       | 结果                                                  |
|----------------------------|-------------------------------------------------------|
| `FOREIGN_KEY`，无 `filter` | 保持现状，可支持 `cascadeDelete`                      |
| `FOREIGN_KEY` + `filter`   | 仍可生成外键/索引，但 `filter` 只影响查询，不影响 DDL |
| `CONDITION`                | 只读派生关系，不生成 DDL，不支持 `cascadeDelete`      |

v1 校验规则：

- `cascadeDelete` 与任何 `filter` 互斥。
- `CONDITION` 关联不能声明 `cascadeDelete`。
- 不允许只提供 `localField` 或只提供 `foreignField`。
- 不允许同时缺失键字段和 `filter`。

### 6. 元数据

`ModelRefField` 需要新增以下内部字段：

```json
{
  "name": "activeStudents",
  "from": "Student",
  "strategy": "CONDITION",
  "localField": null,
  "foreignField": null,
  "filter": {
    "_and": [
      { "activeStudents.classId": { "_eq": { "_field": "id" } } },
      { "activeStudents.status": { "_eq": "ACTIVE" } }
    ]
  },
  "cascadeDelete": false
}
```

兼容性默认值：

- 旧模型缺省 `strategy` 时解析为 `FOREIGN_KEY`。
- 旧模型缺省 `filter` 时解析为 `null`。
- 新增字段均为可选，旧序列化模型可继续加载。

### 7. 解析器扩展

`ModelParser.jj` 已扩展注解对象字面量解析，`filter` 不需要写成 JSON 字符串。实现包含：

1. 在 `AnnotationValue()` 中新增 `MapLiteral()` 分支。
2. 新增 `MapLiteralPair()`，支持 `key: value` 语法。
3. 允许嵌套对象、数组和基础字面量。
4. 在 `ASTNodeConverter` 中把 `relation.parameters.get("filter")` 转为 `Map<String, Object>` 并写入
   `ModelRefField.filter`。
5. 在 `ASTNodeConverter` 中根据键字段与 `filter` 推断并固化 `strategy`。

解析阶段的组合校验：

- 只提供 `localField` 或只提供 `foreignField` 时拒绝。
- `localField` / `foreignField` 与 `filter` 同时缺失时拒绝。
- `cascadeDelete` 与任何 `filter` 组合时拒绝。
- UI 提交的 `filterText` 必须是 JSON 对象，提交前转换为结构化 `filter`。

### 8. 查询翻译

#### SQL

路径翻译规则：

| Relation filter         | SQL 表达式           |
|-------------------------|----------------------|
| `activeStudents.status` | `<joinAlias>.status` |
| `{ "_field": "id" }`    | `<sourceAlias>.id`   |
| `id`（条件键）          | `<sourceAlias>.id`   |

渲染时保留原有查询条件 DSL，SQL 条件渲染器负责加引号和参数绑定；`@relation(filter)` 中的路径先由 `RelationFilterSupport`
替换为实际表别名 / join alias。

`FOREIGN_KEY + filter`：

```sql
JOIN Student activeStudents
  ON activeStudents.classId = f_classes.id
 AND activeStudents.status = :status
```

`CONDITION`：

```sql
JOIN Student activeStudents
  ON activeStudents.classId = f_classes.id
 AND activeStudents.status = :status
```

两者最终 SQL 形态可以相同，差异只在于元数据来源：

- `FOREIGN_KEY` 的 join 条件来自 `localField` / `foreignField`，`filter` 只是附加条件。
- `CONDITION` 的 join 条件完全来自 `filter`。

#### MongoDB

MongoDB 渲染会将关系字段前缀去掉，目标字段渲染为 `$field`；当前表字段渲染为 `$lookup` 的 `let` 变量，例如 `id` 对应 `$$id`。

`FOREIGN_KEY + filter`：

```json
{
  "$lookup": {
    "from": "Student",
    "let": { "id": "$id" },
    "pipeline": [
      { "$match": {
          "$and": [
            { "$expr": { "$eq": ["$classId", "$$id"] } },
            { "status": { "$eq": "ACTIVE" } }
          ]
      }}
    ],
    "as": "activeStudents"
  }
}
```

`CONDITION`：

```json
{
  "$lookup": {
    "from": "Student",
    "let": { "id": "$id" },
    "pipeline": [
      { "$match": {
          "$expr": {
            "$and": [
              { "$eq": ["$classId", "$$id"] },
              { "$eq": ["$status", "ACTIVE"] }
            ]
          }
      }}
    ],
    "as": "activeStudents"
  }
}
```

#### GraphQL

- 生成的关联字段保持现有类型。
- 设计目标是关系 resolver 在查询时自动附加 `filter`，且不改变 GraphQL `where` 入参结构。
- v1 的 GraphQL 关联 resolver 尚未统一复用该逻辑；当前已覆盖 SQL / MongoDB 查询构建、REST 嵌套展开和对象懒加载。
- 单数字段如果底层查询返回多条，直接抛出错误。

#### Codegen

- `FOREIGN_KEY` 关联继续生成强类型实体字段。
- `CONDITION` 关联生成同样的业务字段，但底层为运行时派生关系。
- 生成代码不假设 condition 关联存在物理外键。

### 9. 校验与安全

- 解析阶段已校验键字段、`filter`、`cascadeDelete` 的组合合法性。
- 解析阶段已校验 condition 关联必须至少包含一个当前表字段与目标模型字段的比较谓词；裸路径源字段条件必须通过 `_field`
  指向目标字段。
- `filter` 支持对象字面量与 JSON 字符串；对象字面量中的数字、布尔值保持原始类型，FML 输出可重新解析。
- 懒加载路径与嵌套展开路径中，单数关联匹配多条时均抛出明确错误。
- 租户、权限、数据可见性过滤必须与关系 `filter` 叠加，不能覆盖。
- 生成 SQL 时必须使用参数绑定，禁止拼接字符串。
- 对高频过滤字段提供索引建议；v1 可先输出 warning，不自动创建索引。

### 10. UI 与工具

- 建模表单显式提供 `FOREIGN_KEY` / `CONDITION` 策略选择。
- `FOREIGN_KEY` 必须填写 `localField` / `foreignField`，`filter` 可选；填写 `filter` 时禁用 `cascadeDelete`。
- `CONDITION` 必须填写 `filter`，并隐藏、清空 `localField` / `foreignField` / `cascadeDelete`。
- `filter` 以 JSON 文本编辑，提交前校验必须是 JSON 对象并转换为结构化元数据。
- UI 提示目标字段使用 `<relationField>.<field>`，当前表字段在 `_field` 中直接填写 `<field>`。

## 实施阶段

1. **语法与元数据**
  - 扩展注解对象字面量解析。
  - 新增 `filter`、`strategy` 元数据。
  - 完成解析与校验测试。

2. **执行引擎**
  - SQL 与 MongoDB 查询构建器支持 `filter`。
  - GraphQL resolver 和懒加载路径复用同一翻译逻辑。
  - 增加 cardinality 与安全过滤测试。

3. **生成与兼容**
  - codegen、GraphQL schema、REST 展开行为同步支持。
  - 旧模型回归测试通过。

4. **文档与工具**
  - 更新 FML 用户文档。
  - 在 UI 建模器中提示 condition 关联与索引建议。

## 验收标准

- 旧 `@relation(localField, foreignField)` 模型行为不变。
- `FOREIGN_KEY + filter` 能在 SQL 与 MongoDB 中得到一致的过滤结果。
- `CONDITION` 关联可在 SQL 与 MongoDB 中执行，且不产生 DDL。
- 单数 condition 关联匹配多条时返回明确错误。
- `cascadeDelete` 与 `filter` 组合被 schema 校验拒绝。
- 调用方 `join.where` 不会取消模型声明的 `filter`，两者同时生效。
- UI 新建 / 编辑 ModelRef 时透传 `strategy`、`filter`、`from`、`localField`、`foreignField`、`cascadeDelete` 与 `multiple`。
- 租户/权限过滤与关系 `filter` 同时生效。

## 风险

- **性能**：condition 关联可能产生全表扫描，需要索引建议和查询计划提示。
- **语义复杂度**：过滤条件过多会让模型变成查询视图，应优先引导用户使用外键或显式中间模型。
- **解析器扩展**：对象字面量语法需要与现有注解解析保持兼容。
- **跨库一致性**：MongoDB `$lookup` 与 SQL join 在空值、类型转换和排序语义上存在差异，测试需覆盖这些边界。
