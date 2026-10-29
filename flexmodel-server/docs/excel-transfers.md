# 多表 Excel 导入导出

Apps 记录列表提供「导入」「导出」；勾选记录后的工具栏支持按所选记录导出。适用于同一应用、同一 SQL 业务库中的现有业务表单。面向已登录用户开放，沿用当前记录访问范围；数据权限配置尚未接入运行时，本功能不增加行级或字段级授权引擎。

## 操作流程

- **导入**：下载模板或上传普通 `.xlsx` → 选择 Sheet 对应表单 → 配置表头、包内键列和字段 → 校验预览 → 确认导入。Sheet 和列均可忽略。普通文件的表头可选第 1–100 行。
- **导出**：选择多个业务表和字段；当前表支持当前筛选全部结果、全部记录或勾选记录，其他表默认全部。默认包含所选主记录的子表，按需补齐引用的业务记录。
- 导入仅新增，数据库 ID、所有者、创建人和审计时间由服务端生成。唯一约束或任何写入失败会回滚整个数据包，修正后可重试。
- 文件最大 10 MB，最多 20 张业务表、整包 5,000 条数据；每张表最多 200 列。OOXML 解压内容最大 50 MB、最多 1,000 条目。上限包含自动收集的关联记录。

## Excel 数据包

每张表一个 Sheet，`__flexmodel` 隐藏 Sheet 保存 `flexmodel-excel-v1` 标记及每张表的映射；`__说明` 保存填写说明。服务端使用目标表单定义校验所有映射，不会按文件创建表或修改模型。

`__key` 是当前 Sheet 内唯一的包内记录键。导入时以「Sheet＋键」分配新的 UUID，依据实际记录依赖排序并重写关系。支持无环的同表自引用；实际循环引用阻止提交。主子表的普通文本关联列也按逻辑引用重映射，并保留原字段校验。

引用字段提供两列：

| 列 | 语义 |
|---|---|
| 包内键 | 指向指定目标 Sheet 的包内键；目标 Sheet 必须配置键列 |
| 已有 ID | 指向目标库已有记录，预览与提交都会验证其存在 |

每行只能填写其中一列，不会把找不到的包内键回退解释为数据库 ID。人员、角色等系统目录只能使用已有 ID，不参与新增或自动补齐。导出按「表＋记录 ID」去重；沿引用补齐的共享记录不会反向带出其余业务记录。

文本按文本单元格导出以保留前导零和长数字。数字按目标字段转换；布尔值支持 `true/false`、`是/否` 和 `1/0`；日期使用 `yyyy-MM-dd`，时间使用 `HH:mm:ss`，日期时间使用 ISO 本地日期时间；同时支持 Excel 原生日期单元格。多选使用 JSON 数组。平台文件使用选项 ID，普通文件允许唯一匹配的选项标签。公式和错误单元格禁止导入；导出以文本保存 `=` 开头的内容，不执行公式。附件只交换既有地址，不复制文件。

## REST 接口

基础路径：`/api/apps/{appId}/transfers`。仅支持已登录用户；导入任务同时绑定应用和创建者。

| 方法与路径 | 输入／输出 |
|---|---|
| `GET /forms` | 业务表单及逻辑引用信息，包含普通文本子表关联列的 `referenceForm` |
| `POST /templates` | `ExportRequest` → 多 Sheet 空模板 |
| `POST /exports` | `ExportRequest` → Excel 文件 |
| `POST /imports` | multipart 的 `file` 部件 → `uploadId`、Sheet 样例及映射建议 |
| `POST /imports/{uploadId}/preview` | `PreviewRequest` → `importId`、`planHash`、各表数量、前20条预览和错误 |
| `POST /imports/{importId}/commit` | `{ "planHash": "..." }` → `COMMITTED` 及各表新增数量 |
| `GET /imports/{importId}` | 当前预览或已提交结果 |

导出请求示例：

```json
{
  "tables": [{"formName": "orders", "scope": "FILTERED", "search": "采购", "viewTabId": "all"}],
  "includeSubforms": true,
  "includeReferences": false
}
```

`scope` 为 `ALL`、`FILTERED` 或 `IDS`；`IDS` 使用 `recordIds`。`fields` 可指定可见业务字段，省略则使用表单布局字段；必要的主子关系键单独保留。模板包含可创建的业务字段。

预览请求示例（列索引从 0 开始，表头行从 1 开始）：

```json
{
  "sheets": [
    {"sheet":"订单","formName":"orders","headerRow":1,"keyColumn":0,
      "columns":[{"column":1,"field":"name","kind":"VALUE","targetSheet":null}]},
    {"sheet":"明细","formName":"items","headerRow":1,"keyColumn":0,
      "columns":[{"column":1,"field":"name","kind":"VALUE","targetSheet":null},
        {"column":2,"field":"order_id","kind":"KEY","targetSheet":"订单"}]}
  ]
}
```

每个 Sheet 对应不同目标表单；未列出的 Sheet 忽略。映射类型为 `VALUE`、`KEY`、`EXISTING`。`keyColumn` 为 null 时按源行生成包内键，此 Sheet 不可被包内引用。

## 一致性与运行维护

- 导出通过独立串行化事务采集所有表，完成后提交，再生成 Excel。数据源不支持所需隔离级别时明确失败。
- 导入通过 `ProjectUnitOfWork` 共用一个业务事务，按依赖顺序每批最多100条写入。业务数据和 `f_app_transfer_receipt` 提交凭据一同提交。凭据表首次使用时初始化，标记为系统模型并排除在业务表单之外，其变更不广播给业务实时订阅或触发器。
- 相同 `importId` 和 `planHash` 的并发／重复提交返回同一持久化结果。网络错误后重试原请求，不创建新预览；重新预览意味着新的导入操作。
- 提交前重新校验表定义、字段与包外引用。定义变化或引用失效返回409，要求重新预览；无效预览返回400，过期返回410，文件超限返回413，其他用户的任务返回404。
- 暂存文件默认位于 `${java.io.tmpdir}/flexmodel-transfers`，可通过 `flexmodel.transfers.directory` 指定受控持久目录。上传与预览有效期30分钟，每5分钟清理；服务重启后仍可读取有效暂存，已提交结果从业务库凭据读取。多实例部署需使用共享暂存目录或将同一导入会话路由到同一实例。
- 原生镜像构建尚需单独验证 Apache POI/XMLBeans 依赖；本次验收路径为 Java 25 JVM。

专项验证：后端 `AppTransferPlannerTest`、`AppTransferResourceTest`；Apps `npm test`、`npm run build` 和 `npx playwright test --config playwright.transfers.config.ts`。
