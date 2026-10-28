# MCP AppTools 应用创建技术方案

> 状态：待实施  
> 关联特性：`feat-042`  
> 目标读者：后端实现者、MCP 客户端接入方、测试与验收人员

## 1. 背景与目标

Flexmodel Apps 已具备应用、表单、菜单和记录的完整服务端能力，但 MCP 侧目前只有 Project、Modeling、Data 和 Function 工具。AI
客户端如果要“一句话创建应用”，需要跨多个 REST 接口编排，容易丢失中间 ID，也不清楚 `fields` 与 `layout.items` 的职责边界。

本方案新增 `AppTools`，把现有 Apps 服务封装成面向 AI 的分步 MCP 工具，按以下固定顺序创建应用：

1. 创建应用
2. 创建表单与字段
3. 保存表单布局
4. 创建应用菜单并绑定表单
5. 创建 mock 数据

核心目标：

- 让 AI 可以按明确顺序创建完整应用，而不需要理解 REST 路由和内部服务装配
- 复用现有 `AppService`、`AppFormService`、`AppMenuService`、`AppRecordService`，不在 MCP 层重复实现业务规则
- 保持 `fields + layout.items` 的扁平契约，不引入嵌套布局或坐标系统
- 让 AI 在每一步拿到可靠的中间结果，特别是表单字段名和记录 ID

## 2. 现状与边界

### 2.1 已有能力

- `AppService.createApp`：创建应用项目，`appId` 与 `projectId` 一致
- `AppFormService.createForm`：创建表单实体和默认布局
- `AppFormService.createField`：创建字段并自动追加 `FIELD` 布局节点
- `AppFormService.saveFormLayout`：保存并强校验扁平 `layout.items`
- `AppMenuService.createMenu`：创建 `GROUP` / `ITEM` 菜单并绑定 `BUILTIN` 或 `FORM`
- `AppRecordService.createRecord`：按表单字段清洗数据并创建记录

### 2.2 设计边界

- **不新增 REST API**：`AppTools` 是 MCP 到既有服务层的薄适配层
- **不做复杂布局**：只支持当前扁平 `layout.items`，后续复杂页面走独立定制能力
- **不做自动 mock 生成**：第一版由 AI 显式传入记录数组，服务端按顺序创建
- **不做跨工具全局事务**：每个工具独立提交；但 `create_app_form` 内部在失败时做尽力清理
- **不绕过权限和校验**：继续使用现有服务端校验，MCP 认证沿用现有 `McpAuthHandler`

## 3. 工具清单

新增类：`flexmodel-server/src/main/java/dev/flexmodel/mcp/AppTools.java`

注入服务：

- `AppService`
- `AppFormService`
- `AppMenuService`
- `AppRecordService`

### 3.1 查询工具

| 工具名           | 入参                | 返回                |
|------------------|---------------------|---------------------|
| `list_apps`      | 无                  | `AppResponse[]`     |
| `get_app`        | `appId`             | `AppResponse`       |
| `list_app_forms` | `appId`             | `AppFormResponse[]` |
| `get_app_form`   | `appId`, `formName` | `AppFormResponse`   |

这些查询工具用于 AI 在创建前检查上下文、在失败后读取当前状态，以及生成 mock 数据前确认字段名。

### 3.2 创建工具

| 工具名                    | 阶段 | 说明                                     |
|---------------------------|------|------------------------------------------|
| `create_app`              | 1    | 创建应用，返回 `appId`                   |
| `create_app_form`         | 2    | 创建表单并批量创建字段，返回最终表单契约 |
| `save_app_form_layout`    | 3    | 保存扁平布局，返回更新后的表单           |
| `create_app_menu`         | 4    | 创建菜单并绑定表单或内置页面             |
| `create_app_mock_records` | 5    | 为单个表单批量创建显式 mock 记录         |

## 4. 工具契约

### 4.1 `create_app`

入参：

- `name`：应用名称，必填
- `description`：应用描述，可选
- `projectId`：应用 ID，可选；未传时由服务端生成
- `iconName`：可选
- `primaryColor`：可选

允许值：

- `iconName`：Lucide 图标名（PascalCase），例如 `ShoppingCart`、`Package`、`Rocket`
- `primaryColor`：小写色名 slug，例如 `blue`、`sky`、`violet`、`gray`；默认 `blue`

返回：完整 `AppResponse` JSON。

示例入参：

```json
{
  "name": "订单管理",
  "description": "订单与客户管理应用",
  "projectId": "order_demo",
  "iconName": "ShoppingCart",
  "primaryColor": "indigo"
}
```

### 4.2 `create_app_form`

入参：

- `appId`
- `label`
- `name`
- `description`
- `createDisplayField`
- `fieldsJson`

`fieldsJson` 是字段数组 JSON 字符串，每项结构与 `AppFormFieldCreateRequest` 对齐：

```json
[
  {
    "label": "客户名称",
    "name": "customer_name",
    "type": "TEXT",
    "required": true
  },
  {
    "label": "订单金额",
    "type": "DECIMAL",
    "required": true
  },
  {
    "label": "客户",
    "type": "REFERENCE",
    "required": false,
    "referenceForm": "customer"
  }
]
```

允许字段类型：

```text
TEXT, NTEXT, PHONE, EMAIL, URL, SERIES, PICKLIST, SELECT,
NUMBER, DECIMAL, DATE, DATETIME, TIME, CLASSIFICATION,
MULTISELECT, TAG, REFERENCE, FILE, IMAGE, AVATAR, BARCODE,
LOCATION, SIGN, BOOL, STATE
```

关键行为：

1. 先调用 `AppFormService.createForm`
2. 按 `fieldsJson` 顺序调用 `AppFormService.createField`
3. AI 在每个字段上生成可选的英文 snake_case `name`，服务端校验后原样保留
4. 未传 `name` 时，服务端优先从 ASCII 标签推导友好字段名，冲突或无法推导时回退随机字段名
5. 返回最终 `AppFormResponse`，其中包含最终字段名和默认布局
6. 如果任一字段创建失败，尽力调用 `deleteForm` 清理已创建表单，再返回错误

**为什么布局必须单独保存**：`AppFormService.generateFieldName` 会生成随机内部字段名，AI 在表单创建前无法预知这些名称。因此
`save_app_form_layout` 必须在拿到 `create_app_form` 返回值之后调用。

### 4.3 `save_app_form_layout`

入参：

- `appId`
- `formName`
- `itemsJson`

`itemsJson` 是扁平有序数组，每项结构与 `AppFormItemRecord` 对齐：

```json
[
  {
    "type": "SECTION",
    "label": "基础信息",
    "width": "FULL"
  },
  {
    "type": "FIELD",
    "name": "name",
    "width": "HALF"
  },
  {
    "type": "FIELD",
    "name": "order_amount",
    "width": "HALF"
  },
  {
    "type": "SUBFORM",
    "label": "订单明细",
    "formName": "order_item",
    "referenceField": "orderId",
    "width": "FULL"
  }
]
```

约束：

- `type` 只允许 `FIELD`、`SECTION`、`SUBFORM`
- `width` 只允许 `QUARTER`、`THIRD`、`HALF`、`TWO_THIRDS`、`THREE_QUARTERS`、`FULL`
- `SECTION` 是全宽分隔标题，不是容器，不携带 `children`
- `FIELD.name` 必须来自 `create_app_form` 或 `get_app_form` 返回的字段名
- 所有业务字段必须出现且只能出现一次
- `SUBFORM` 必须提供 `formName` 和 `referenceField`

返回：更新后的 `AppFormResponse`。

### 4.4 `create_app_menu`

入参：

- `appId`
- `menuType`：`GROUP` 或 `ITEM`
- `name`
- `iconName`
- `parentId`：可选
- `targetType`：`BUILTIN` 或 `FORM`
- `targetId`：内置页面 ID 或表单名

允许菜单图标（Lucide 图标名，PascalCase）：

```text
Home, Package, Rocket, Shield, GraduationCap
```

规则：

- `GROUP` 不绑定 `target`
- `ITEM` 必须绑定 `target`
- 绑定表单时使用 `targetType=FORM`、`targetId=formName`
- 返回完整 `AppMenuResponse`，包含 `id` 和 `pageRoute`

### 4.5 `create_app_mock_records`

入参：

- `appId`
- `formName`
- `recordsJson`
- `mainFormName`：可选
- `mainRecordId`：可选
- `referenceField`：可选

`recordsJson` 是记录数组 JSON 字符串：

```json
[
  {
    "name": "张三",
    "field_ab12cd34ef56": "华东客户"
  },
  {
    "name": "李四",
    "field_ab12cd34ef56": "华南客户"
  }
]
```

行为：

1. 按数组顺序逐条调用 `AppRecordService.createRecord`
2. 每条记录使用表单 `fields` 做服务端清洗和必填校验
3. 可选的 `mainFormName`、`mainRecordId`、`referenceField` 用于子表单上下文
4. 返回创建后的记录数组，便于 AI 继续创建引用记录
5. 如果第 N 条失败，停止后续创建并返回错误信息，已创建记录保留

错误返回示例：

```text
Error: create_app_mock_records failed at record index 2 - 必填字段不能为空: 订单金额
```

## 5. AI 编排顺序

推荐 AI 按以下顺序调用工具：

```text
1. create_app
   ↓ appId
2. create_app_form
   ↓ formName + generated field names
3. save_app_form_layout
   ↓ final form schema
4. create_app_menu
   ↓ menuId
5. create_app_mock_records
   ↓ record IDs
```

如果存在引用或子表单，顺序应调整为：

1. 创建应用
2. 先创建被引用表单
3. 创建引用表单
4. 分别保存布局
5. 创建菜单
6. 先创建父表单 mock 记录
7. 使用父记录 ID 创建子表单 mock 记录

## 6. 错误处理与一致性

- 所有工具沿用现有 MCP 工具风格：成功返回 JSON 字符串，失败返回 `Error: <tool_name> failed - <message>`
- JSON 解析错误直接返回给 AI，不吞异常
- `create_app_form` 在字段创建失败时做尽力清理，避免留下半成品表单
- `create_app_mock_records` 允许部分成功，并在错误信息中给出失败索引
- `save_app_form_layout` 继续复用现有强校验，不额外发明校验规则
- 不做跨工具回滚；AI 应根据查询工具确认状态后重试

## 7. 实现要点

1. 新增 `AppTools`
  - 位置：`flexmodel-server/src/main/java/dev/flexmodel/mcp/AppTools.java`
  - 风格对齐 `ProjectTools` / `DataTools`
  - 注入四个 Apps 服务，不直接访问 Repository
2. JSON 解析
  - 复用 `JsonUtils`
  - `fieldsJson` 解析为 `AppFormFieldCreateRequest[]`
  - `itemsJson` 解析为 `AppFormItemRecord[]`
  - `recordsJson` 解析为 `Map<String, Object>[]`
3. 返回值
  - 查询和创建工具返回 `JsonUtils.toJsonString(...)`
  - 错误信息保持稳定前缀，便于 AI 判断失败原因
4. 工具描述
  - 在 `@Tool` 描述中明确调用顺序、允许枚举和字段名来源
  - 明确 `SECTION` 不是容器
  - 明确 `FIELD.name` 必须来自上一步返回值
5. 文档更新
  - `flexmodel-server/README.md`
  - `flexmodel-website/docs/tutorial/features/mcp-server.md`
  - 相关硬编码工具数量统计改为按 AppTools 更新后的实际数量

## 8. 测试与验收

### 8.1 新增集成测试

新增 `AppToolsTest`，使用 `@QuarkusTest` 和 `SQLiteTestResource`。

用例 1：完整创建链路

1. `create_app`
2. `create_app_form`
3. 校验返回字段名和默认布局
4. `save_app_form_layout`
5. `create_app_menu`
6. `create_app_mock_records`
7. `get_app_form` 验证最终契约
8. 清理测试应用

用例 2：非法布局

- 提交不存在的字段名
- 提交非法宽度
- 提交 `SECTION.children`
- 断言返回错误且原布局未被破坏

用例 3：字段创建失败

- 在 `fieldsJson` 中传入非法字段类型
- 断言返回错误
- 尽力清理后表单不再存在

用例 4：mock 数据失败

- 第二条记录缺少必填字段
- 断言返回失败索引
- 第一条记录保留

### 8.2 验证命令

```bash
mvn test -pl flexmodel-server -am -Dtest=AppToolsTest -Dsurefire.failIfNoSpecifiedTests=false
mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest,AppMenuResourceTest,AppRecordResourceTest -Dsurefire.failIfNoSpecifiedTests=false
mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'
mvn test -pl flexmodel-engine
bash ./init.sh
git diff --check
```

### 8.3 验收标准

- AI 可以按固定顺序创建应用、表单、布局、菜单和 mock 数据
- 表单字段名不要求 AI 预测，由服务端生成并返回
- 布局仅支持扁平 `items`，所有非法结构被服务端拒绝
- 工具返回值包含后续步骤所需的 `appId`、`formName`、字段名、`menuId` 和记录 ID
- 现有 REST 契约不发生破坏性变更
- MCP 文档中的工具分类和数量与实际实现一致

## 9. 实施步骤

1. 新增 `AppTools` 查询工具
2. 实现 `create_app`
3. 实现 `create_app_form`，包含失败清理
4. 实现 `save_app_form_layout`
5. 实现 `create_app_menu`
6. 实现 `create_app_mock_records`
7. 新增 `AppToolsTest`
8. 更新 MCP 文档和工具数量
9. 更新 `progress.md` 与 `feature_list.json`
10. 运行验证命令并记录证据

## 10. 明确不做

- 不做嵌套布局、自由画布、坐标或 `children`
- 不做自动推断 mock 数据
- 不做跨工具全局事务
- 不新增 App 专用 REST API
- 不在 MCP 层重复实现字段、布局、菜单或记录校验
- 不引入新的前端改动
