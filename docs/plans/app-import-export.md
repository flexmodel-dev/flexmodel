# Apps 应用导入导出技术方案

## 一、目标、范围与现状

### 1.1 建设目标

为应用提供 **结构级**整包导入导出能力，支持：

- 将一个应用的完整定义（应用配置、表单、导航、工作台、审批流程、数据权限）打包下载。
- 上传应用包，在本实例中 **创建一个新应用**（新项目 + 新业务库），可选携带记录数据。
- 应用备份、跨环境迁移、模板分发。

feat-131 已交付 **记录级**多表 Excel 导入导出（`/apps/{appId}/transfers`），本方案补齐应用结构级能力，两者互补、互不替代。

### 1.2 已确认的功能范围

| 项目           | 本期约定                                                                    |
|----------------|-----------------------------------------------------------------------------|
| 导出内容       | 结构 + 可选记录数据（按表单勾选）                                           |
| 模型定义       | **不导出 f_model_registry 中的模型**，导入时由表单定义确定性重建（见 §3.2） |
| 导入目标行为   | 仅创建新应用，不做合并，无同名冲突问题                                      |
| 附件对象       | 不复制 Bucket 对象，记录中 URL 原样保留并在预览中提示                       |
| 系统表单       | f_user/f_role/f_department 等不出包、不导入，目标实例天然自带               |
| 讨论/通知/历史 | 不迁移；历史按 feat-134 的 IMPORT 协议记一条汇总事件                        |
| UI 入口        | flexmodel-apps-ui AppSettings 新增「导入导出」分节                          |
| 一致性校验     | 导出前校验表单与模型一致性，发现偏差**直接拦截导出**并返回明细              |
| 包大小上限     | 50MB；数据每表上限 5000 行（同 feat-131 量级）                              |

### 1.3 现状与可复用基础

**应用即项目**：App 与 Project 1:1 对齐（`AppService` 直接包装 `ProjectService`），应用展示属性（iconName/primaryColor）与表单/导航配置存在
`f_project.metadata.app` JSON 中。

各子资源存储位置：

| 子资源   | 存储位置                                         | 重建服务                       |
|----------|--------------------------------------------------|--------------------------------|
| 表单     | `f_project.metadata.app.forms`                   | `AppFormService`               |
| 导航     | `f_project.metadata.app.navigation`              | `AppNavigationService`         |
| 工作台   | 系统库 `f_app_workbench`（project_id 隔离）      | `WorkbenchService`             |
| 审批流程 | 系统库 `f_em_flow_definition`（project_id 隔离） | `AppApprovalFlowService`       |
| 数据权限 | 系统库 f_app_data_authorization 等               | `AppDataAuthorizationService`  |
| 模型     | 项目业务库 `f_model_registry`                    | 由 `createFormWithFields` 重建 |

**关键机制**：`AppFormService.createFormWithFields` 建表单时同步建模型——`buildFormEntity`/`buildField` 构造
`EntityDefinition`（固定主键 `id`、系统字段 created_by/owner_id/updated_by/created_at/updated_at、idx_owner/idx_name 索引），经
`modelingService.createModel` 写 `f_model_registry` 并 DDL 建表，失败补偿 dropModel。表单字段 → 模型列的映射是确定性的，因此
**完整表单定义足以重建等价模型**。

**可复用组件**：

- `AppTransferResource/Service/Store/Executor`：暂存（UUID + 30 分钟 TTL + 定时清理）、multipart 上传、Blob
  下载、批量写入（200/批事务）、异常映射的完整范本。
- `JsonUtils` / `ObjectMapper`（已注册 `FlexmodelCoreModule`）：元模型 JSON round-trip。
- `SessionContext`、`ProjectUnitOfWork`、`ValidationException`/`NotFoundException`。
- MCP `AppTools.create_app_form` 证明了「从零创建应用 + 逐表单重建」路径可行。

---

## 二、应用包格式

zip 容器（`java.util.zip`，无新增依赖），文件扩展名 `.flexmodel.zip`：

```
app-package.zip
├── manifest.json               # 包元信息（见下）
├── app.json                    # 应用配置
├── forms.json                  # 完整表单定义列表
├── navigation.json            # 导航树
├── workbenches.json            # 工作台定义
├── approvals.json              # 审批流程定义
├── data-authorizations.json    # 数据权限规则
└── data/<formName>.json        # 可选：记录数据
```

### 2.1 manifest.json

```json
{
  "format": "flexmodel-app-package",
  "version": 1,
  "exportedAt": "2026-10-30T12:00:00Z",
  "source": {
    "name": "示例应用",
    "description": "...",
    "iconName": "Boxes",
    "primaryColor": "sky",
    "flexmodelVersion": "..."
  },
  "forms": [
    {
      "name": "f_order",
      "label": "订单",
      "fieldCount": 12,
      "dataIncluded": true,
      "dataRows": 1234,
      "dataTruncated": false
    }
  ],
  "warnings": [
    "数据中的用户/部门 ID 来自源实例，导入后可能悬空",
    "附件 URL 指向源应用存储，不会复制对象"
  ]
}
```

### 2.2 forms.json

完整 `AppFormRecord` 序列化（不裁剪），每条含：
`name/label/description/displayField/searchField/fields（含显式 name、type、required、options、referenceForm、config）/layout/viewTabs`。

**必须保留字段显式 name**：导入端不得依赖 label 推导（非 ASCII label 会退化为随机 `field_<uuid12>`），且导航/布局/审批/权限均按字段
name 引用。

### 2.3 data/\<formName\>.json

```json
{
  "total": 1234,
  "truncated": false,
  "rows": [
    {
      "id": "...",
      "...": "..."
    }
  ]
}
```

串行化快照读取（同 feat-131 导出）；超 5000 行截断并置 `truncated: true`。

---

## 三、导出与导入流程

### 3.1 导出

入口：`POST /api/apps/{appId}/package-exports`，请求体：

```json
{
  "includeData": true,
  "dataForms": [
    "f_order",
    "f_order_item"
  ]
}
```

（`dataForms` 缺省为全部表单）

流程：

1. **鉴权**：`SessionContext` 校验应用所有者/管理员（同 feat-131 `AppTransferService` 口径）。
2. **一致性校验（关键守卫，直接拦截）**：比对 `metadata.app.forms` 与 `modelingService.findModels(appId)`，发现以下任一情况返回
   400，响应体附明细， **不生成残缺包**：
  - f_model_registry 中存在无 AppFormRecord 的实体（经建模 REST/MCP/FML/UI 建模页直建的模型——forms-only 重建无法表达
    EnumRef/ModelRef/自定义索引/枚举/NativeQuery，静默导出会产生变形副本）；
  - forms 中存在已被 dropModel 的孤儿 record；
  - 双方字段签名不一致（经建模 REST 改过字段）。
3. **采集**：读取完整 forms records、navigation、workbenches、approvals、data-authorizations；includeData 时按表读取记录。
4. **打包下载**：zip 流式写出，同步返回，`Content-Disposition: attachment; filename="<应用名>.flexmodel.zip"`。

### 3.2 导入（三阶段）

复用 `AppTransferStore` 暂存模式：UUID 文件、30 分钟 TTL、5 分钟定时清理（`flexmodel.transfers.directory` 同体系）。

**① 上传**：`POST /api/app-packages/imports`（multipart）→ 解包校验（manifest 版本、必备文件齐全、forms 字段名合法、包 ≤50MB）→
暂存 → 返回 `uploadId` + 预览：

- 应用摘要、表单清单与字段数、表间引用关系；
- 数据行数与截断标记；
- 固定风险提示：附件 URL、源实例用户/部门 ID 悬空；
- 不做任何写入。

**② 确认**：`POST /api/app-packages/imports/{uploadId}/commit`，请求体 `{ "name": "示例应用-副本" }`（默认 `<源名>-副本`）。

重建顺序（任一步失败 **整体回滚**：删除已建项目与业务库，暂存文件保留供重试，不产生半成品应用）：

```
AppService.createApp(name, iconName, primaryColor)        → 新项目 + 空业务库
按 REFERENCE 依赖拓扑排序表单
  （f_user/f_role/f_department 为系统自带，作 0 层，不出现在包中）
逐个 AppFormService.createFormWithFields(显式字段 name + config)
                                                          → 模型重建（主键/系统字段/
                                                             idx_owner/idx_name 确定性重生成）
逐个 saveFormLayout（子表单晚于父表单）/ viewTabs / updateForm(displayField 等)
AppNavigationService 逐节点重建导航树（保持排序）
WorkbenchService 重建工作台（project_id 替换）
AppApprovalFlowService.save 重建审批定义（project_id 替换，重新部署）
AppDataAuthorizationService 重建数据权限规则（project_id 替换）
可选数据：事务内 200/批写入，主键/引用 ID 原样保留（新库为空无冲突）
每表单记一条 IMPORT 来源的记录历史事件（feat-134 协议）
```

**拓扑排序规则**：REFERENCE 字段的 `referenceForm` 目标必须先建；子表单（layout 中 SUBFORM 项引用
`{formName, referenceField}`）的父表单必须先建；检测到环则 400 拒绝。

**③ 返回**：新 `appId`，前端跳转新应用。

### 3.3 已知限制（预览与文档明示）

| 项                                                        | 处理                                                |
|-----------------------------------------------------------|-----------------------------------------------------|
| 建模入口直建的模型                                        | 导出被一致性校验拦截，明细列出冲突项                |
| 表单模型上后加的索引/唯一约束（经 `/models/{m}/indexes`） | 不导出，重建仅含标准 idx_owner/idx_name             |
| 附件 URL                                                  | 指向源应用 Bucket，不复制对象；预览明示             |
| 数据中用户/部门 ID                                        | 源实例 ID，跨实例导入后悬空；预览明示，本期不做映射 |
| 讨论/通知/记录历史                                        | 不迁移；历史仅记 IMPORT 汇总事件                    |

---

## 四、REST API 设计

```
POST /api/apps/{appId}/package-exports            # 同步导出，返回 zip
POST /api/app-packages/imports                    # multipart 上传，返回 uploadId + 预览
GET  /api/app-packages/imports/{uploadId}         # 重新获取预览
POST /api/app-packages/imports/{uploadId}/commit  # 确认导入，返回新 appId
```

风格遵循 `AppTransferResource`：

- `@Blocking`、`@Tag`/`@Operation` OpenAPI 注解；
- `@RestForm FileUpload` multipart；
- 下载 `Response.ok(bytes, MediaType.APPLICATION_OCTET_STREAM).header("Content-Disposition", ...)`；
- `@ServerExceptionMapper`：`ValidationException`→400、`WebApplicationException`→JSON；
- DTO 为 `record + @RegisterForReflection`。

---

## 五、后端类设计（`dev.flexmodel.apps` 包内）

| 类                    | 职责                                                                        |
|-----------------------|-----------------------------------------------------------------------------|
| `AppPackageResource`  | REST 端点（薄层）                                                           |
| `AppPackageService`   | 编排、鉴权、一致性校验、导出采集、导入预览                                  |
| `AppPackageImport`    | 导入执行器：拓扑排序、逐表单重建、导航/工作台/审批/权限重建、数据写入与回滚 |
| `AppPackageStore`     | 暂存（UUID + TTL + 定时清理，复用 AppTransferStore 体系）                   |
| `dto/AppPackage.java` | 共享协议 record（ExportRequest/ImportPreview/CommitRequest/...）            |

复用不新造：`AppFormService.createFormWithFields/saveFormLayout/updateForm`、`AppNavigationService`、`WorkbenchService`、
`AppApprovalFlowService`、`AppDataAuthorizationService`、`ProjectService`/`AppService`、`ProjectUnitOfWork`、`JsonUtils`、
`ObjectMapper`。

---

## 六、前端设计（flexmodel-apps-ui）

`AppSettings.tsx` 的 `settingsNavs` 新增 `transfer` 分节「导入导出」，两个区块：

- **导出本应用**：开关「包含记录数据」+ 表单勾选列表（含行数提示）→ 鉴权 Blob 下载 zip。
- **从包导入新应用**：上传 → 预览弹窗（应用信息、表单清单、风险提示、可编辑新应用名称）→ 确认 → 成功跳转新应用。

新增 `services/appPackages.ts`（multipart 上传、Blob 下载）。复用 BizDialog 与既有表单组件、语义 token（遵循 DESIGN.md，不新增配色）。

---

## 七、验证计划

| 层       | 内容                                                                                                                                                  |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| 后端测试 | `AppPackageResourceTest`：导出→导入 round-trip（结构+数据比对）、一致性校验拦截、孤儿表单、拓扑排序（子表单/引用链/环）、数据写入、失败回滚、上限截断 |
| 后端构建 | 全模块编译（排除 maven-plugin）+ 规定引擎测试                                                                                                         |
| 前端     | `npm run build`、unit、导入导出 Playwright 专项（含明暗主题）                                                                                         |
| 工件     | `feature_list.json` 新增 feat-141、`progress.md` 记录、本文档链接加入 `flexmodel-server/README.md`                                                    |
