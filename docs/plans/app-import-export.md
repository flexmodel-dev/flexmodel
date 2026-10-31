# Apps 应用导入导出技术方案

## 一、目标与边界

导出可重建的应用结构与可选记录，在目标实例创建一个新应用（新项目、新业务库）。支持模板分发和受限迁移；不提供完整灾备、覆盖或合并。应用与项目保持
1:1，配置存储位置沿用现有实现。

本期包含：应用名称/描述/图标/主色、业务表单及字段配置、布局与视图、原始导航树、工作台、managed 审批定义、数据权限规则，以及可选业务记录。

不包含：系统表单及目录成员、附件对象、讨论/通知/旧记录历史、审批实例和部署快照、函数、触发器、Bucket 配置与对象、API
keys、认证提供商及其他项目基础设施。附件 URL 保留源值并提示依赖源环境。建模入口直建的模型不支持；自定义索引/唯一约束不迁移，必须在预览中逐项警告，不能称为等价备份。

仅支持 SQL 业务库。包协议版本仅接受 `version=1`；未知版本明确拒绝，不根据 flexmodelVersion 推测兼容。记录源产品版本仅供诊断。

## 二、包协议

容器使用 ZIP，扩展名 `.flexmodel.zip`，UTF-8 JSON，无新增 ZIP 依赖。

```text
manifest.json
app.json
forms.json
navigation.json
workbenches.json
approvals.json
data-authorizations.json
data/<formName>.json       # 可选
```

除 data 文件外均必需；没有资源时写空数组。拒绝未知文件、重复路径及与 manifest 不一致的数据文件。

### 2.1 manifest.json

```json
{
  "format": "flexmodel-app-package",
  "version": 1,
  "exportedAt": "2026-10-30T12:00:00Z",
  "source": {
    "appId": "source_app",
    "flexmodelVersion": "..."
  },
  "forms": [
    {
      "name": "f_order",
      "fieldCount": 12,
      "dataIncluded": true,
      "dataRows": 100
    }
  ],
  "warnings": []
}
```

manifest 统计与 warnings 仅为导出说明。导入重新统计并校验数据文件，重新生成风险提示，不信任源包的行数、权限结论或提示内容。

### 2.2 各资源结构

| 文件                     | v1 结构与身份规则                                                                                                                                                                |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| app.json                 | `{name, description, iconName, primaryColor}`；不接受 ownerId、projectId、数据库连接或任意 metadata                                                                              |
| forms.json               | `AppFormRecord[]` 的原始 metadata 记录，完整保留显式字段 name、builtin、options、referenceForm、config、layout、viewTabs；时间戳仅源信息，导入重新生成                           |
| navigation.json          | 原始 `AppNavRecord[]`，包含 id/type/name/iconName/parentId/destination/children/visibility；不得使用按当前用户过滤后的响应                                                       |
| workbenches.json         | `[{id, name, description, layout}]`；layout 使用现有工作台布局协议；源 id 只用于映射，目标重新分配                                                                               |
| approvals.json           | `[{id, flowKey, name, description, status, form, definition}]`；使用 managed 编辑态协议，status 为 draft/disabled/enabled；源 revision、deploymentId、编译运行态 metadata 不出包 |
| data-authorizations.json | `[{id, formName, name, enabled, subjects, dataScope, controls}]`；使用现有规则协议，不接受源 projectId；目标重新生成规则 id                                                      |
| data/<formName>.json     | `{rows: [{id, ...业务字段}]}`；包含隐藏业务字段，不使用 Excel 的 exposedFields 投影                                                                                              |

字段与表单显式 name 必须保留，禁止按 label 重生成。协议解析使用受限 DTO 与显式校验，不直接将包对象写入系统表。嵌套配置沿用现有
DTO，并由共享验证器检查所有引用、长度、枚举值及范围。`searchField == displayField` 作为本期可移植不变量；不满足时导出阻断，避免
updateForm 静默覆盖。

### 2.3 限额与引用完整性

压缩文件上限 50 MiB，实际解压累计上限 100 MiB，单 entry 上限 50 MiB，entry 数上限 128。业务表单最多 20 个、每表最多 500
字段、布局项最多 1000、导航节点最多 1000 且深度最多 16、工作台最多 50 个且每台最多 100 组件；审批沿用服务 200 节点限制。JSON
嵌套深度最多 64。所有限额由服务端统一常量约束，导出和导入一致执行。

导出始终包含全部业务表单结构，不提供表单选择。选择包含记录数据时，导出全部业务表单的全部记录。记录每表最多 5000 行，整包累计最多
5000 行（feat-131 是整包上限）。按 id 稳定排序，超限直接拒绝并列出超限明细， **本期不自动截断或只导出部分表单**。校验包内全部业务
REFERENCE 的目标记录存在，源数据已悬空则拒绝，无需递归补齐记录。系统目录引用按下一节处理。

## 三、身份、鉴权与一致性

### 3.1 身份策略

本期不做跨实例身份映射。以下策略在上传预览与 commit 前重复校验：

- 导航 visibility、数据权限 subjects/dataScope、审批执行人中的显式 USER/ROLE/ORGANIZATION 或 users/roles/organizations
  必须在目标目录存在；缺失即阻断提交并列明 JSON path。保留全部配置，不过滤未知成员，不把受限 visibility 清空为公开。
- 业务记录中显式指向系统目录的 REFERENCE 必须在目标目录存在，否则阻断。只按目标 ID 校验存在性，不代表两个实例身份语义一致；预览明确提示操作者核对。
- 新应用归属导入人；业务主键与业务引用 ID 保留。owner_id/created_by/updated_by 归属导入人，created_at/updated_at
  使用导入时间，不保留源审计值。预览固定提示此变化。
- 附件对象不复制，FILE/IMAGE 等源 URL 保留，预览提示源访问依赖。

### 3.2 鉴权与只读采集

导出使用 `AppNavigationService.isOwnerOrAdmin`，不沿用仅检查登录的 `AppTransferService.authorize`
。包服务在管理权限边界内读取原始权限配置，不能因普通数据权限服务仅允许 owner 而误拒管理员。

上传、预览、提交必须是已认证用户，遵循应用创建权限。暂存绑定 userId、expiresAt、包摘要和预览摘要；所有 GET/commit/回执读取校验归属。UUID
不是授权凭据。

导航读取原始 metadata；工作台直接通过 Repository 读取，不能调用可能创建默认工作台的 findWorkbenches。导出不得产生写副作用。

### 3.3 共享验证器与快照

新增 `AppPackageValidator`，导出前、上传预览、commit 前共用，错误返回 `{code, path, message}`。上传时完成包内结构、引用图和目标目录验证，commit
再校验目录与计划有效性。

forms.json 以 raw AppFormRecord 为准，禁止以 findForms 合成字段掩盖 metadata 与模型漂移。读取全部
SchemaObject，按类型和系统保留名识别系统对象；额外业务 Entity、Enum、NativeQuery
等不可表达对象均阻断。由同一表单构造逻辑生成预期模型，比较字段集合、物理类型、长度、精度、nullable、默认值及物理引用目标；忽略注释与字段顺序。自定义索引/唯一约束单独警告，不参与等价性承诺。

历史应用可能早于当前系统字段（如 `owner_id`）或存在孤儿表单。 **仅结构导出（`includeData=false`）时，模型漂移降级为 manifest
warnings，不阻断导出**：包内容完全由 metadata 生成，导入会按 metadata 重建模型，漂移不影响结构包。带数据的导出仍要求 metadata
与真实模型一致并阻断，避免字段缺失导致数据静默丢失。审批分支节点既可由网关 blocks 嵌套表达，也可由扁平连线表达，不强制要求
blocks。

验证全部 form/field/nav/subflow 引用、布局主子关系、视图条件、工作台快捷入口与权限控制明细。系统保留表单名禁止出现在业务
forms 中，防止调用系统表单修改路径。调用 Service 不会自动触发 Resource DTO 校验，因此显式执行 Bean Validation 及业务验证。

导出采集结构与数据时开启业务库快照，并在采集前后核对平台配置修订摘要（含模型签名）；变化则失败，要求重试。平台库与业务库没有跨库快照事务，不承诺跨库严格时间点备份。

## 四、导出流程

`POST /api/apps/{appId}/package-exports`：`{includeData}`。始终导出全部业务表单结构；includeData 缺省为 false，不采集数据；true
时导出全部业务表单的全部记录。不接受 dataForms 参数。

1. 管理权限检查、限额与共享验证。
2. 只读采集全部结构，业务库快照读取全部业务字段及主键，校验全部业务记录引用完整性；统计并生成 manifest。
3. 写受限临时 ZIP，写入过程中累计实际字节，完成后校验压缩大小；超限删除并返回明确错误。全部验证成功后才开始 HTTP
   下载，避免响应已发送后才发现错误。
4. 文件流下载，不以 byte[] 全量驻留内存。文件名移除控制字符、路径分隔符和危险字符，同时提供 ASCII filename 与 UTF-8
   filename*。下载完成/断连清理临时文件，异常残留由定时任务回收。

不提供表单选择或导出前逐表行数查询接口；导入预览的表单数和记录数由包内容重新计算。

## 五、导入流程与恢复

### 5.1 上传与预览

`POST /api/app-packages/imports` multipart → 用户级暂存 → 校验 → 返回
uploadId、planHash、expiresAt、READY/INVALID、摘要、错误和警告。仅写暂存与任务状态，不创建应用。

AppPackageStore 复用暂存模式而非直接用 JSON save 存 ZIP。采用独立目录/后缀，30 分钟 TTL、5 分钟清理；清理覆盖
ZIP、解包数据、计划、失败临时文件与下载文件。禁止清理执行中的任务。目录仅服务进程身份可读写；暂存路径由服务端生成。

ZIP 以实际读取字节计数，不能信任 entry size。拒绝绝对路径、盘符、反斜杠绕过、`..`、重复 entry 和非法文件名；若落盘，规范化结果必须位于任务目录。限制
JSON 深度、数组与字符串尺寸，逐项执行协议和领域校验。源包统计与实际内容不符则 INVALID。

### 5.2 幂等确认

`POST /api/app-packages/imports/{uploadId}/commit`：`{name, planHash}`，名称默认 `<源名>-副本`
，仍执行100字符限制。第一次确认固定规范化请求摘要，后续不同 name/hash 返回409。

任务状态 `READY → COMMITTING → COMMITTED`，失败进入 `COMPENSATING → FAILED/CLEANUP_REQUIRED`
。使用持久任务状态与原子状态切换避免双击、并发及多进程重复执行；成功回执持久保存最终 appId，响应丢失后重试返回同一
appId。COMMITTING 重试返回执行状态，不创建第二个应用。回执保留7天，不能随30分钟上传清理而丢失；COMMITTED 后不再受预览过期影响。

### 5.3 重建顺序

1. 先持久记录目标 projectId 与补偿上下文，再以 `AppCreateRequest` 创建应用（包含
   description/iconName/primaryColor）；目标名称不作为资源唯一身份。导入中应用对列表和普通访问不可见，成功后才激活。需要补充服务内部
   provisioning 状态与访问守卫。
2. **先创建全部表单骨架**：自动生成系统字段。根据源字段是否存在 name 决定 createDisplayField，不能根据 displayField 判断；过滤
   builtin=true 及 name 字段，其他非引用字段按显式 name/config/options 重建。校验 name 的标准物理签名，并在自动创建后恢复允许修改的名称字段配置。
3. **再补全部 REFERENCE 字段**：此时目标表单全部存在，通过 createField 重建。支持自引用及互相引用，不以 REFERENCE 环为由拒绝。业务
   REFERENCE 当前使用字符串列，不要求数据库外键拓扑建表。
4. **最后保存全部布局、viewTabs、displayField**：父布局保存前，子表单及其引用字段必须存在；空布局保留初始空布局，跳过不接受空布局的
   saveFormLayout。非法主子关系和布局循环仍拒绝。
5. 按父节点先行创建导航，保存 oldNavId→newNavId 映射，重写 parentId 与排序引用。工作台创建新 ID，重写 layout.shortcuts 中全部导航
   ID；禁止遗漏映射后继续导入。
6. 只导入 managed 审批：先创建全部草稿建立 oldFlowId→newFlowId 映射，再重写全部子流程 flowId 并保存完整定义。按子流程依赖顺序调用
   status 发布，enabled 恢复启用；draft 保持草稿，disabled 以服务支持的状态转换恢复禁用，必要时先部署再禁用。所有调用使用返回的最新
   revision，部署 ID 重新生成。子流程环、启用父流程依赖未发布子流程等不合法组合在预览阻断。
7. 通过 AppDataAuthorizationService 重建规则，生成新规则 ID；显式成员按目标目录校验。
8. 在一个业务库事务内写全部数据，每批100行。复用 prepareImport 的字段与类型校验，不复用 Excel
   可见字段投影；如现有方法过滤隐藏字段，提取共享的完整业务字段校验入口。拒绝未知字段及重复主键，校验整包业务引用完整性；赋审计字段后保留包内业务
   id，再 insertAll，禁止直接插入原始 JSON。
9. 数据成功提交后，每条记录写 IMPORT 历史事件，operationId=uploadId，沿用 feat-134 的事件键和尽力写入协议；无数据不写历史，不新增表单汇总事件。历史失败不回滚业务。
10. 最终激活应用并持久化 COMMITTED 回执；二者应在同一平台事务完成，之后返回 appId。

### 5.4 补偿与中断恢复

这是跨库、DDL 的 **补偿式操作，不是原子事务**。失败后优先经 `AppService.deleteApp` 清理工作台、数据权限、审批及项目；不能只调用
ProjectService.deleteProject。项目创建中途失败且尚无项目记录时，需依据预先持久化的上下文单独清理 schema、注册项和分支残留。

现有删除方法吞掉 dropSchema 异常，不能以正常返回作为清理成功依据；新增内部可核验的补偿入口，检查平台记录、schema、注册缓存及部署资源确已移除。失败记录
CLEANUP_REQUIRED 与错误明细并后台重试，失败应用持续不可见。重启扫描遗留 COMMITTING/COMPENSATING 任务进行核查和补偿，不盲目重跑
createApp。清理完成才允许原任务重试，成功 appId 的回执不能丢失。

## 六、接口与类设计

```text
POST /api/apps/{appId}/package-exports
POST /api/app-packages/imports
GET  /api/app-packages/imports/{uploadId}
POST /api/app-packages/imports/{uploadId}/commit
```

GET 返回预览或持久任务执行结果。未登录401、无权限403、归属不匹配404、过期410、内容/引用校验400、请求摘要或任务状态冲突409、字节超限413。返回的错误包含
JSON path，避免泄露其他用户暂存内容。

在 `dev.flexmodel.apps` 内实现
AppPackageResource（@Blocking、multipart、文件流、OpenAPI）、AppPackageService（鉴权与编排）、AppPackageValidator（共享验证）、AppPackageImport（重建与补偿）、AppPackageStore（文件暂存）、AppPackageTaskRepository（持久状态与回执）、dto/AppPackage（record +
@RegisterForReflection）。平台表任务状态及 provisioning 状态需要纳入实现，不假设现有服务已经具备。

复用
AppFormService、AppNavigationService、WorkbenchService、AppApprovalFlowService、AppDataAuthorizationService、AppService、ProjectUnitOfWork、ServerSessions、ObjectMapper/JsonUtils。表单预期模型构造与完整字段校验应提取共享入口，避免复制私有逻辑。

## 七、前端

导出放在 AppSettings「导入导出」，同时新增 `/admin/apps/:appId/settings/transfer`
路由。仅提供「包含记录数据」开关与导出按钮，默认仅导出全部业务表单结构；开启后包含全部业务表单数据。下载鉴权文件流，展示资源丢弃警告和超限拒绝规则。

导入入口放在 `/admin/apps` 工具栏，空应用列表同样可用。上传 → 预览（结构、实际记录数、身份错误、附件和审计字段提示、名称）→
确认。INVALID 禁止确认；提交中防重复点击并保留 uploadId/planHash，超时通过 GET 查询状态；COMMITTED 跳转返回
appId，补偿失败明确显示状态，不能宣称已回滚成功。

新增 services/appPackages.ts，复用 BizDialog、既有组件与 DESIGN.md 语义 token。

## 八、验证与交付

| 范围        | 必须覆盖                                                                                                                                                                         |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 表单        | builtin/name 过滤、name 配置与自定义 displayField、空布局、隐藏字段、viewTabs、主子布局、自引用及互相引用、非法布局环、searchField 不变量                                        |
| 结构一致性  | raw metadata 漂移、孤儿表单、额外 Entity/Enum/NativeQuery、物理字段签名变化、自定义索引警告、引用错误 JSON path、采集期间配置变化                                                |
| 导航/工作台 | 管理员导出完整原始导航、导航 ID 映射及 shortcuts 重写、零工作台导出无写副作用                                                                                                    |
| 审批/权限   | managed 范围、子流程 ID 映射与发布顺序、各状态恢复、未知成员阻断、受限 visibility 不得变公开                                                                                     |
| 数据        | 全字段 round-trip（隐藏字段、FILE、MULTISELECT、日期、数值）、业务 id 保留、审计字段归导入人、全部表单结构/全部表单数据导出、源悬空、每表与整包行数/字节超限、每记录 IMPORT 历史 |
| 安全/幂等   | ZIP bomb、路径穿越、重复文件、超深 JSON、保留表单名、伪造统计、权限与跨用户暂存访问、过期、并发 commit、响应丢失、请求参数变化                                                   |
| 恢复        | 每个重建步骤注入失败、创建项目中途失败、dropSchema 失败、进程中断、激活与回执原子性；清理后无工作台/权限/流程/schema 残留                                                        |
| UI          | 无应用可导入、settings 路由可达、无表单选择且包含数据开关生效、INVALID 禁用确认、失败查询恢复、明暗主题 Playwright                                                               |
| 构建        | ./init.sh（全模块编译排除 maven-plugin、规定引擎测试）；后端专项测试；Apps UI build/unit/E2E；部署代理允许50 MiB multipart及合理超时                                             |

交付更新 feature_list.json 的 feat-141、progress.md，并在服务端 README 加入本文档链接。只有实现、专项验证与标准启动验证实际通过后才标记
done。
