# Apps 应用模板（内置应用包）技术方案

## 一、目标与边界

在「新建应用」中增加「从模板创建」：模板以应用包形式随服务端发布，用户选择模板即走现有整包导入管道创建一个完整应用（表单、布局、导航、工作台、审批、数据权限、可选示例数据）。模板是可复用的脚手架，不是长期同步的蓝图。

本方案 **不新增第二条应用创建实现**。feat-141 的 `AppPackageImport`
本身就是"从包创建一个全新应用"，模板只替换包的来源：从用户上传改为服务端内置资源。`.flexmodel.zip` 协议 v1 保持不变。

包含：

- 模板目录资源布局与启动加载（只读，无数据库表）
- `GET /app-packages/templates` 与 `POST /app-packages/templates/{id}/imports` 两个入口
- 服务端暂存模板包并返回现有 `PreviewResponse`，后续预览/commit/补偿/恢复完全复用
- 前端「从模板创建」入口、模板画廊，复用导入预览弹窗
- 模板规范（身份无关）与内置模板全量校验测试
- Native Image 资源声明

不包含：

- 模板市场、远端模板源、从 URL 拉取模板
- 用户把自定义模板放入服务端目录（普通导入已覆盖此场景）
- 应用跟随模板升级/差异合并
- MCP `create_app` 增加 `templateId`（后续可基于本方案目录扩展）
- 模板独立版本管理、模板使用统计

前提：feat-141 已交付（`AppPackageService` / `AppPackageImport` / `AppPackageValidator` / `AppPackageStore` /
`AppPackageZip` / `AppPackageImportDialog`），本方案不修改其重建、补偿、幂等语义。

## 二、总体设计

```text
UI                          Server                                     资源
GET  /app-packages/templates      AppTemplateCatalog.list()          ← app-templates/index.json
POST /app-packages/templates/{id}/imports
                                  Catalog.find(id)
                                  复制 classpath 包 → Store.stageZip()
                                  AppPackageZip.read() + AppPackageValidator.validate(directory)
                                  Store.savePlan(uploadId, StagedPlan)
                                  → PreviewResponse(uploadId, planHash, ...)
POST /app-packages/imports/{uploadId}/commit        （完全不变）
                                  AppPackageImport.rebuild() → COMMITTED(appId)
```

关键不变式：

1. 模板包与上传包共用读取、校验、重建、补偿全部代码路径，不允许模板专用写库捷径。
2. `uploadId` 仍为服务端随机 UUID，绑定用户、TTL 与 planHash；模板目录不参与暂存文件路径拼接。
3. 校验器始终以目标实例目录为准执行；模板不是"跳过校验"的理由。
4. 模板目录只读、启动后不可变；每个模板条目是运维/开发随制品发布的受信内容，但仍按不可信输入防御性解析。

### 备选方案与否决理由

| 方案                                                          | 结论     | 原因                                                                             |
|---------------------------------------------------------------|----------|----------------------------------------------------------------------------------|
| 模板 zip 作为 apps-ui 静态资源，前端 fetch 后调用现有上传接口 | 否决     | 模板与 UI 构建绑定；每次选择都走一轮网络上传；API/MCP 无法复用；预览仍要传完整包 |
| 新增 `AppTemplateService` 独立解析并逐个调用表单/导航服务     | 否决     | 复制重建逻辑，失去补偿、幂等回执、恢复能力，长期必然漂移                         |
| 模板放入数据库/对象存储并由管理端维护                         | 本期否决 | 引入模板 CRUD、权限、版本管理；「从已有应用导出再导入」已覆盖自定义模板诉求      |
| 服务端内置包 + 复用导入管道                                   | **采用** | 单一重建路径，前后端与 API 三端复用，模板作者流零新工具                          |

## 三、资源布局与模板目录

```text
flexmodel-server/src/main/resources/app-templates/
├── index.json
├── customer-crm.flexmodel.zip
└── order-approval.flexmodel.zip
```

包文件名直接由 `templateId` 派生：`app-templates/<templateId>.flexmodel.zip`，索引中不记录包路径。`index.json`（显式索引，不扫描
classpath 目录，JAR 与 native image 均不可靠列目录）：

```json
{
  "templates": [
    {
      "id": "customer-crm",
      "name": "客户管理",
      "description": "客户、联系人、跟进记录与工作台",
      "category": "销售",
      "iconName": "Users",
      "order": 10
    }
  ]
}
```

字段规则：

- `id`：`^[a-z][a-z0-9-]{1,63}$`，全局唯一；同时是 API 白名单键、包文件名与展示标识。
- `name` / `description` / `category` / `iconName` / `order` 仅用于画廊展示；创建后的应用名称、图标、主色来自包内
  `app.json`（预览步骤允许改名）。
- `iconName` 必须存在于 `flexmodel-apps-ui/src/data/appIcons.ts`，否则前端回退 `Boxes`。

结构取舍：不设 `file` 字段、不建模板子目录。资源路径完全由服务端从合法 id
派生，索引和请求都无法提供任意路径，天然消除了路径穿越面；代价是同一模板只能对应一个包、不能附带旁置文件（截图等）。若未来确需模板封面/长描述等附加资源，再以可选字段扩展，不提前预留。

加载策略（`AppTemplateCatalog`）：

- `StartupEvent` 时读取 index + 每个条目派生的 `<id>.flexmodel.zip`：校验 id、资源存在、包大小 ≤ `MAX_PACKAGE_BYTES`、
  `AppPackageZip.read` 可解析，并执行一次完整语义校验（平台基线目录 + 无系统记录，见
  6.2），通过后才上架并缓存展示摘要（表单数、是否含数据、manifest.exportedAt）。
- index 无法解析：记录 ERROR，目录为空，服务正常启动；单个条目非法：WARN 并跳过该条目。
- 目录不可在运行期修改；新增模板 = 放 zip + 登记 index + 重新构建。
- 不在目录中缓存包字节，避免多模板占用堆内存；每次实例化从 classpath 重新读取。
- 资源读取必须使用 `ClassLoader.getResourceAsStream`，禁止 `Files`/`Path` 直接读：开发模式资源来自 `src/main/resources`/
  `target/classes` 目录，打包后来自 `quarkus-app/app/*.jar` 内的 ZIP 条目，两者只有 classpath 读取方式同时成立。该路径已在开发模式与
  `quarkus-run.jar` 两种形态下实测。

Native Image：

- `application.properties` 增加 `quarkus.native.resources.includes=app-templates/**`。
- 新增 DTO 放在 `dev.flexmodel.apps.dto`，已有 `FlexmodelNativeProcessor` 的 `dev.flexmodel.apps.dto.**` 反射注册自动覆盖。

## 四、接口契约

### 4.1 模板列表

`GET /api/app-packages/templates`

- 已认证用户可访问（与应用创建权限一致），未登录 401。
- 返回 `AppTemplateSummary[]`，目录为空返回 `[]`。
- 不返回包路径、内部源应用 ID 或包内容。

```json
[
  {
    "id": "customer-crm",
    "name": "客户管理",
    "description": "客户、联系人、跟进记录与工作台",
    "category": "销售",
    "iconName": "Users",
    "formCount": 3,
    "includesData": false,
    "exportedAt": "2026-10-30T12:00:00Z"
  }
]
```

### 4.2 从模板创建暂存

`POST /api/app-packages/templates/{templateId}/imports`

- 已认证用户；未知 id → 404「模板不存在」。
- 服务端完成：classpath 读取 → 复制到 `AppPackageStore.stageZip()`（按实际字节计数，超限 413）→ `AppPackageZip.read` →
  `AppPackageValidator.validate(目标目录)` → 生成随机 `uploadId`、保存 `StagedPlan`（TTL 30 分钟）→ 返回既有
  `PreviewResponse`。
- `defaultAppName` 使用模板展示名（或包内 `app.name`）， **不追加「-副本」**；`package.fileName` 使用
  `<templateId>.flexmodel.zip`。
- `PreviewResponse` 增加可空字段 `templateId`（上传路径为 `null`），供 UI 标注来源；其余字段语义不变。
- 校验不通过时按现有规则返回 `INVALID` + issues，不创建应用；内置模板由目录测试保证正常情况下为 `READY`。
- 该接口不幂等：每次调用生成新 `uploadId`，与重复上传语义一致；多余暂存由 30 分钟 TTL 清理。UI 需防重复点击。

### 4.3 既有接口不变

- `GET /api/app-packages/imports/{uploadId}`
- `POST /api/app-packages/imports/{uploadId}/commit`（`{name, planHash}`）

模板实例化返回的 `uploadId/planHash` 直接用于上述接口；commit 后的重建、进度轮询、`COMMITTED` / `FAILED` /
`CLEANUP_REQUIRED`、响应丢失恢复全部沿用 feat-141。

### 4.4 错误映射

| 场景                        | 状态                           |
|-----------------------------|--------------------------------|
| 未登录                      | 401                            |
| 模板 id 不存在              | 404                            |
| 内置包结构非法/引用错误     | 200 + `INVALID`（issues 列出） |
| 包实际大小超限（不应发生）  | 413                            |
| 暂存过期后再查询/提交       | 410                            |
| commit 的 planHash/名称冲突 | 409                            |

## 五、服务端实现

新增/修改类（均在 `dev.flexmodel.apps`）：

| 类                               | 职责                                                                                                                                                            |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AppTemplateCatalog`             | 启动加载 `index.json`、包解析与语义校验、不可变目录、按 id 读取条目；解析失败降级为空目录并告警                                                                 |
| `dto/AppTemplate`                | `AppTemplateSummary` record（`id/name/description/category/iconName/formCount/includesData/exportedAt`）；index 解析的内部 record 独立定义，不放协议 DTO 包之外 |
| `AppTemplateResource`            | `@Path("/app-packages/templates")`、`@Blocking`、OpenAPI `Tag=应用`；鉴权、列表、实例化                                                                         |
| `AppPackageService`              | 新增 `instantiateTemplate(user, template)`；抽取现有 `upload()` 的"校验 + 生成预览 + 保存 plan"为共享私有方法，上传与模板共用                                   |
| `dto/AppPackage.PreviewResponse` | 增加 `String templateId`                                                                                                                                        |
| `application.properties`         | native 资源 include                                                                                                                                             |

实现要点：

- 复制资源时用 `InputStream` 分块写入 `stageZip()`，累计字节超限立即中止并删除临时文件；不把整个包读进 `byte[]`。
- `flexmodel-server/pom.xml` 的 maven-resources-plugin 对 `src/main/resources` 启用了文本过滤，必须在
  `nonFilteredFileExtensions` 中加入 `zip`，否则模板包会被 MalformedInputException 破坏。
- 模板 id 先经目录 Map 查找、再按 `^[a-z][a-z0-9-]{1,63}$` 复校验后派生 classpath 路径；不接受 index 或请求方提供任意路径。
- 保持 `AppPackageService.requireAuthenticatedUser()` 鉴权口径；不新增管理员限制（空白应用创建也不要求管理员）。
- 校验与预览复用 `AppPackageService.preview(...)` 的统计/警告生成逻辑，仅改默认名称与来源标注，避免两套预览结构。
- 不在实例化时写任何应用/项目/模型数据；写操作仍从 commit 开始，保持"预览无副作用"。

## 六、模板规范（作者与校验）

### 6.1 制作流程

1. 在开发实例创建一个演示应用：表单、布局、导航、工作台、审批。
2. 应用设置 → 导入导出 → 仅结构导出（`includeData=false`）；导出本身会执行与导入同源的校验。
3. 将 zip 命名为 `<id>.flexmodel.zip` 放入 `flexmodel-server/src/main/resources/app-templates/`，在 `index.json` 登记同
   id 条目。
4. 运行目录测试（见 6.3）验证可导入性。

### 6.2 内置模板硬性规范

模板会在任意目标实例导入，因此 **只能依赖平台基线身份**。平台种子固定包含 `admin` 用户与 `admin`/`developer`
角色，除此之外的身份一律不可引用：

- 导航 `visibility` 必须为空（公开），不得引用 USER/ROLE/ORGANIZATION。
- 审批执行人只允许 `initiator`/`node`/`variable` 等目录无关来源，或 `roles` 且取值 ⊆ `{admin, developer}`；不得引用
  `users`、`organizations` 或源实例的自定义角色。
- 不包含数据权限规则（`data-authorizations.json` 为空数组）。导入端虽对未知成员降级为警告并跳过，但模板不应依赖该兜底。
- 不引用系统目录表单记录（如 `f_user` 数据）；仅允许结构级 REFERENCE 到系统表单。
- 默认不含记录数据；确需示例数据时在包内提供并确认 `includesData=true`，仍受每表/整包 5000 行与引用闭包限制。示例数据必须
  **零警告**：不得包含表单中不存在的字段、悬空引用、重复主键、必填缺失或类型不匹配，否则会被静默忽略或写入降级。
- 示例数据不得包含附件对象依赖：FILE/IMAGE 字段的值必须为空（源 URL 在目标实例不可访问）。
- 满足既有可移植不变量：`searchField == displayField`、显式表单/字段 name 保留等（导出阶段已阻断）。

### 6.3 目录测试（`AppTemplateCatalogTest`）

不依赖数据库，使用验证器的目录注入入口 `validate(content, directory, lookup)`，目录取平台基线（`admin` 用户、`admin`/
`developer` 角色、无组织）：

- index 解析：正常、未知字段、重复 id、非法 id、条目对应包缺失、损坏 zip、超限包。
- 语义校验：ZIP 可解析但协议版本不支持等语义错误必须在目录加载时被跳过，而不是仅凭 ZIP 可解析上架。
- 每个内置模板：`AppPackageZip.read` 成功、`validate(content, 基线目录, 无系统记录)` **零 issue 且零 warning**。
- 断言审批执行人来源可移植、导航无可见性成员、数据权限为空。
- 断言示例数据不含未知字段与附件字段值。
- 断言 `formCount` / `includesData` 与 manifest、data 文件一致。
- 断言资源查找只接受 `^[a-z][a-z0-9-]{1,63}$` 的 id，非法 id（含 `../`、`/`）不产生任何 classpath 读取。

该测试与启动校验使用同一验证器，是模板质量的唯一闸门：CI 失败即表示某个内置模板在全新实例的基线目录下不可导入。

### 6.4 当前内置模板

`erp-system`（ERP 管理系统）：4 个业务表单，采购订单主子表、物料/供应商基础数据、2 条启用审批、采购工作台与 636
条示例记录。源包来自开发实例导出，入库前完成可移植化：审批执行人改为平台角色 `admin`/`developer`，移除引用源实例用户/组织的
`data-authorizations.json`，删除示例数据中已不存在的字段；后续同源模板按 6.2 处理后可直接登记。

## 七、前端

### 7.1 服务层 `src/services/appTemplates.ts`

```ts
listAppTemplates(): Promise<AppTemplateSummary[]>
instantiateAppTemplate(templateId: string): Promise<AppPackagePreview>  // 复用 appPackages.ts 类型
```

`AppPackagePreview` 增加 `templateId?: string | null`。

### 7.2 入口与交互

- `AppManagement` 工具栏「新建应用」改为二选一入口（下拉菜单或分栏弹窗）： **空白应用** / **从模板创建**；模板目录为空时不显示模板入口。
- 模板画廊弹窗：卡片网格（图标、名称、分类、描述、表单数、「含示例数据」标记），数据来自 `listAppTemplates`；加载失败展示错误与重试，不静默隐藏。
- 选中卡片 → 调用 `instantiateAppTemplate` → 进入 `AppPackageImportDialog` 的 **导入预览**
  步骤（跳过"选择应用包"）：弹窗增加可选入参（如 `templateId` 或 `initialPreview`），内部步骤条从预览开始，其余预览/进度/失败展示逻辑不变。
- 预览中显示来源模板标注（`templateId` 非空）；默认名称为模板名，用户可改；`INVALID` 时禁用确认并展示 issues，与上传导入一致。
- 成功后沿用 `onImported` → 刷新应用列表；网关式错误（401/404/过期）使用现有 `toErrorMessage` 提示。
- 复用 `BizDialog`、现有卡片/空态/警告样式与语义 token；不新增设计语言。

## 八、验证与交付

| 范围     | 必须覆盖                                                                                                                                                                                         |
|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 目录     | index 各类非法输入降级、坏模板不阻断启动、id 白名单、路径穿越拒绝、native 资源声明后可读取                                                                                                       |
| 实例化   | 未登录 401、未知 id 404、返回 READY + planHash、重复调用生成独立 uploadId、暂存 TTL、超限拒绝                                                                                                    |
| 导入链路 | 模板实例化 → commit → 新应用可见、表单/导航/工作台/审批/记录正确；commit 冲突、响应丢失轮询、补偿失败展示沿用 feat-141 用例                                                                      |
| 模板质量 | 每个内置模板空目录零 issue；含示例数据模板的行数与引用校验                                                                                                                                       |
| 前端     | 入口显隐、画廊渲染、预览→改名→提交→成功刷新、INVALID 禁用、暗色/移动端截图                                                                                                                       |
| 构建     | `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'`；`mvn test -pl flexmodel-engine`；`mvn test -pl flexmodel-server -Dtest='AppTemplate*'`；apps-ui `npm run build`；`./init.sh` |

交付时更新 `feature_list.json`（建议 feat-143「Apps - 从模板创建应用」）、`progress.md`，并在 `flexmodel-server/README.md`
增加本文档链接。

## 九、实施步骤

1. `dto/AppTemplate` + `AppTemplateCatalog` + `index.json` 空目录骨架；native 资源声明。
2. `AppPackageService.instantiateTemplate`（抽取共享预览方法）+ `AppTemplateResource` 两个接口。
3. `AppTemplateCatalogTest` 与非法输入用例；准备测试用最小模板包。
4. 前端 `appTemplates.ts`、入口与画廊、`AppPackageImportDialog` 预览直达改造。
5. 制作首个内置模板（如「客户管理」：2–3 个业务表单 + 导航 + 工作台），跑目录测试与端到端冒烟。
6. 文档、feature 状态与验证证据归档。

## 十、风险与后续

| 风险                                 | 影响                           | 应对                                                 |
|--------------------------------------|--------------------------------|------------------------------------------------------|
| 模板身份引用（导航授权、审批执行人） | 其他实例导入直接 INVALID       | 6.2 规范 + 基线目录测试与启动语义校验强制            |
| 内置包随制品发布导致体积膨胀         | 构建/分发变重                  | 单包建议 ≤ 2 MiB、目录建议 ≤ 10 个；超限在测试中拒绝 |
| 坏模板或损坏资源                     | 服务启动失败或入口出现"假模板" | 启动逐个校验并跳过、仅 WARN；测试兜底                |
| native 未包含 zip 资源               | 运行期读取失败                 | `quarkus.native.resources.includes` + 启动校验日志   |
| 模板更新与已创建应用脱节             | 用户预期偏差                   | 明确"一次性脚手架"定位；不做模板升级同步             |
| 暂存文件残留                         | 磁盘占用                       | 复用 30 分钟 TTL 与 5 分钟清理任务，不新增清理逻辑   |

后续可基于同一"服务端包来源"抽象扩展：

- 「复制应用」：服务端导出 → 暂存 → 预览 → commit，流程与本方案一致。
- MCP `create_app` 增加 `templateId`，或在 `list_app_templates` 工具中暴露目录。
- 管理端把当前应用"另存为模板"（导出包 + 下载），仍不触碰服务端目录。
