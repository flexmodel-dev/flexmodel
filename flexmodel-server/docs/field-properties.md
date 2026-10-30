# 应用表单字段属性

参考 [REBUILD 字段文档](https://getrebuild.com/docs/admin/entity/index#字段)，应用表单使用 `fields[].config` 保存类型属性。
属性存在项目的表单 metadata 中，系统表单扩展字段保存在系统表单 metadata 中；不增加引擎字段类型，也不改变既有物理列类型。
管理中心的“字段管理”、表单设计器的“添加字段”和布局中的“修改字段”共用属性编辑器。
布局编辑弹窗区分字段名称与可选的布局显示名称；字段属性保存后立即生效，布局显示名称、填写提示、只读和隐藏等设置
在设计器右上角“保存”后持久化。保存字段属性会保留当前尚未保存的布局草稿；内置字段只能调整布局设置。

| 属性 | 适用类型 | 行为 |
| --- | --- | --- |
| `creatable` / `updatable` | 业务字段 | 默认允许；设为 false 后，表单禁用输入且不提交该字段，服务端拒绝对应阶段的显式写入 |
| `defaultValue` | 文本、电话、邮箱、链接、数字、日期时间、布尔 | 新建且请求未包含字段时使用；即使未布局也生效，显式 null 不触发默认值 |
| `commonValues` | TEXT、NTEXT | 常用输入候选，允许自行输入；最多100项，按本字段规则校验 |
| `maxLength` | 文本、电话、邮箱、链接 | 正整数，不超过物理字段已有容量：TEXT 255、NTEXT 4000，其他512 |
| `pattern` / `validationMessage` | TEXT、NTEXT | Java 正则匹配完整字段值；可自定义错误提示，保存属性前检查表达式是否合法 |
| `allowNegative` | NUMBER、DECIMAL | 默认允许负数，设为 false 后服务端拒绝负数 |
| `decimalPlaces` | DECIMAL | 0–6位；限制写入的小数位数，并用于列表、详情的固定精度显示；空值不设此限制 |
| `thousandsSeparator` / `prefix` / `suffix` | NUMBER、DECIMAL | 列表和详情的千分位、前后缀；前后缀最多20字符，不改变存储值，`%` 后缀不乘100 |
| `defaultNow` | DATE、DATETIME、TIME | 新建时使用当前日期或时间，不能与固定默认值同时配置 |
| `dateFormat` | DATE、DATETIME、TIME | 仅控制显示，输入和接口仍使用标准日期时间格式 |
| `displayStyle` | PICKLIST、SELECT、MULTISELECT | 单选支持 DROPDOWN / RADIO，多选支持 CHECKBOX / DROPDOWN |

`options` 继续作为字段的顶层属性。现在可编辑标识、展示文本与默认选项；单选至多一个默认选项，多选可有多个。
单选保存选项标识，多选使用标识数组；服务端检查选项是否存在。布尔保留“是 / 否 / 空”三态，false、数值0都不会视为空值。

示例：

```json
{
  "label": "金额",
  "type": "DECIMAL",
  "required": true,
  "config": {
    "defaultValue": 0,
    "allowNegative": false,
    "decimalPlaces": 2,
    "thousandsSeparator": true,
    "prefix": "¥",
    "suffix": "元",
    "updatable": true
  }
}
```

字段创建、批量建表和修改均支持 `config`。修改时省略或传入 null 保留已有配置，传 `{}` 清空配置；省略 `options` 保留原选项，传 `[]` 清空选项。
不适用于当前类型的属性会返回400。属性校验发生在创建或修改物理字段之前。

`DATE` 显示格式支持 `yyyy-MM-dd`、`yyyy/MM/dd`、`yyyy-MM`、`yyyy`；`DATETIME` 支持
`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd HH:mm`、`yyyy/MM/dd HH:mm`；`TIME` 支持 `HH:mm:ss`、`HH:mm`。
前端“当前时间”按浏览器本地时间预填；请求未传字段时，后端按服务部署时区补齐。

这些约束作用于 Apps 与系统表单的记录接口。直接使用底层数据接口或 GraphQL 不经过应用表单规则。
该变更不包含 REBUILD 的自动编号规则、公式、富文本、扫码、脱敏、附件类型/数量配置、引用级联和回填等独立能力。

## 附件上传与预览

Apps 的 `FILE`、`IMAGE`、`AVATAR` 布局字段使用上传控件，支持单个或多个文件；图片类字段限制选择图片。
新建、编辑（含子表单）和只读详情共用附件列表，支持图片、PDF、音视频预览及文件下载。

- 使用应用 `projectId` 下的 `default` 存储桶，不存在时通过 `BucketResource` 创建私有桶。
- 对象路径为 `{appId}/{formId}/{uuid}_{originalFilename}`，其中 `formId` 为表单内部标识（`AppForm.name`）。
  路径逐段编码，随机前缀避免同名覆盖，文件名中的逗号编码为 `%2C`，不与多文件值分隔符冲突。
- `PUT /api/projects/{projectId}/buckets/{bucketName}/objects/{path}` 接收 `application/octet-stream`，成功返回
  `{"url":"/api/projects/.../objects/...","path":"appId/formId/file","name":"file"}`。
  `url` 为带 `/api` 前缀的同源相对地址，预览/下载仍使用原有 GET 对象接口及登录鉴权。
- 先上传成功再将返回 URL 写入字段，多文件使用 `url1,url2` 字符串，清空保存为 null。
  上传期间禁止保存及发起流程；部分失败保留成功文件，失败项可重新选择重试。
  关闭表单会中止未完成请求并忽略迟到结果。
- 移除只解除字段中的文件引用，不删除对象，避免影响已保存记录或其他引用。
  上传后取消记录可能留下未引用对象，当前不自动回收。
- 附件类物理字段新建时长度为4000；修改已有附件字段配置时将旧的512长度至少扩到4000。
  已有严格长度数据库的字段需保存一次字段配置以扩容，当前不进行批量数据库迁移。

验证：`npm run test:attachments`；`mvn test -pl flexmodel-server -Dtest=BucketAttachmentResourceTest`。

验证：

```powershell
mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'
mvn test -q -pl flexmodel-engine
mvn test -q -pl flexmodel-server '-Dtest=AppFieldRulesTest,AppRecordServiceTest,AppFormResourceTest#testFieldConfigRoundTripDefaultsAndServerValidation,SystemFormResourceTest' '-Dquarkus.scheduler.enabled=false' '-Dquarkus.log.file.enable=false'
cd flexmodel-apps-ui
npm run build
npm test
npm run test:records
npm run test:fields
```
