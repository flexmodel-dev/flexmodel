# flexmodel-apps 表单 Schema 重构技术方案

> 状态：已实施。
>
> 本方案将表单定义从「平行 map + 哨兵前缀」重构为「`fields` + `layout.items`」。  
> 本次为破坏性变更，不做历史数据迁移。

## 1. 背景

当前表单定义由两套结构组成：

- `AppFormRecord.fields`：字段定义
- `AppFormLayoutRecord`：布局顺序、宽度、展示属性、子表单配置

布局部分使用四份平行结构：

```json
{
  "fields": ["name", "$DIVIDER$-xxx", "$SUBFORM$-yyy"],
  "widths": { "name": 50 },
  "properties": { "name": { "label": "名称", "required": true } },
  "subforms": { "$SUBFORM$-yyy": { "formName": "child", "referenceField": "parent_id" } }
}
```

这带来几个问题：

1. **键漂移**：`fields`、`widths`、`properties`、`subforms` 必须共享同一批人造 ID。
2. **语义混乱**：`required`、`label` 等字段级属性被放在布局结构里。
3. **哨兵前缀**：`$DIVIDER$` 和 `$SUBFORM$` 把节点类型编码进字符串。
4. **AI 生成不稳定**：模型需要同时维护四张 map 的一致性。

## 2. 目标

重构为：

```text
fields        = 完整字段定义
layout.items  = 有序布局节点，只引用字段名
```

核心原则：

- **字段定义**仍然放在 `fields` / `EntityDefinition`
- **布局节点**只负责顺序、宽度、分组、隐藏、只读、提示和子表单位置
- **不做字段定义与布局的双写**
- **去掉哨兵前缀**
- **去掉 `advanced` 自由扩展区**

## 3. 最终 Schema

### 3.1 `AppFormFieldRecord`（保留）

完整字段定义继续放在 `fields` 中，物理模型仍以 `EntityDefinition` 为准。

```java
public record AppFormFieldRecord(
  String name,
  String label,
  String type,
  boolean required,
  boolean builtin,
  List<AppFormFieldOptionResponse> options,
  String referenceForm
) {}
```

职责：

- 字段名
- 展示名
- 数据类型
- 是否必填
- 是否内置字段
- 选项配置
- 引用表单

### 3.2 `AppFormItemRecord`（新增）

`AppFormItemRecord` 只描述布局节点，不重复字段定义。

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppFormItemRecord(
  String type,          // FIELD | SECTION | SUBFORM
  String name,          // FIELD：引用 fields[].name
  String label,         // SECTION/SUBFORM：展示标题；FIELD：可选覆盖名
  String tip,           // FIELD：提示文案
  String width,     // QUARTER | THIRD | HALF | TWO_THIRDS | THREE_QUARTERS | FULL
  Boolean hidden,       // FIELD：是否隐藏
  Boolean readonly,     // FIELD：是否只读
  String formName,      // SUBFORM：子表单名
  String referenceField // SUBFORM：子表单引用字段
) {}
```

节点类型：

| 类型      | 含义            | 必填字段                     |
|-----------|-----------------|------------------------------|
| `FIELD`   | 引用一个字段    | `name`                       |
| `SECTION` | 分栏 / 分组标题 | `label`                      |
| `SUBFORM` | 子表单区域      | `formName`、`referenceField` |

`width` 使用语义化字符串枚举，而不是数字列数：

| `width`          | 12 栅格列数 | 含义 |
|------------------|------------:|------|
| `QUARTER`        |           3 | 1/4  |
| `THIRD`          |           4 | 1/3  |
| `HALF`           |           6 | 1/2  |
| `TWO_THIRDS`     |           8 | 2/3  |
| `THREE_QUARTERS` |           9 | 3/4  |
| `FULL`           |          12 | 1/1  |

### 3.3 `AppFormLayoutRecord`（改写）

布局只保留一个有序数组：

```java
public record AppFormLayoutRecord(
  List<AppFormItemRecord> items
) {}
```

不再有：

- `fields`
- `widths`
- `properties`
- `subforms`
- `advanced`

### 3.4 `AppFormRecord`（改写）

```java
public record AppFormRecord(
  String name,
  String label,
  String description,
  String displayField,
  String searchField,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<AppFormFieldRecord> fields,
  AppFormLayoutRecord layout
) {}
```

说明：

- `name` 同时作为表单名和实体名
- `fields` 是完整字段定义
- `layout.items` 是布局节点
- `layout` 与 `fields` 存在同一个 `AppFormRecord` 中，避免双写漂移

### 3.5 示例

```json
{
  "name": "order",
  "label": "订单",
  "displayField": "name",
  "searchField": "name",
  "managed": true,
  "fields": [
    { "name": "id", "label": "ID", "type": "TEXT", "required": true, "builtin": true },
    { "name": "name", "label": "订单名称", "type": "TEXT", "required": true },
    { "name": "customer", "label": "客户", "type": "REFERENCE", "required": false, "referenceForm": "customer" }
  ],
  "layout": {
    "items": [
      { "type": "SECTION", "label": "基本信息", "width": "FULL" },
      { "type": "FIELD", "name": "name", "width": "HALF" },
      { "type": "FIELD", "name": "customer", "width": "HALF" },
      { "type": "SECTION", "label": "订单明细", "width": "FULL" },
      {
        "type": "SUBFORM",
        "formName": "order_item",
        "referenceField": "order_id",
        "label": "订单明细",
        "width": "FULL"
      }
    ]
  }
}
```

## 4. 服务端改动

### 4.1 DTO 增删

| 操作 | 文件                                                                                                                  |
|------|-----------------------------------------------------------------------------------------------------------------------|
| 新增 | `dto/AppFormItemRecord.java`                                                                                          |
| 改写 | `dto/AppFormRecord.java`、`dto/AppFormResponse.java`、`dto/AppFormLayoutRecord.java`、`dto/AppFormLayoutRequest.java` |
| 删除 | `dto/AppFormItemPropertiesRecord.java`、`dto/AppFormSubformRecord.java`                                               |
| 保留 | `dto/AppFormFieldRecord.java`、`dto/AppFormFieldResponse.java`、`dto/AppFormFieldOptionResponse.java`                 |

`AppFormResponse` 变为：

```java
public record AppFormResponse(
  String name,
  String label,
  String description,
  String displayField,
  String searchField,
  boolean managed,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<AppFormFieldResponse> fields,
  AppFormLayoutRecord layout
) {}
```

### 4.2 `AppFormService` 改动

新增或改写：

- `saveFormLayout(appId, formName, AppFormLayoutRequest)`
- `normalizeLayoutItems(...)`
- `normalizeLayoutItem(...)`
- `validateSubformItem(...)`
- `defaultLayout(EntityDefinition)`
- `withLayout(AppFormRecord, AppFormLayoutRecord)`
- `findLayoutFieldItem(...)`

删除：

- `FORM_DIVIDER_PREFIX`
- `FORM_SUBFORM_PREFIX`
- `FIELD_WIDTHS`
- `normalizeLayout`
- `normalizeSubforms`
- `removeLayoutField`
- `normalizeWidths`
- `normalizeProperties`
- `formLayouts`
- `persistFormLayouts`

保留并继续使用：

- `findForms`
- `createForm`
- `updateForm`
- `deleteForm`
- `createField`
- `updateField`
- `deleteField`
- `requireForm`
- `toResponse`
- `defaultRecord`

### 4.3 校验规则

| #  | 规则                                                                         | 错误信息                                 |
|----|------------------------------------------------------------------------------|------------------------------------------|
| 1  | `items` 非空且数量不超过 500                                                 | `表单布局项数量不合法`                   |
| 2  | `type` 只允许 `FIELD` / `SECTION` / `SUBFORM`                                | `表单项类型不合法: {type}`               |
| 3  | `FIELD.name` 必须存在于 `fields`，且不能是内置字段                           | `布局字段不存在: {name}`                 |
| 4  | `FIELD.name` 不允许重复                                                      | `布局字段重复: {name}`                   |
| 5  | 每个非内置字段必须在 `layout.items` 中出现一次                               | `字段未上版: {name}`                     |
| 6  | `SECTION.label` 不能为空                                                     | `分栏名称不能为空`                       |
| 7  | `SUBFORM.formName` 必须存在                                                  | `子表单不存在: {formName}`               |
| 8  | `SUBFORM.referenceField` 必须存在于子表单 `fields`                           | `子表单引用字段不合法: {referenceField}` |
| 9  | 同一个 `(formName, referenceField)` 不允许重复                               | `子表单重复`                             |
| 10 | `width` 只允许 `QUARTER / THIRD / HALF / TWO_THIRDS / THREE_QUARTERS / FULL` | `字段宽度类型不合法: {width}`            |
| 11 | `SECTION` 和 `SUBFORM` 不允许携带 `name`                                     | `布局项字段名不合法`                     |
| 12 | `FIELD` 不允许携带 `formName` / `referenceField`                             | `布局项配置不合法`                       |
| 13 | `label` / `tip` 去除首尾空白，空字符串转 `null`                              | —                                        |

### 4.4 端点变化

```http
PUT /apps/{appId}/forms/{formName}/layout
```

请求体：

```json
{
  "items": [
    { "type": "SECTION", "label": "基本信息", "width": "FULL" },
    { "type": "FIELD", "name": "name", "width": "HALF" },
    {
      "type": "SUBFORM",
      "formName": "order_item",
      "referenceField": "order_id",
      "width": "FULL"
    }
  ]
}
```

响应体：

```json
{
  "fields": [],
  "layout": {
    "items": []
  }
}
```

## 5. 前端改动

### 5.1 `appForms.ts`

```ts
export type AppFormItemType = 'FIELD' | 'SECTION' | 'SUBFORM'

export type AppFormItemWidthType =
  | 'QUARTER'
  | 'THIRD'
  | 'HALF'
  | 'TWO_THIRDS'
  | 'THREE_QUARTERS'
  | 'FULL'

export interface AppFormItem {
  type: AppFormItemType
  name?: string
  label?: string
  tip?: string
  width?: AppFormItemWidthType
  hidden?: boolean
  readonly?: boolean
  formName?: string
  referenceField?: string
}

export interface AppFormLayout {
  items: AppFormItem[]
}
```

`AppForm` 增加：

```ts
layout: AppFormLayout
```

保存方法改为：

```ts
saveAppFormLayout(appId, name, layout: AppFormLayout)
```

### 5.2 `form-layout.ts`

建议重命名为 `form-items.ts`。

保留：

```ts
export type FormItemWidthType =
  | 'QUARTER'
  | 'THIRD'
  | 'HALF'
  | 'TWO_THIRDS'
  | 'THREE_QUARTERS'
  | 'FULL'

export const formItemWidthTypeClasses: Record<FormItemWidthType, string> = {
  QUARTER: 'md:col-span-3',
  THIRD: 'md:col-span-4',
  HALF: 'md:col-span-6',
  TWO_THIRDS: 'md:col-span-8',
  THREE_QUARTERS: 'md:col-span-9',
  FULL: 'md:col-span-12',
}
```

删除：

- `$DIVIDER$` / `$SUBFORM$` 前缀
- `StoredFormLayout`
- `StoredCustomField`
- `AppFormItemProperties`
- localStorage 布局缓存

### 5.3 `FormDesign.tsx`

状态收敛为：

```ts
const [items, setItems] = React.useState<AppFormItem[]>([])
```

逻辑：

- 加载时直接读取 `form.layout.items`
- 保存时提交 `{items}`
- `SECTION` 用 `type: 'SECTION'`
- `SUBFORM` 用 `type: 'SUBFORM'`
- 字段引用用 `type: 'FIELD'` + `name`

### 5.4 消费组件

| 组件                 | 改动                                             |
|----------------------|--------------------------------------------------|
| `AppRecordFormModal` | 按 `layout.items` 渲染，字段元数据从 `fields` 取 |
| `AppRecordDetail`    | 按 `layout.items` 分组展示                       |
| `AppSubformRecords`  | 从 `layout.items` 识别 `SUBFORM`                 |
| `RecordList`         | 从 `layout.items` 中取字段顺序                   |

## 6. 测试方案

### 6.1 服务端测试

新增或改写：

- `testFormLayoutValidation`
- `testFormLayoutFieldReference`
- `testFormLayoutSubform`
- `testFormLayoutDuplicateField`
- `testFormLayoutMissingField`
- `testFormLayoutInvalidWidth`
- `testFormLayoutFieldWithoutName`
- `testFormLayoutSubformWithoutFormName`
- `testCreateFormBindsExistingModel`
- `testCreateFormDuplicateRecord`

### 6.2 前端验证

```bash
npx tsc --noEmit
npm run build
```

## 7. 实施顺序

1. 新增 `AppFormItemRecord`
2. 改写 `AppFormLayoutRecord`
3. 改写 `AppFormRecord`
4. 改写 `AppFormService`
5. 改写 `AppFormResource`
6. 更新前端 `appForms.ts`
7. 更新 `FormDesign.tsx`
8. 更新消费组件
9. 补测试并全量验证

## 8. 风险与待决

| # | 风险                                    | 建议                                                |
|---|-----------------------------------------|-----------------------------------------------------|
| 1 | 表单名同时作为实体名，可能撞 SQL 保留字 | 收紧为 `^[a-z][a-z0-9_]{0,63}$`，并加保留字拒绝     |
| 2 | 同名枚举 / 原生查询会与表单冲突         | 创建前先查全部 `SchemaObject`                       |
| 3 | 字段 `required` 变更会触发 DDL          | 沿用现有 `ValidationException` 通道，错误信息需清晰 |
| 4 | `SECTION` / `SUBFORM` 无服务端身份      | 本次接受，按数组位置寻址                            |
| 5 | 旧 metadata 中的 `formLayouts` 残留     | 写入新结构时顺带删除                                |
| 6 | 旧宽度值 `{25,33,...}` 不再合法         | 已接受破坏性变更                                    |

## 9. 后续 MCP 建议

MCP 工具应直接暴露：

- `get_app_form`
- `save_app_form_layout`
- `validate_app_form_layout`

工具描述中明确：

- `layout.items[].name` 必须来自 `fields[].name`
- `width` 只允许 `QUARTER / THIRD / HALF / TWO_THIRDS / THREE_QUARTERS / FULL`
- `SECTION` / `SUBFORM` 不携带字段名
- 子表单必须先存在，再被主表单引用
