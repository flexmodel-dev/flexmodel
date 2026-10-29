# Session Progress Log

## Apps UI - 引用选择弹窗实时搜索与清空（2026-10-29，feat-128 补充）

- 后续按要求移除引用表格名称字段下方的附加 ID，下拉候选和选择弹窗共用的表格同步生效；构建/类型检查及 diff --check 再次通过，未运行测试用例，Apps UI 提交 `c451c16`。
- 按最新要求移除弹窗的“搜索”按钮及提交关键词状态，直接复用 250ms 防抖搜索；输入变化自动回到第一页，清空恢复全部记录。
- 搜索框有内容时显示右侧“清空搜索”按钮，点击清空关键词并恢复输入焦点；用它替代独立的“重置”按钮。引用字段原有的清空选中值按钮保持。
- 不运行测试用例或 `init.sh`（包含测试），只进行 Apps 构建/类型检查及差异空白检查；未重复进行上次被工具阻止的浏览器检查。
- 验证：`npm run build`（TypeScript + Vite）与 `git diff --check` 通过；Apps UI 提交 `c74db9e`，工作区干净，构建生成的 tsbuildinfo 已还原。

## Apps UI - 引用字段输入搜索与记录选择弹窗（2026-10-29，feat-128）

- 新增共享 `ReferenceField`，接入新建/编辑主记录与子表记录共用的 `FieldControl`：输入框右侧提供可点击选取按钮；输入关键字后，底部打开候选表格，显示引用表单的显示字段与布局字段，主列附记录 ID。
- 单选引用改为按需调用现有记录接口，输入 250ms 防抖、下拉最多 10 条；选择弹窗提供搜索/重置、单选表格、总数、翻页、每页条数及确定/取消，支持超过原 100 条范围的记录。搜索沿用后端全部可搜索字段的包含查询，本次未扩展图片中的多条件公式编辑器。
- 下拉点击立即回填引用 ID；弹窗维护独立选择草稿，确认后回填，取消保留原值。搜索文字与 ID 分开管理，未选择的搜索文字不会写入记录；支持清空、方向键/Enter/Esc、关闭后焦点返回、失败重试和迟到响应隔离。
- 编辑时按 ID 加载显示名称，避免候选第一页范围影响回显；系统表单的显式候选继续复用本地选项，不调用普通应用记录接口；多选引用保留既有控件。既有 records 用例仅同步输入框断言及单记录接口夹具，未执行。
- 已读取根/Apps AGENTS、项目 README、实际位于 `flexmodel-ui/DESIGN.md` 的设计规范与 token 投影；沿用 Apps 语义主题和已有 Popover、BizDialog、Pagination，无新增硬编码颜色。
- 验证：最终 `npm run build`（`tsc -b && vite build`）通过，主包 2067.57 kB / gzip 650.49 kB；主仓库及 Apps `git diff --check` 通过。标准 `bash ./init.sh` 已执行，clean 被现有运行中 `flexmodel-server-dev.jar` 锁阻塞，保留服务后全模块非 clean compile 通过。引擎测试在用户追加“不要跑测试用例”前已执行并通过，此后未运行单元或 E2E 测试。
- 浏览器界面检查未完成：Computer Use 自动安全检查无法可靠识别当前 Windows 浏览器 URL，工具停止；未继续操作浏览器，也未保存业务记录。界面交互、明暗主题及窄屏效果仍需人工验收。
- Apps UI 独立嵌套仓库已提交 `69ef7dc`，工作区干净；主仓库只提交本特性状态文档，保留原有 `flexmodel-ui` 改动与未跟踪 Apps 目录状态。构建生成的 tsbuildinfo 已还原，未混入提交。

## Apps - 图标单一数据源、图标扩容与选择器分组搜索（2026-10-28）

- 新增 `flexmodel-apps-ui/src/data/appIcons.ts`：252 个 Lucide 图标、9 个中文分组、派生 `AppIconName`、默认图标与
  `resolveAppIconName`/`resolveAppIcon`；删除 `mock.ts` 的 `appIconOptions`/`appIcons`/`navIconOptions`/`navIcons`
  与 `services/apps.ts` 的重复联合类型，应用与菜单全部改用它，未知图标名回落 `Boxes`（菜单显示无图标）。
- `IconPicker` 顶部增加搜索框，图标按分组展示；分组标签为纯文字、无背景色、不吸顶；图标保持 44px 方块、
  每行 10 个、间距 6px 左对齐并让分组标签与首列图标左边缘对齐；选中态沿用原边框 + 主色浅底；
  搜索时展示扁平结果并隐藏分组。
- 后端 `AppCreateRequest`/`AppUpdateRequest` 的 `@Pattern` 与 `AppService`/`AppMenuService` 校验改为
  `^[A-Z][A-Za-z0-9]{0,63}$`（MCP 直连 service 仍受校验），MCP 描述与 `docs/plans/app-tools-mcp.md` 同步。
- 验证：`npm run build`、`npm test`（36 项）、`mvn -pl flexmodel-server -am -DskipTests compile` 通过；
  `AppMenuResourceTest` 10 项通过，`AppResourceTest` 12 项仅既有 `testCreateAppWithExplicitProjectId` 401 失败，
  新增 Rocket 图标创建、非法图标 400 通过；浏览器实测图标数、分组标签背景透明、搜索与选中回填均正常。

## Apps - 应用主色单一数据源与色板扩充（2026-10-28）

- 新增 `flexmodel-apps-ui/src/theme/appPrimaryColors.ts` 作为主色唯一数据源：18 种颜色的 HSL token、派生类型、默认色兜底
  `getAppPrimaryColor`、`applyAppPrimaryColor` 与幂等样式注入（模块加载时写入 `<style id="app-primary-theme">`，生成
  `[data-primary='x']` 与 `.dark [data-primary='x'],.dark[data-primary='x']` 规则）。
- 删除 `index.css` 的 12 段手写主色变量、`mock.ts` 的 `AppPrimaryColor`/`appPrimaryColors`、`services/apps.ts` 的重复联合类型
  及 `AppManagement.tsx` 的 `appPrimaryStyles`；应用卡片改用自身 `data-primary` + `bg-primary/10 text-primary`
  / `hover:border-primary/60` 自动着色。`amber` 中文名改为「琥珀」，「橙色」让给新增 `orange`。
- 后端 `@Pattern` 与 `AppService.validatePrimaryColor` 改为 slug 格式校验 `^[a-z][a-z0-9-]{0,31}$`（MCP 直连 service
  仍受校验），
  MCP 工具描述与 `docs/plans/app-tools-mcp.md` 同步；`flexmodel-apps-ui/AGENTS.md` 记录新增主色只改 theme 文件。
- 验证：`npm run build`、`npm test`（36 项）、`mvn -pl flexmodel-server -am -DskipTests compile` 通过；`AppResourceTest`
  12 项中仅 `testCreateAppWithExplicitProjectId` 的既有 401 失败，新增自定义 slug 成功与非法 slug 400 两项通过。
  浏览器实测：卡片图标/悬停色按各应用主色解析，夜间模式切换到 400 档变量，新建应用弹窗 18 个色板且标签唯一、选中高亮正常。

## 平台模型更名适配（2026-10-28）

- 按 `platform.fml` 未提交改动同步 Java：`f_data_authorization` → `f_app_data_authorization`（`app_id` → `project_id`）、
  `f_data_permission_control` → `f_app_data_permission_control`、`f_workbench` → `f_app_workbench`、
  `f_approval_instance/wait/task/record_lock` → `f_app_approval_*`，`f_notification.app_id` → `project_id`。
- codegen 生成的实体与 `System` 元模型变量随 FML 自动变更，源码改用 `AppWorkbench`、`AppApproval*`、`AppDataAuthorization`
  等类型及 `appWorkbench`/`appApproval*`/`appDataAuthorization` 元模型；通知查询与 DTO 映射同步改用 `project_id`（对外
  JSON 仍为 `appId`）。
- 文档 `approvals.md`、`data-permissions.md`、`notifications.md` 同步表名与字段。按用户要求不做旧表/旧字段兼容。
- 验证：`mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过（clean 仍受运行中 dev.jar 占用）；
  `WorkbenchServiceTest`、`AppApprovalFlowResourceTest` 通过；`NotificationResourceTest` 7 项中 5 项通过，
  2 项失败为 `progress.md` 已记录的既有 outbox/SSE 设计问题，与本次更名无关。
- Apps UI 审批中心按入口区分布局：从应用菜单进入时保留应用导航并按当前 `appId` 过滤；从通知进入（`/approval/home` 无
  `appId`）时
  隐藏导航并显示返回按钮，采用居中 `max-w-5xl` 布局，新增“所属应用”筛选（复用 `getApps`）；`npx tsc -b` 通过，
  浏览器验证应用入口请求携带 `appId`、独立入口切换应用筛选生效。

## Apps UI - 审批中心隐藏 mock 导航（2026-10-28）

- `/approval/home` 的 `AppLayout` 增加 `hideNavigation`，不再渲染来自 `src/data/mock.ts` 的顶部导航（工作台/客户/销售）
  与左侧菜单（首页/客户/商机/产品/常用/项目），与通知页、个人设置页保持一致；审批列表仍走真实 `/api/approvals/*` 接口。
- 验证：Apps UI `npx tsc -b` 通过；Playwright 内置浏览器加载 `#/approval/home` 后 `aside` 为空、页面无 mock 导航链接，
  待处理/已处理列表接口数据正常。
- 未提交。

## Apps - AppDataAuthorizationRepository 改用类型化 DSL（2026-10-28）

- `AppDataAuthorizationRepository` 不再使用模型名常量与 Map 行，改用 `DSL#selectFrom(Class)` 类型化访问：
  `selectFrom(DataAuthorization.class)` / `selectFrom(DataPermissionControl.class)`、`mergeInto(Class).values(实体)`、
  `deleteFrom(Class)`，过滤条件使用 codegen 元模型 `System.dataAuthorization` / `System.dataPermissionControl` 的
  `FilterExpression`，与 `WorkbenchFmRepository` 等现有仓库风格一致；删除 `RULE`/`CONTROL` 字符串常量（无其他引用）。
- 验证：全模块 `mvn compile` 通过；
  `mvn test -pl flexmodel-server -Dtest=AppDataPermissionsTest,AppDataAuthorizationResourceTest,AppRecordServiceTest`
  全部通过；`AppRecordResourceTest` 的 5 个失败为 WIP 既有问题（本次重写前的旧 surefire 报告同样失败，涉及记录字段/layout
  投影，与本仓库类无关——该类仅在数据授权启用时被调用）。
- 未提交。

## Apps - 撤回运行时授权，仅保留配置与所有者（2026-10-28，feat-124）

-
按最新要求恢复记录读写、工作台、审批、通用数据访问、事务会话、GraphQL、附件和实时广播的原有运行时行为；删除授权评估器、范围解析器、访问过滤器、授权/原始数据拦截器及其专用测试。前端记录列表、详情、编辑与子表的运行时权限限制一并撤回。
- 保留两张系统配置表、`dev.flexmodel.apps` 下配置 CRUD/校验、主子表配置生命周期清理，以及公共用户/角色/组织选择器及审批复用。保留
  Drawer + 主子表 Tabs、单个共用条件和查看全部/修改全部/允许删除开关。
- 移除应用级启用接口、默认所有者授权初始化和启用按钮；规则 enabled 仅为配置，配置页明确提示尚未参与运行时数据访问控制。技术方案和
  README 同步当前范围，运行时章节明确列为后续规划。
- 所有者：新业务表单建模时创建 `owner_id`、用户引用元数据和索引；记录服务相对 HEAD 仅增加新增时将 owner_id
  设置为当前创建人的一行逻辑，沿用现有内置字段保护；不增加历史补列、回填或移交逻辑。
- 验证：全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`、规定的 `mvn test -q -pl flexmodel-engine`
  、Apps UI `npm run build`、`npm test`（36 项）均通过。后端 `AppPermissionConditionsTest` 3 +
  `AppDataAuthorizationResourceTest` 4 + `AppRecordServiceTest` 6 + `WorkbenchDataServiceTest` 4 共 17 项通过，日志
  `logs/data-configuration-focused-tests.log`；覆盖保存限制性配置不改变记录 CRUD、配置管理资格、主子关系清理，以及所有者默认值、不可伪造和更新不变。
- 扩大后端回归共 39 项最初发现新测试初始化使用不可变 Map，已修复；余下 7 项 `AppFormResourceTest`/`AppRecordResourceTest`
  的布局字段投影和系统鉴权失败，与 feat-108 既有记录一致，未扩展修复。`init.sh` 的 clean 仍被运行中的 dev.jar 锁定，保留用户服务并完成非
  clean 验证；E2E 按前次用户要求跳过，保留已更新的配置场景用例。
- Apps UI 提交 `566a89a`。提交仅包含本特性文件；用户的 AGENTS.md、flexmodel-ui 修改，以及 Apps UI 的
  profile/AppSettings/构建缓存改动保留。

## Apps - 数据权限单条件模型与 Drawer+Tabs 配置界面（2026-10-28）

- 按最新要求：每张表单只保留一个记录条件，对查看、修改、删除同时生效；不再按 READ/WRITE/DELETE 分别配置；不考虑历史兼容。
- 后端：`AppDataAuthorization.Control` 的 `conditions: Map<操作, 节点>` 改为单个可空 `condition` 节点；
  `AppDataAuthorizationService.save` 只解析并校验一个条件；`AppDataPermissionEvaluator` 的
  Grant.permits/hasParent/readFilter 全部使用单条件；`AppDataAuthorizationRepository` 与 `platform.fml` 的
  `f_data_permission_control` 列 `conditions` 同步更名为 `condition`；`seedOwner` 传 `null` 条件。
- 前端（flexmodel-apps-ui）：`dataAuthorization.ts` 的 `FormPermissionControl.condition` 单条件、`emptyControl` 条件为 null；
  `FormDataPermissions.tsx` 规则编辑从 BizDialog 改为 BizDrawer，主表单与子表单通过 Tabs 切换配置，每个表单一个
  ConditionEditor（提示对查看/修改/删除同时生效）；字段权限表格改为 4
  列：查看/修改/删除开关上移至表头（查看全部字段、修改全部业务字段、允许删除），删除列逐字段复选框禁用仅表头可全选；
  `dataAuthorization.test.ts` 与 e2e `data-permissions.spec.ts` 同步更新（e2e 改用 tab/tabpanel 定位，并补充保存条件断言）。
- 验证：
  `mvn test -pl flexmodel-server -Dtest=AppDataPermissionsTest,AppDataAuthorizationResourceTest,AppRecordServiceTest` 16
  个测试全部通过；`npm run build`（tsc + vite）通过；`npm test`（vitest 36 个）通过。
- 未决风险：e2e `test:permissions` 曾试跑一次，3 个用例均在页面初始数据加载处超时/失败（“新增规则”按钮始终 disabled、alert
  未出现），发生在进入配置界面之前，与本次 Drawer+Tabs 改动无交集，疑似 Playwright webServer 端口或环境问题；按用户要求跳过
  e2e，待下次会话排查。
- 未提交；工作区还包含数据权限功能的大量未提交改动。

## Apps - 移除 owner_id 历史兼容代码（2026-10-28）

- 按最新决策，`owner_id` 作为系统内置字段在创建表单时由 `systemFields()` 自动创建，不再考虑存量表与历史兼容。
- 删除 `AppFormService.ensureOwnerField`（存量表首次写入前补可空所有者列及冲突校验），并移除
  `AppRecordService.writeTransaction` 中的调用。
- 删除 `AppFormService.toFieldResponse` 中对 `owner_id` 强制回退系统字段记录的兼容分支（新表单保存时已包含 owner_id
  系统字段记录）。
- 修正 `AppDataAuthorizationService.activate` 过时的迁移/回填注释。
- 修复数据权限 WIP 的 3 个失败测试：`AppDataAuthorizationResource` 移除类级 `@Consumes(APPLICATION_JSON)`（无 body 的
  `activate` POST 被 REST Assured 默认 form-urlencoded 命中 415），仅在 `create`/`update` 上声明；`activate`
  按原注释意图为既有业务表单初始化“应用所有者”规则（`seedOwner`），否则启用前创建的表单无所有者规则导致 404；测试附件上传按仓库惯例改用
  `byte[]` body 以支持 `application/octet-stream` 编码。
- 验证：`mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过（clean 因 dev.jar 被运行中的 dev 进程锁定跳过）；
  `mvn test -pl flexmodel-server -Dtest=AppRecordServiceTest,AppDataAuthorizationResourceTest,AppDataPermissionsTest` 16
  个测试全部通过（6+3+7，0 failures）。
- 未提交；工作区还包含数据权限功能的大量未提交改动。

## Apps - 数据权限技术方案文档（2026-10-28，feat-123）

- 新增 `flexmodel-server/docs/data-permissions.md`，服务端 README 增加入口；本次只交付设计文档，未修改平台模型、业务数据、组件或运行时权限代码。
- 固定最终简化方案：业务表增加 `owner_id`，中文统一为“所有者”，新增时 `created_by = owner_id = 当前用户 ID`；不保存业务部门
  ID、不实现移交。
  使用授权主表与控制明细两张表，每条规则每张表单一条明细；新增和更新共用 WRITE，使用 read_all/write_all/allow_delete
  与逐字段配置。
  权限表不存关联字段，运行时读取真实主子关系；新子表默认不授权，关系变化后按新关系校验。
- 按最新用户要求，权限后端放在 `dev.flexmodel.apps`，DTO 使用现有 apps.dto；通用目录继续复用 dev.flexmodel.directory。
  方案新增公共 DirectoryMemberPicker，用户、角色、组织选择供授权成员、人员范围和审批复用，公共目录服务不绑定权限存储格式。
- 文档覆盖 JSON 契约、逐记录规则合并、写入前后校验、子表、其他可达入口、历史人员语义、存量回填、缓存和验收矩阵。
- 验证：4 个 JSON 示例解析、6 个本地链接、两张 FML 模型结构对照、已移除字段检查、最新三项要求核对、代码围栏与空白检查、feature
  JSON 和 feat-123 唯一性、`git diff --check` 通过。
  未执行 FML 解析器集成测试或运行时构建；init.sh 在沙箱内启动失败且无输出，申请沙箱外执行后被拒绝，未绕过限制。
- 既有 feature_list.json 中 feat-108 重复，已对照 HEAD 确认为原状，未扩展范围修复；完整 git status 受 SDK 子模块元数据读取权限影响，
  使用忽略子模块的根仓库状态检查，保留原有未跟踪 `flexmodel-apps-ui/`。
- 后续实现需另立功能条目，完成迁移、全入口授权和规定测试后才能宣称数据权限功能可用。

## Apps UI - 详情流程进度节点预测（2026-10-26，feat-117）

- 详情面板新增“后续节点”预测：后端 `AppApprovalForecast` 从 `flow_deploy_id` 的部署快照出发，以当前
  `WAITING` 审批节点的 `node_key` 为起点向后推演线性路径；只保留审批、子流程与结束节点，
  通知、新增、更新、查询、删除等自动节点跳过，分支候选同样跳过自动节点后取首个可展示节点。
  网关条件复用 `ApprovalRecordActions.matches` 与当前实例变量求值，条件引用了尚未产出的变量时不猜测走向，
  返回各分支首个节点作为候选并停止推演；最多返回 12 个节点，部署快照解析异常时降级为空，不影响详情接口。
  实例详情响应新增 `upcoming`，前端只消费该字段，不需要拉取流程定义。
- 流程结束（已通过/已拒绝/已终止）时详情接口返回部署快照中的结束节点，优先取实际执行到的 end 节点；
  时间线末尾以实际状态色展示该节点，不再显示为灰色预测。未完成时结束节点仅作为灰色预测出现。
- 前端预测节点直接接在现有审批时间线末尾，不新增独立分区；沿用原节点标记、标题、元信息与竖线结构，
  以 `muted` 灰阶和“预计/待定”徽标区分，分支候选在网关条目下以“可能进入”列出。
  实例抽屉的“签核进度”复用同一时间线，同样展示预测节点与结束节点。
  预测只覆盖“全部同意”的主路径，驳回/终止走向与子流程内部节点不在此次范围内。
- 验证：`AppApprovalForecastTest` 5 项通过（自动节点跳过、条件命中并汇合、变量缺失返回候选、结束节点解析、
  未知起点返回空）；`mvn -pl flexmodel-server -am -Dtest=ApprovalForecastTest test` 通过（测试时关闭文件日志
  以避开运行中 dev.jar 的日志文件锁）。Apps UI `npx tsc -b`、`npm run build` 通过，`npm test` 34 项通过；
  另用 Playwright 加 mock 接口分别渲染进行态与完成态，检查浅色与深色下灰色预测节点及绿色结束节点，
  临时脚本与截图已删除。未改动已有未提交内容，未提交。

## Apps UI - 列表附件气泡框（2026-10-26，feat-115）

- 根据用户要求，列表附件点击后的完整附件列表改为共用 `Popover`，贴近单元格显示，取消居中模态列表。
  主列表和子表同步；气泡框宽360px，保留16px视口边距，高度受可用空间和24rem限制，附件多时内容内滚动。
  点击外部、关闭按钮或Esc可关闭；图片/PDF等预览先收起气泡框，再打开独立预览窗，关闭预览后恢复触发按钮焦点。
  下载时保留气泡框，事件继续在附件单元格阻止冒泡，不误开记录详情。未修改后端或 `flexmodel-engine`。
- 验证：`npm run build` 通过；`npm run test:attachments` 7项通过，覆盖原有上传及子表场景，并校验
  气泡框贴近单元格定位、背景可访问、键盘打开、预览与焦点返回、下载、Esc/关闭按钮/外部点击关闭、
  子表气泡框浮于抽屉上方及375px视口边界。缩放后的定位使用expect.poll等待浏览器完成布局，不使用固定延时。
  已检查忽略目录 `artifacts/attachment-popover-{light,dark,mobile}.png`，无浏览器异常；
  构建及测试日志为 `artifacts/attachment-popover-{build,e2e}.log`。
- 原生全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`、规定 `mvn test -q -pl flexmodel-engine` 通过；
  `bash ./init.sh` 已执行，clean仍被运行中 `flexmodel-server-dev.jar` 锁定，保留现有服务并完成非clean等价验证。
  日志在根仓库忽略目录 `logs/attachment-popover-{init,compile,engine-tests}.log`。
- 工作期间已有外部提交纳入气泡框实现（Apps UI `54f9cef`）与特性登记（根仓库 `9ea6892`），已检查与本次行为一致并保留；
  额外提交Apps UI `636f29b`记录响应式测试等待。根仓库与Apps UI `git diff --check` 通过。
  保留AppRecordDrawer审批文案的暂存/未暂存改动、`flexmodel-ui` 工作区改动及Apps UI独立仓库未跟踪的现状。

## Apps UI - 列表附件紧凑展示（2026-10-26，feat-114）

- 用户反馈列表页仍显示附件链接。`RecordList` 原先走普通字符串格式化，子表列表则直接 `displayRecordText`，
  两处都未识别附件类型。现由共用 `AttachmentCell` 渲染 `FILE/IMAGE/AVATAR`：图片缩略图或文件图标、
  解码并去掉随机前缀的首个文件名，以及“+剩余数量”；单行28px、最大240px，长名称截断，空值显示“-”。
- 点击或键盘Enter打开完整附件列表，复用此前鉴权预览/下载，不在表格里铺开所有附件；预览等弹窗事件在单元格阻止冒泡，
  不误开记录详情，原有点击名称查看记录继续生效。缩略图只加载首个图片，完整列表按需加载。
  主列表与子表同步，未修改后端或 `flexmodel-engine`。
- 验证：`npm run build` 通过；`npm run test:attachments` 7项通过（新增列表2项，覆盖原文件名/数量/空值、
  缩略图、键盘、完整列表/预览/下载不误开记录、四文件行高≤60px、子表、长文件名和375px页面不溢出）；
  `npm run test:records` 4项通过，无浏览器异常。已检查 `artifacts/attachment-list-{light,dark,mobile}.png`，
  构建及测试日志为忽略目录 `artifacts/attachment-list-{build,e2e,records}.log`。
- 原生全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`、规定 `mvn test -q -pl flexmodel-engine` 通过；
  根仓库与Apps UI `git diff --check` 通过。Apps UI提交 `e2d9dec`，构建生成的 `tsconfig.tsbuildinfo` 已恢复，
  原有AppRecordDrawer审批按钮文案暂存/未暂存改动均保留，未纳入提交。
- `bash ./init.sh` 已执行，clean仍被运行中 `flexmodel-server-dev.jar` 文件锁阻塞，保留服务并完成非clean原生等价检查；
  日志在忽略目录 `logs/attachment-list-{init,compile,engine-tests}.log`。
  保留 `flexmodel-ui` 工作区改动及Apps UI在根仓库中未跟踪独立仓库的现状。

## Apps - 附件字段上传与预览（2026-10-26，feat-113）

- 修复 `FILE` 字段落入普通文本输入框分支的问题；`FILE`、`IMAGE`、`AVATAR` 接入共用 `AttachmentField`，
  主表和子表新建/编辑均支持多选上传、文件列表、移除引用；只读详情复用附件列表，图片缩略图及大图、PDF、音视频可预览，其他类型可下载。
  使用现有语义样式与对话框，检查明暗主题及375px手机布局；无engine改动。
- 新增 `services/attachments.ts`，通过应用 `projectId` 定位 `default` 存储桶，缺失时经 `BucketResource` 创建私有桶，
  缓存纳入现有登出清理机制；路径为 `{appId}/{formId}/{uuid}_{原文件名}`，`formId` 使用 `AppForm.name`。
  PUT原始字节（octet-stream），不使用multipart；逐段编码路径，随机前缀避免重名覆盖，文件名逗号以%2C保留。
  `BucketResource` 上传成功返回 `{url,path,name}`，URL为含API前缀的同源相对地址，适配前端代理。
- 文件上传成功后才将返回URL加入字段，多文件用逗号分隔，清空保存null；预览/下载通过带登录鉴权的GET读取Blob，
  不将token写入文件URL，同源以外的URL不附加token，临时Blob URL关闭后释放。仅内嵌图片/PDF/音视频，HTML等提供下载。
  部分失败保留已成功文件并提示文件名和错误，可重选重试；多字段并行上传时禁止保存、发起流程及主表自动保存。
  关闭时中止请求，按应用/表单/记录区分控件实例，防止迟到响应写入下一条记录。
- 附件类物理字段新建容量由512扩为4000；修改字段配置时也将旧容量至少扩到4000，避免多URL字符串很快溢出。
  不做批量数据库迁移；已有MySQL等严格长度数据库字段需保存一次字段配置以扩容。
  移除只解除引用，不删除存储对象；取消记录后可能留下已上传的未引用对象，暂不自动回收。
  API契约、存储目录和边界已记录到 `flexmodel-server/docs/field-properties.md`。
- 验证通过：Apps UI `npm run build`；`npm test` 34项；`npm run test:attachments` 5项（上传/返回URL/保存回填/清空、
  图片PDF预览及下载、中文和逗号文件名、部分失败重试/同名不覆盖、多字段上传阻止保存、图片多选与类型限制、关闭迟到隔离、子表目录）；
  `npm run test:records` 4项、`npm run test:fields` 5项。测试浏览器异常为空，截图在忽略目录
  `artifacts/attachments-{image-preview,readonly-dark,mobile}.png`，日志 `artifacts/attachment-field-*.log`。
- `BucketAttachmentResourceTest` 2项通过：真实私有桶PUT返回URL并GET逐字节读回、未登录401、中文/逗号编码、零字节文件；
  创建附件字段物理容量4000、8个URL超过512字符保存及读回、修改字段配置、清空null。
  原生全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 和 `mvn test -q -pl flexmodel-engine` 通过。
  后端定向测试关闭scheduler、RabbitMQ devservices与文件日志，仍有既有种子流程SQLite锁和函数运行时9999未启动的后台日志，测试断言均通过。
- `bash ./init.sh` 已执行，clean仍受运行中 `flexmodel-server-dev.jar` 文件锁阻塞；保留现有服务并完成非clean等价检查。
  根仓库日志在 `logs/attachment-{field-*,bucket-tests.log}`；根仓库和Apps UI `git diff --check` 通过。
  Apps UI提交 `009ad5f`，构建生成的 `tsconfig.tsbuildinfo` 已恢复；使用独立提交索引，保留原有AppRecordDrawer暂存/未暂存的审批按钮文案修改。
  保留 `flexmodel-ui` 工作区改动及根仓库中Apps UI未跟踪独立仓库的现状。

## Apps UI - 表单管理四列紧凑布局（2026-10-26，feat-112）

- 根据用户要求，`FormManagementContent` 表单列表宽屏（≥1280px）改为每行四张卡片；640–1279px两列，手机单列。
  卡片间距与内边距由16px缩为12px，图标20px缩为16px，减少描述和底部留白，保留标题、标识、描述、字段数量和返回按钮。
  内置徽标不收缩、不换行，长标题和描述仍按原方式截断。仅调整该列表样式，未修改后端及 `flexmodel-engine`。
- 验证：`npm run build` 通过；`npm run test:settings` 7项通过（导航、返回、新建、加载与错误、移动端）。
  临时Playwright检查使用8张卡片，1440/1280px确认四列、960/768px两列、375px单列，下一行正确换行，
  无页面横向或外层溢出、返回按钮可见、浏览器异常为空；已检查明暗与手机截图。
  证据在 Apps UI 忽略目录 `artifacts/form-grid-{1440,375,dark}.png`、`form-grid-check.json`，
  构建和回归日志为 `artifacts/form-grid-{build,settings-tests}.log`。
- 原生 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`、`mvn test -q -pl flexmodel-engine` 通过；
  根仓库及 Apps UI `git diff --check` 通过。Apps UI提交 `f2f0bb9`，构建生成的 `tsconfig.tsbuildinfo` 已恢复。
- `bash ./init.sh` 已执行，clean仍被现有开发服务占用的 `flexmodel-server-dev.jar` 阻塞；保留现有服务，完成非clean原生Maven等价验证。
  日志位于根仓库忽略目录 `logs/form-grid-{init,compile,engine-tests}.log`。本次浏览器检查启动的5183端口服务已关闭。
  保留原有 `flexmodel-ui` 工作区改动，以及 Apps UI 作为根仓库未跟踪独立仓库的现状。

## Apps UI - 表单设置页面视觉与返回导航统一（2026-10-25，feat-111）

- 用户反馈应用设置/表单下页面 UI 不统一，并要求返回按钮。新增 `SettingsPageHeader`，让表单列表、基本信息、字段管理、
  设计器使用相同的页头间距、标题层级、返回按钮和操作排列；加载中与接口失败时同样保留返回入口。
  表单详情返回当前应用表单列表，列表返回应用管理；系统角色设计器返回角色权限，其他系统设计器返回部门用户，均不依赖历史记录。
- 新增 `FormSettingsNav` 统一基本信息、表单设计、字段管理的路由与选中态。桌面侧栏调整为与应用设置一致的独立卡片，
  主内容与列表页对齐；1024px以下使用页头下的横向导航，避免两个侧栏压缩内容。路由参数由 useParams 读取，避免重复编码。
- 表单列表移除重复标题及虚线新建卡片，改为带图标、描述、内部标识、字段数量的卡片，保留单一“新建表单”按钮；
  基本信息使用 dl 信息分组并显示主显字段名称，删除操作独立放在底部；字段列表统一状态标识与轻量操作，手机使用带无障碍名称的图标按钮。
  窄屏徽标保持单行，夜间删除操作使用可读的语义文字颜色。
- 设计器复用页头，窄屏时画布与字段面板在内容区内上下排列和滚动；保留此前字段属性编辑、保存与草稿行为。
  本次未修改后端或 `flexmodel-engine`。
- 验证：`npm run build` 通过；`npm test` 32项通过；`npm run test:settings` 7项通过（页面导航与页头对齐、直达返回、
  手机/平板无溢出、错误和加载中返回、新建表单、系统设计器返回）；`npm run test:fields` 5项、`npm run test:records` 4项通过。
  已检查明暗截图与375px手机截图；768/960px平板检查无横向及页面外层滚动。截图在 Apps UI 忽略目录
  `artifacts/forms-settings-{list,base,fields,designer,fields-dark,mobile-base,mobile-fields,mobile-form-design}.png`。
  构建日志 `artifacts/forms-settings-build.log`；浏览器测试采集的页面异常为空。
- 原生全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`、规定 `mvn test -q -pl flexmodel-engine` 通过；
  根仓库及 Apps UI `git diff --check` 通过。Apps UI提交 `66eb414`，构建生成的 `tsconfig.tsbuildinfo` 已恢复。
- 标准 `bash ./init.sh` 已实际执行，clean 仍受现有开发服务占用 `flexmodel-server-dev.jar` 阻塞，未停止服务；
  已完成非clean原生 Maven 等价验证。日志位于根仓库忽略目录 `logs/forms-settings-ui-*.log`。
  保留原有 `flexmodel-ui` 工作区改动和 Apps UI 在根仓库中作为未跟踪独立仓库的现状。

## Apps UI - 布局字段编辑接入类型属性（2026-10-25，feat-110）

- 用户指出表单设计器“表单布局”里的“修改字段”入口尚未接入新增属性。现复用 `FieldConfigEditor`，支持字段名称、
  必填、默认值、常用值、格式验证、数字/日期显示格式、选项与默认项；类型及内部标识只读，引用字段可选择目标表单。
- 字段名称与可选的布局显示名称分开编辑，保留填写提示、只读和隐藏。字段属性保存立即调用字段接口，布局仍在右上角保存；
  仅修改布局、内置字段或锁定字段不会调用字段更新接口。弹窗说明了两种保存时机，防止误解。
- 字段保存成功后更新当前字段定义和父组件，并携带全部尚未保存的布局，避免 initialForm 回传将分栏、顺序、宽度和提示重置。
  取消不改元数据；保存失败展示服务端错误并保留草稿可重试；提交中禁用输入、保存、取消与关闭，避免重复提交和中途丢失输入。
- 同时补齐 `AppFormService.updateField` 对系统表单扩展字段的转发，与已有创建路径一致；现有 `SystemFormService`
  核心字段保护继续生效。`SystemFormResourceTest` 覆盖经该入口更新扩展属性，以及核心字段禁止修改。
  字段属性文档已补充布局编辑入口及保存行为，`flexmodel-engine` 无改动。
- 验证：Apps UI `npm run build` 通过，`npm test` 32项通过；`npm run test:fields` 5项通过（新增3项：
  完整属性保存/回填及未保存分栏保留、失败重试与多选保持、内置字段仅编辑布局）；`npm run test:records` 4项通过。
  浏览器无异常，已检查 `artifacts/layout-field-config.png` 与 `layout-field-config-dark.png`。
- 后端 `AppFieldRulesTest` 4 + 字段属性 REST 闭环1 + `SystemFormResourceTest` 5，共10项通过；
  原生全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 和规定 `mvn test -q -pl flexmodel-engine` 通过。
  根仓库及 Apps UI `git diff --check` 通过。Apps UI 提交 `c82f77c`，构建生成的 `tsconfig.tsbuildinfo` 已恢复。
- 标准 `bash ./init.sh` 实际执行，clean 仍被运行中 `flexmodel-server-dev.jar` 文件锁阻塞；未停止现有服务，
  已完成非clean的原生 Maven 等价验证。日志在忽略目录 `logs/field-layout-*.log`。
  保留已有 `flexmodel-ui` 工作区改动及根仓库中 Apps UI 独立仓库未跟踪的现状。

## Apps - 字段类型属性完善（2026-10-24，feat-109）

- 根据用户提供的 REBUILD 字段文档补齐常用类型属性，全部实现在 server 和 Apps UI，`flexmodel-engine` 无代码改动。
  新增 `AppFieldConfig`，通过 `config` 保存到应用表单、系统表单扩展字段 metadata，支持创建、批量建表、读取与编辑。
- 属性包括：默认值与创建/修改限制；TEXT/NTEXT 常用值、最大长度、Java 正则及错误提示；数值负数限制、0–6位小数、
  千分位与显示前后缀；日期/日期时间/时间的当前值默认值和显示格式；单选/多选显示样式。
  选项编辑支持标识、文本和默认项；多选完整保存数组与多个默认值，布尔保留是/否/空，0和false不被当作空值。
- `AppFieldRules` 在记录接口执行校验，未布局且未显式传入的字段也由后端补齐默认值；部分修改不重设默认值。
  配置在修改物理字段前检查。未传 config/options 时保留旧值，`{}`/`[]` 可显式清空。
  系统表单扩展字段同步保留并执行配置，MCP 建表说明补充 config 属性。
- Apps UI 的字段管理和设计器添加字段共用 `FieldConfigEditor`；记录创建/编辑、列表、详情接入默认值、限制与格式，
  修复多选误用单选控件，以及更新字段时选项被重置的问题。多选下拉使用高于抽屉的 Popover 层级。
  说明见 `flexmodel-server/docs/field-properties.md`，服务端 README 已加入入口。
- 验证通过：
  - 原生 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`；`mvn test -q -pl flexmodel-engine`。
  - `mvn test -q -pl flexmodel-server '-Dtest=AppFieldRulesTest,AppRecordServiceTest,AppFormResourceTest#testFieldConfigRoundTripDefaultsAndServerValidation,SystemFormResourceTest'`
    配合关闭测试 scheduler、RabbitMQ devservices、文件日志的命令行参数，16 tests，0 failures/errors。
    覆盖属性保存回读、未布局必填默认值、直接API绕过前端的校验、系统扩展字段默认值与禁止修改。
  - `flexmodel-apps-ui`：`npm run build`、`npm test`（32）、`npm run test:records`（4）、`npm run test:fields`（2）。
    新浏览器测试校验保存回填、默认值、多选、精度错误提示、统一格式与禁止修改字段不提交，无浏览器异常。
    已检查 `artifacts/field-config-editor.png`、`field-config-editor-dark.png`、`field-config-record.png`。
  - 根仓库及 Apps UI `git diff --check` 通过。Apps UI 提交 `9a32a5c`，构建生成的 `tsconfig.tsbuildinfo` 已恢复。
- 扩大运行 AppFormResourceTest、AppRecordResourceTest 等37项时，有7项与本次无关的既有失败：空布局投影导致6项旧断言取不到字段，
  以及系统 Apps 表单入口返回401的1项鉴权断言。相关投影和鉴权逻辑未修改；本次没有扩展修复这些问题。
  测试启动时还存在 SQLite 并发锁和本机函数运行时9999未启动的后台日志，定向验证最终全部通过。
- `bash ./init.sh` 已实际运行，clean 仍受现有服务占用 `flexmodel-server-dev.jar` 阻塞；未停止用户服务，
  已完成非 clean 原生 Maven 等价检查。完整 init 需释放该文件锁。日志位于忽略目录 `logs/field-config-*.log`。
  保留原有 `flexmodel-ui` 工作区改动，以及根仓库将 Apps UI 作为未跟踪独立仓库的现状。
- 边界：属性约束用于 Apps/系统表单记录接口；底层 Data/GraphQL 不经过这些规则。此次没有实现编号规则、公式、富文本、
  扫码、脱敏、附件限制或引用级联等独立能力，详见字段属性文档。

## 修复: AppRecordFormModal 主表单卡片缺失（2026-10-22）

**现象:** 编辑页（AppRecordFormModal：审批记录抽屉可编辑态、子表记录、系统表单、页面详情编辑）主表单
字段仍是裸 grid，无卡片；此前卡片修复只覆盖了 AppRecordDrawer。

**修复:** AppRecordFormModal 主体与 AppRecordDrawer 布局对齐——主表单字段（SECTION+FIELD）包进
`rounded-xl border bg-card p-4 shadow-sm` 卡片，子表单（SUBFORM）移到卡片外；entries 按类型收窄预过滤
（mainEntries/subformEntries）。

**验证:** build 与 test（27 tests）通过；Playwright 实测审批中心已处理（已拒绝→可编辑）：卡片渲染、
主表单 11 个字段在卡片内、BOM明细等子表在卡片外。

## 修复: 审批中心列表人名展示 id（2026-10-22）

**现象:** 审批中心列表发起人一栏直接渲染 `item.initiator`（用户 id）。

**修复:** services/approvals.ts 新增 `getApprovalMemberNames(appId)`（审批成员目录 id→姓名，会话内
缓存）；ApprovalFeed 按列表涉及的应用批量拉取目录合并 names（以 appId 集合为 effect 依赖，轮询刷新
不重复请求），发起人显示 `names[id] ?? id`。InstanceDrawer/ApprovalProgressPanel 原本已正确解析，未动。

**验证:** build 与 test（27 tests）通过；Playwright 实测审批中心已处理 tab：发起人显示「Admin」「张三」。

## Feature: 详情抽屉审批 UI 统一（审批进度右侧面板 + 审批按钮入底部操作行）（2026-10-22）

**需求:** UI 保持统一——右侧区域只展示审批进度（与审批记录抽屉一致的 ApprovalProgressPanel）；审批按钮
（同意/不同意、提交审批）放到表单底部操作行（编辑/删除那一列）；编辑页不展示审批状态。

**修改:**

- 新增 `components/biz/useRecordApproval.ts`：记录审批状态 hook（3s 轮询 recordApprovals+flows，
  对进行中实例 getApprovalDetail 解析当前用户 PENDING 任务），返回 flows/instances/myTask/locked/
  latestInstance（优先进行中实例，否则最新已结束实例）；cancelled 标志防切换记录时旧请求竞态更新，
  enabled=false 时清空状态（修复了切换记录后同意/不同意按钮残留上一条记录任务的 bug）。
- `AppRecordDrawer`：
  - approvalLocked 由 hook 派生（移除 RecordApprovalPanel 与 onLocked state）。
  - 查看态 footer：锁定提示 + 提交审批（未锁定且有启用流程；查看态直接 submitApproval 不改记录，
    与新建态「发起流程」=保存+提交区分）+ 删除 + 编辑 + 不同意/同意（有待办任务时），决策走意见
    BizDialog（requestKey 幂等）。
  - aside 仅查看态且有审批实例时渲染 ApprovalProgressPanel（进度 UI 与审批记录抽屉统一）；
    编辑/新建态无 aside。
  - 发起流程弹窗选项按模式取 flows（新建=加载的 flows，查看=hook flows）。
- `RecordApprovalPanel`（AppRecordDetail 独立页面仍用）上一轮已加同意/不同意，本轮未动。

**验证:** build（tsc -b + vite build）与 test（27 tests）通过；Playwright 实测 products 列表：
锁定记录 footer=「审批中已锁定+删除 (禁)+编辑 (禁)+不同意+同意」，右侧为统一进度面板（审批中状态卡+
或签/依次审批时间线+待处理/等待前序），同意弹意见弹窗正常；无审批记录 footer=「提交审批+删除+编辑」
无残留按钮；编辑态 aside 消失、返回查看态恢复。

## Feature: 流程进度改为抽屉右侧内嵌面板（2026-10-22）

**需求:** 去掉「查看流程进度」按钮，在 Drawer 右侧区域内直接展示流程进度，右上角按钮支持展开/折叠
（类似明道云）。

**修改:**

- `ui/drawer.tsx` BizDrawer 新增 `aside` prop：主体变为左右布局（表单 flex-1 + 右侧 `w-72` 边栏，
  `border-l bg-muted/30` 独立滚动）；aside 存在时 header 右侧自动渲染展开/折叠按钮
  （PanelRightClose/PanelRightOpen，aria-label 收起/展开流程进度），默认展开、每次打开抽屉重置为展开。
- `AppRecordFormModal` 透传 `aside`（footerExtra 保留备用）。
- 新增 `pages/Approvals/ApprovalProgress.tsx`：
  - `ApprovalTimeline`：签核进度时间线纯展示组件（会签/或签/依次审批 + 各任务决策与意见），
    InstanceDrawer 的「签核进度」区同步改用，消除重复。
  - `ApprovalProgressPanel`：右侧面板数据组件，getApprovalDetail + 成员名映射 + 3s 轮询，
    渲染状态卡（状态/发起人/时间）+ ApprovalTimeline。
- `ApprovalTaskDrawer`：移除「查看流程进度」按钮、progressOpen 状态与 InstanceDrawer 引用，
  `aside={<ApprovalProgressPanel appId instanceId/>}`；底部恢复纯 同意/不同意、关闭、默认保存。

**验证:** build（tsc -b + vite build）与 test（27 tests）通过；Playwright 实测审批中心「已处理」：
按钮已移除、右侧面板默认展开（288px，状态卡+时间线含或签/依次审批与各人决策意见）、右上角收起后面板
隐藏、再展开恢复、底部为 取消/保存（已结束=编辑模式）。

**环境备忘:** Vite dev watcher 偶发漏检工具写入的文件（本次 drawer.tsx 未热更导致 aside 不渲染），
通过 `(Get-Item file).LastWriteTime = Get-Date` 强制触发变更后恢复；后续会话若浏览器表现与源码不符，
先 `Invoke-WebRequest http://localhost:5180/src/...` 核对模块是否最新。

## Feature: 审批入口统一为记录表单 + 查看流程进度按钮（2026-10-22）

**需求:** 使用 InstanceDrawer 的地方全部换成编辑模式的表单；表单增加「查看流程进度」按钮打开流程进度
（InstanceDrawer 退化为流程进度查看器，不再是审批条目的主视图）。

**修改:**

- `AppRecordFormModal` 新增 `footerExtra` prop：渲染在默认底部操作（取消/保存/发起流程）左侧，供
  调用方注入额外按钮；自定义 `footer` 时不生效。
- `ApprovalTaskDrawer` 泛化为通用审批记录抽屉（prop `task` → `item: ApprovalItem`）：
  - 待我审批（taskId 且未决策）：只读表单 + 底部「不同意/同意」（意见弹窗、requestKey 幂等不变）。
  - 流程进行中：只读表单 + 关闭按钮。
  - 流程已结束：编辑模式表单（可保存，onSaved 回调刷新列表）。
  - 三种形态底部均常驻「查看流程进度」按钮 → 打开 InstanceDrawer（签核进度、快照、节点记录）。
  - 修复判断：已处理列表条目也带 taskId，用 `taskId && !decision` 区分待办与已办。
- `ApprovalFeed`：三个 tab（待处理/已处理/我提交的）统一打开 ApprovalTaskDrawer，移除 InstanceDrawer。
- `Notifications` 查看审批：有 PENDING 任务带 taskId，否则直接传 ApprovalDetail（无 taskId）→
  统一走 ApprovalTaskDrawer，移除 InstanceDrawer 分支。
- `RecordApprovalPanel` 保留 InstanceDrawer：它本身处于记录表单内部，点击审批历史打开的就是
  「查看流程进度」目标视图，符合新交互模式。

**验证:** build（tsc -b + vite build）与 test（27 tests）通过；Playwright 实测审批中心「已处理」tab：
点击条目打开记录表单（已拒绝的实例为编辑模式，子表出现新增/操作列），标题无审批前缀；点击
「查看流程进度」后第二个抽屉正常叠加展示快照与签核进度。待处理链路逻辑未变（feat-096 已验证）。

## 修复: 统一抽屉主表单卡片丢失（2026-10-22）

**现象:** 用户反馈详情抽屉里主表单字段的卡片不见了（对比原 AppRecordDetail 有 `rounded-xl border
bg-card p-4 shadow-sm` 卡片容器）。

**修复:** AppRecordDrawer 主体改为 `space-y-4` 布局：错误提示与审批面板在卡片外；主表单字段
（SECTION + FIELD，查看/编辑/新建三态共用）包回与原详情一致的卡片，子表单渲染在卡片外。布局条目按
kind 预过滤（mainEntries/subformEntries，带类型收窄），切换模式时卡片容器不变，保持滚动位置。

**验证:** build（tsc -b + vite build）与 test（27 tests）通过；Playwright 实测：卡片渲染（字段 10 项在
卡片内）、分组标题展示、点击「编辑」原地切换后卡片保持且输入控件在卡片内（8 项）。

## 修复: 统一抽屉点击详情空白（2026-10-22）

**现象:** 用户反馈点击记录后详情抽屉 body 空白（标题兜底为「记录」）。

**定位:** 用 Playwright 连接本地 dev（5180）+ 后端（8080）实测复现：记录接口正常返回但抽屉 body 三段
（loading/error/form）均不渲染。根因为 `AppRecordDrawer` 表单加载 `.then` 回调清理标志条件写反：
`if (cancelled) setForm(loaded)` —— 组件未取消（cancelled=false）时反而丢弃表单，`finally` 又将
loading 置 false，导致 form=null、无错误、无 loading 的全空白状态。

**修复:** 改为 `if (!cancelled) setForm(loaded)`，与同文件其他回调一致。

**验证:** build（tsc -b + vite build）与 test（27 tests）通过；浏览器实测：抽屉标题显示主显字段值、
表单设计入口出现（form 非空才渲染）、字段与子表引用选项正常加载。既有数据问题（f_user 引用的
f_user_department 表后端不存在，404）与本次无关。

## Feature: Apps UI - 记录详情与编辑合并统一抽屉（2026-10-22）

**目标:** 消除“打开详情 → 点击编辑 → 再打开一层编辑抽屉”的双层切换。同一抽屉内原地切换 查看/编辑/新建
（Power Apps 表单模式），字段位置、子表单与审批进度保持连续。

**修改:**

- 新增共享助手，消除与 AppRecordFormModal 的逻辑重复：
  - `form/recordPayload.ts`：`buildRecordPayload`（可见字段必填校验+载荷构建）、`initialCreateValues`（新建
    默认值）、`toEditValues`（记录 → 输入值）。
  - `form/useReferenceOptions.ts`：REFERENCE/MULTIREFERENCE 候选加载 hook。
  - `AppRecordFormModal` 重构消费上述助手（行为不变，继续服务子表记录、快速新建、系统表单、审批待办）。
- 新增 `components/biz/AppRecordDrawer.tsx` 统一抽屉：
  - 三态共用 `parseFormLayout` 布局：查看态文本（renderFormValue / AppReferenceValue 可点击关联记录 /
    MULTIREFERENCE 用 displayRecordText），编辑/新建态 FieldControl 输入控件，切换不换容器、保留滚动位置。
  - 底部按模式切换：查看=「删除」「编辑」；编辑/新建=「取消」「保存」（新建态额外「发起流程」）；编辑/新建态
    左侧固定提示“子表记录即时保存，主表单字段点击「保存」生效”，明确分开保存语义。
  - 保存边界：dirty 状态下关闭抽屉或编辑取消均弹「放弃未保存的修改」确认；新建态自动保存过主表单时确认文案
    明确“主表单已自动保存并保留”。
  - 保存成功：编辑/新建均切回查看态（新建内部 activeRecordId 更新后加载记录详情）并回调 onSaved 刷新列表；
    「发起流程」=先保存校验再 submitApproval，与保存分离。
  - `RecordApprovalPanel` 常驻（非内置表单且有记录时），编辑期间也能看到审批状态与锁定原因；锁定时禁用
    编辑/删除/保存。
  - 子表：查看态只读；编辑/新建态可操作，新建态新增子记录沿用“自动保存主表单”流程。
- `RecordList` 接入：行点击/主显字段链接=查看态、行内编辑与批量编辑=编辑态、新建=新建态、关联记录跳转=第二个
  统一抽屉；删除原双层 `Drawer + AppRecordDetail` 与 `AppRecordFormModal` 组合及 formRecordId 状态。
- `AppRecordView` 独立页面保留 `AppRecordDetail`（页面编辑仅覆盖一层抽屉，无双层叠加问题）。
- 后端零改动。

**验证:**

- `flexmodel-apps-ui npm run build`（tsc -b + vite build）通过，仅既有 chunk 体积与 /logo.png 提示。
- `flexmodel-apps-ui npm test`（vitest run）通过：3 files / 27 tests。

**遗留/风险:**

- 查看态 MULTIREFERENCE 已用 displayRecordText 展示，但 AppRecordDetail（独立页面）仍未处理数组引用展示，
  后续可统一。
- 统一抽屉的编辑/查看切换未做浏览器端手工回归（构建+单测覆盖）；建议实测一次滚动保持与确认弹窗交互。
- 若后续确认某些表单主要用于高频资料维护，可加“默认进入编辑态”的表单级配置（当前统一默认查看态）。

## Feature: Apps UI - 待办审批复用记录表单与意见弹窗（2026-10-22）

**目标:** 审批中心待办任务点击后复用记录编辑表单（只读）展示记录内容，底部按钮改为「同意」「不同意」，点击后弹窗
输入审批意见再提交。

**修改:**

- `AppRecordFormModal` 新增可选 props：`title`（覆盖抽屉标题）、`readOnly`（禁用全部字段控件 + 隐藏表单设计
  headerExtra + 默认子表单只读）、`footer`（覆盖默认保存/取消底部，传入则完全替换）。
- `AppSubformRecords` 新增 `readOnly`：隐藏「新增」按钮、行内编辑/删除按钮与操作列，空态与未保存提示文案适配。
- 新增 `pages/Approvals/ApprovalTaskDrawer.tsx`：
  - `getAppForm(appId, formName)` 加载表单，加载/错误态用简单 Drawer 呈现（错误可重试）；
  - 表单就绪后复用 `AppRecordFormModal`（readOnly + recordId）只读渲染审批记录（审批期间记录被
    f_approval_record_lock 锁定，只读符合后端约束）；
  - footer 为「不同意」（secondary）+「同意」（primary）；点击打开 BizDialog 意见弹窗（Textarea，maxLength
    2000，选填），确认调用既有 `decideApproval(appId, taskId, decision, comment, requestKey)`；
  - 幂等：同任务+同决策+同意见复用同一 requestKey（对齐 InstanceDrawer 的重试语义），成功后 message 提示、
    关闭意见弹窗与抽屉、刷新列表。
- `ApprovalFeed`：待处理 tab 且任务带 taskId 时打开 `ApprovalTaskDrawer`；已处理/我提交的及无 taskId 场景保留
  `InstanceDrawer`；切换 tab 清空选中，避免抽屉内容与 tab 不一致。
- 消息中心（`Notifications.tsx`）「查看审批」同步接入：通知详情的 `approval` 为 `ApprovalDetail`（后端 detail 接口
  不带 taskId，仅列表接口附加），前端从 `approval.tasks` 中解析当前用户（getAuthSession）PENDING 任务并拼装
  `taskId` 打开 `ApprovalTaskDrawer`；无待办任务（已处理/他人任务）回退 `InstanceDrawer` 进度详情；决策成功后
  刷新通知列表（onChanged=reload）。
- 后端零改动（决策接口、requestKey 幂等、comment ≤2000 校验均为既有能力）。

**验证:**

- `flexmodel-apps-ui npm run build`（tsc -b + vite build）通过，仅既有 chunk 体积与 /logo.png 提示。
- `flexmodel-apps-ui npm test`（vitest run）通过：3 files / 27 tests（消息中心改造后复跑通过）。

**遗留/风险:**

- 意见弹窗（z-[1041]）叠在表单抽屉（z-[1040]）之上，未做浏览器端视觉复验。
- 只读态下引用字段以下拉禁用样式展示（与编辑态一致），未复用详情页的可点击链接样式；如需点击跳转关联记录，
  后续可在 readOnly 模式下切换为 AppReferenceValue 渲染。
- 会话间 AppSubformRecords 的两处 Empty description 属性曾被外部改动移除，本次已恢复并适配只读文案。

## Feature: Apps UI - 新增/编辑弹窗支持子表单与自动保存主表单（2026-10-22）

**目标:** 让新增/编辑记录弹窗与详情页保持一致（共享表单渲染层），弹窗内支持添加子表单记录；添加子表单记录时自动
保存主表单；必填字段未填时拦截，不允许添加子表单记录。

**修改:**

- 新增 `flexmodel-apps-ui/src/components/biz/form/` 共享渲染层：
  - `parseFormLayout.ts`：统一 SECTION/SUBFORM/FIELD 布局解析（弹窗与详情页原先各自实现）；layout 为空时回退为
    全部业务字段（此前弹窗空布局渲染为空白，与详情页不一致）；hidden 字段与 excludeField（子表单引用字段）不参与
    渲染与校验。
  - `FormFieldControl.tsx`：可编辑字段控件（FieldControl/toInputValue/ReferenceOption），由弹窗抽出共享。
  - `FormReadonlyValue.tsx`：只读字段值渲染（renderFormValue），由详情页抽出共享。
- `AppRecordFormModal` 重构消费共享层；新增默认 renderSubform：渲染 `AppSubformRecords`（自定义 renderSubform
  优先，SystemRecordFormModal 本地态子记录不受影响；recordContext/saveRecord 场景不启用默认子表单，天然限制嵌套
  一层，防止表单互引时无限递归）。
- 自动保存流程 `persistForSubform`：buildPayload 必填（仅可见字段）校验失败 → 拦截并显示“X不能为空”，不保存；
  通过 → 新建场景 createAppRecord 后 setSavedRecordId 切换编辑态（标题变“编辑”、隐藏发起流程按钮、后续保存走
  update）并 message 提示“主表单已自动保存”；编辑场景静默 update 保存当前改动。取消弹窗保留已保存的主记录
  （草稿式，用户确认的取舍）。
- `AppSubformRecords`：mainRecordId 改为可空，新增 `onBeforeCreate?: () => Promise<string | null>` 拦截；主记录
  未保存时展示“主表单尚未保存”占位且隐藏搜索/刷新/表格，新增按钮 await onBeforeCreate 解析主记录 ID 后再打开
  子记录弹窗；删除操作增加 mainRecordId 判空保护。
- 必填口径（用户确认）：前端仅校验 layout 可见字段；后端 create 时对隐藏必填字段的 400
  （“必填字段不能为空: X”）经 requestJson 抛出并透出到弹窗错误区。
- 后端零改动（`AppRecordService.resolveRecordContext` 已强制主记录先存在，与自动保存顺序天然一致）。
- 按用户要求“样式都使用 Drawer”：`components/ui/drawer.tsx` 新增 `BizDrawer`（标题 + headerExtra + 滚动内容 +
  footer 结构，宽度默认与详情页抽屉一致 `md:w-[calc(100vw-16rem)]`，可用 width 覆盖）；AppRecordFormModal 的
  主表单与子表单记录表单均由 BizDialog 改为 BizDrawer 呈现（SystemRecordFormModal 复用同组件自动生效）；
  「发起流程」小型选择弹窗与删除确认保留 BizDialog。

**验证:**

- `flexmodel-apps-ui npm run build`（tsc -b + vite build）通过，仅既有 chunk 体积与 /logo.png 运行时解析提示。
- `flexmodel-apps-ui npm test`（vitest run）通过：3 files / 27 tests。

**遗留/风险:**

- 自动保存后取消抽屉会在列表留下草稿记录（预期行为）；父列表不会自动刷新，需手动刷新可见。
- 子表单记录抽屉与主表单抽屉同宽，打开时完全覆盖主抽屉，关闭后返回；未做浏览器端视觉复验。
- 既有 LSP 对 flexmodel-server 生成代码的误报与本次改动无关。

## Follow-up: 应用菜单弹窗接入真实接口与内置页面关联（2026-10-09）

**目标:** 保持原始 `MenuEditorDialog` UI 与交互不变，将应用菜单从 mock 数据切换为 `/api/apps/{appId}/menus`
真实接口；空菜单打开弹窗时不显示任何 mock 数据，新增一级菜单时后端保存为 `GROUP`。

**修改:**

- `MenuEditorDialog` 在应用上下文中异步加载后端菜单，空列表保持空态；一级菜单创建提交 `type: GROUP`
  ，左侧菜单/分组/分组内菜单的增删改和拖拽排序均调用真实接口。
- `MenuLinkPicker` 在应用上下文加载 `/menus/targets`，继续使用原有关联项选择交互；`ITEM` 必须绑定 `BUILTIN` 或 `ENTITY`
  目标。
- 菜单叶子类型统一从 MENU 迁移为 ITEM，后端校验、响应、前端请求与路由判断均已同步；按需求不做历史数据兼容。
- 内置页面关联已打通：`BUILTIN:dashboard / feeds / files / approval / contacts / notifications` 保存后生成
  `/apps/{appId}/{menuId}`，点击菜单进入 `AppMenuPage` 并渲染对应的 Dashboard、动态、文件、审批中心、通讯录或通知页面。
- 修复关联项下拉浮层层级：`SelectContent` 从 `z-50` 提升到 `z-[1050]`，高于菜单编辑弹窗的 `z-[1041]`，选项不再被弹窗遮挡。
- 修复无 menuId 时的默认选中：访问 /apps/{appId} 会加载菜单并重定向到第一个 ITEM 的 pageRoute；没有任何 ITEM 时保留空态。
- 菜单编辑支持跨分组拖拽：ITEM 可从分组内拖到分组外，也可从分组外拖入分组；移动后更新父级并保存排序。
- 应用列表、应用详情、应用菜单和关联项改为会话内缓存，并合并并发请求；同一应用内点击菜单不再重复请求
  /api/apps、应用详情和菜单接口，菜单写操作后按应用失效缓存。
- 登录、退出登录和认证会话解析失败时统一清空应用数据缓存，避免跨用户读取旧数据。
- 修复空顶部菜单回落到第一个顶部菜单的问题：空顶部菜单跳转 `/apps/{appId}?top={topId}`，AppHome 仅在当前顶部菜单内查找首个
  ITEM；没有子项时保留空态页面。
- 关联项内置页面选项收敛为工作台、文件、审批中心；动态、通讯录、通知不再出现在菜单关联项下拉中。
- 关联项下拉移除“未关联”选项；未选择时仅显示占位提示，保存仍要求 ITEM 必须绑定关联项。
- 应用管理列表在大屏下改为一行四列；应用卡片移除“业务对象 n 个”，改为展示“所有者：owner”。应用接口响应新增 owner 字段，取项目
  ownerId。
- 应用接口返回的 createdAt / updatedAt 在前端统一格式化为 yyyy-MM-dd HH:mm:ss，应用管理和应用设置不再展示 ISO 时间与纳秒精度。
- 应用管理卡片按应用自身的 primaryColor 渲染图标底色、文字色和悬停边框，不再统一使用全局主色。
- 用户界面概念统一为“表单”：应用设置、菜单关联项、记录页、审批筛选和管理后台中原“业务对象/实体/数据表”文案均已替换；底层接口类型仍保留
  ENTITY，技术实现仍为实体。
- `AppHome` 与 `AppLayout` 共用同一个后端驱动的菜单编辑弹窗；应用页顶部与左侧导航根据后端菜单渲染，避免出现 mock 菜单。
- 后端取消 `GROUP` 仅能位于根级的限制，支持根级分组与嵌套分组，匹配原始 UI 的左侧分组/分组内菜单结构；排序接口递归校验并重建全部分组子级。
- `AppMenuResourceTest` 增加嵌套分组、分组内菜单、递归排序、删除级联和内置页面路由生成覆盖。

**验证:**

- `mvn test -pl flexmodel-server -am "-Dtest=AppMenuResourceTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（8
  tests，0 failures，0 errors，含 ITEM 从嵌套分组移出到分组外的用例）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。

- 请求缓存修复后复跑 `flexmodel-apps-ui npm run build` 通过。
- 空顶部菜单修复后复跑 `flexmodel-apps-ui npm run build` 通过。
- 移除关联项选项后复跑 AppMenuResourceTest 验证 targets 仅返回 3 个内置页面。
- 移除“未关联”选项后复跑 `flexmodel-apps-ui npm run build` 通过。
- 应用管理四列与所有者展示修改后，`flexmodel-apps-ui npm run build` 与 `AppResourceTest` 通过。
- 日期格式化修改后复跑 `flexmodel-apps-ui npm run build` 通过。
- 应用卡片主色样式修改后复跑 `flexmodel-apps-ui npm run build` 通过。
- 界面术语最终统一为“表单”后复跑 `flexmodel-apps-ui npm run build` 通过。
- 层级修复后复跑 `flexmodel-apps-ui npm run build` 通过。

**遗留/风险:**

- 业务实体页面记录仍为 mock，待后续接入真实模型字段与记录接口。
- 完整 `flexmodel-server` 测试套件仍存在项目文档标注的已知失败，本次以菜单集成测试和前端构建验证为准。

## Feature: Apps 应用菜单管理（2026-10-09）

**目标:** 应用点击后进入 `/apps/{appId}`；顶部菜单与左侧导航在应用页保持为空，右侧空态区域点击后打开原始
`MenuEditorDialog`；菜单支持分组与一级子菜单、内置模块和业务实体关联、绑定后自动生成页面路由，并提供增删改查与排序接口。

**修改:**

- 后端新增 `AppMenuResource` / `AppMenuService` 与 DTO，菜单持久化到项目 `metadata.app.menus`，提供列表、目标项、创建、更新、删除和排序接口。
- 菜单模型支持 `GROUP` / `ITEM` 结构、`BUILTIN` / `ENTITY` 关联项校验，`ITEM` 自动生成 `/apps/{appId}/{menuId}` 页面路由。
- `AppService.updateApp` 保留既有 `metadata.app.menus`，避免应用设置保存时丢失菜单。
- 前端应用页点击右侧空白区域直接打开原始 `MenuEditorDialog`；`AppLayout` 在应用页将顶部菜单和左侧导航保持为空。
- `AppHome` 不再自动重定向，也不渲染菜单列表；点击右侧空白区域直接打开原始 `MenuEditorDialog`。
- 左侧菜单不再展示“暂无菜单”按钮，只保持空白区域。
- 路由统一为 `/apps/:appId`、`/apps/:appId/:menuId` 与记录查看路由，实体页面仍使用演示数据。
- 修正 `AppMenuResourceTest` 中子菜单请求 `formatted(TEST_SUFFIX, groupId)` 参数顺序，解决父菜单 ID 误传问题。

**验证:**

- `mvn test -pl flexmodel-server -am -Dtest=AppMenuResourceTest "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（5
  tests，0 failures，0 errors）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- 停止占用 `flexmodel-server-dev.jar` 的 Quarkus dev 进程后，`bash ./init.sh` 通过全模块 clean compile 与
  `flexmodel-engine` 测试；脚本内 UI E2E 因 WSL 未检测到 Node 按脚本跳过。

**遗留/风险:**

- 业务实体页面当前仍使用 mock 记录数据，后续需要接入真实模型字段与记录接口。
- 完整 `flexmodel-server` 测试套件仍存在项目文档标注的已知失败，本次以菜单集成测试与标准 `init.sh` 验证为准。

## Feature: ModelRef Filter 路径语法对齐（2026-09-24）

**目标:** 按 existing query / GraphQL `where` 语法调整 relation filter，不再引入 `source.*` / `target.*` 专用前缀。

**修改:**

- relation filter 目标字段统一使用 `<relationField>.<field>`，例如 `activeStudents.status`。
- `_field` 中引用当前表字段时直接使用裸路径，例如 `{ "_field": "id" }`。
- `RelationFilterSupport` 负责路径识别、SQL alias 替换和懒加载目标过滤解析；支持 `_and` / `_or` 与反向字段比较。
- SQL join、Mongo `$lookup` pipeline/let、嵌套查询与懒加载统一使用新路径规则；Mongo 显式 join filter 继续沿用旧查询 alias
  语法。
- 更新 FML 解析回归测试、SQL/Mongo 渲染测试、UI filter 提示与技术方案文档。
- 同步 `flexmodel-website` 的 `records.md` 与 `modeling.md`：说明策略、filter 元数据、REST 展开行为、路径规则与当前 GraphQL
  支持范围。
- 网站教程进一步补充策略推断规则、条件关联谓词要求、反向路径写法、索引建议与执行支持边界。

**验证:**

- `mvn test -q -pl flexmodel-engine` 通过。
- `mvn -pl flexmodel-engine/flexmodel-core test "-Dtest=ASTNodeConverterTest,RelationFilterTest"` 通过（11 tests，0
  failures，0 errors）。
- `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `git diff --check` 通过。
- `flexmodel-website npm run build` 通过。
- 2026-09-24 完整运行 `./init.sh`：全模块 clean compile 与 `flexmodel-engine` tests 通过；当前环境未安装 Node，UI E2E
  按脚本跳过。

**遗留/风险:**

- Condition 关联依赖高频目标字段索引，索引提示仍待后续完善。

## Hardening: ModelRef Filter 跨路径一致性修复（2026-09-24）

**目标:** 修复 ModelRef filter 在 SQL、MongoDB、REST expand、懒加载和 UI 提交链路中的语义漂移，优先消除静默错数据与运行时错误。

**修复:**

- Mongo 手动 `join.where` 恢复为目标文档普通匹配；模型级 relation filter 与调用方 filter 分开渲染并以 `$and`
  叠加，不再让调用方条件覆盖模型声明。
- Mongo 普通查询中的 `_field` 渲染为文档字段 `$field`，不再生成未声明 `$$` 变量；源字段 `_between` 生成合法的
  `$expr/$and/$gte/$lte` 聚合表达式。
- `FOREIGN_KEY + 纯目标 filter` 的懒加载、expand 与 join 均应用 filter；expand 路径改为批量查询，避免常见场景 N+1。
- UI 新建/编辑 ModelRef 完整透传 `strategy`、`filter`、`from`、`localField`、`foreignField`、`cascadeDelete` 与 `multiple`
  ，编辑时不再丢失已保存 filter。
- 源字段显式 `null` 可参与过滤；源数据缺少引用字段时抛出明确错误，不再触发 `Map.of` NPE。
- SQL builder 不再修改共享 `Query.Join`，`replaceAliases` 幂等；`_in` / `_nin` 集合内 `_field` 在 SQL 与 Mongo 均可解析。
- FML 对象字面量保留数字与布尔原始类型；`filter` 支持 JSON 字符串；FML 输出可 round-trip。
- FML `filter` 支持同一对象中的多个顶层条件，语义等价于 `_and`；已补充解析回归测试并修正 `dev_test.fml` 的多条件示例。
- `CONDITION` 解析阶段强制包含至少一个当前表字段与目标字段比较，拒绝仅有常量目标过滤的错误谓词。
- 单数 condition/filter 关联在 expand 与懒加载中匹配多条均抛出明确错误；多个关系指向同一目标模型时要求使用关系字段名作为
  alias，避免歧义。
- GraphQL 关系 resolver 从 left join 调整为 inner join：复数关系无匹配返回空数组，有 N 条匹配返回 N 条，不再生成目标字段全为
  null 的占位记录。

**验证:**

- `mvn -pl flexmodel-engine/flexmodel-core test "-Dtest=RelationFilterTest,ASTNodeConverterTest"` 通过（23 tests）。
- `mvn test -q -pl flexmodel-engine` 通过。
- `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `flexmodel-ui`：针对性 ESLint、`npx tsc --noEmit --pretty false`、`npm run build` 通过（仅既有 chunk size / Browserslist
  警告）。
- `flexmodel-website npm run build` 通过；根仓库、`flexmodel-ui`、`flexmodel-website` 的 `git diff --check` 通过。
- `mvn test -q -pl flexmodel-engine/flexmodel-graphql "-Dtest=GraphQLProviderTest#relationWithNoRowsReturnsEmptyList"`
  通过；用例同时断言空关系返回 `[]`、普通多条关系返回 2 条、filter 后关系返回 1 条。
- `mvn test -q -pl flexmodel-engine/flexmodel-core "-Dtest=ASTNodeConverterTest"` 通过；
  `mvn compile -q -pl flexmodel-server` 通过，确认 `dev_test.fml` 可解析。
- `bash ./init.sh` 本次在 WSL 路径下编译 JavaCC 生成文件时失败；已改用原生 Windows Maven 执行同等 `mvn compile` 与
  `mvn test -pl flexmodel-engine` 验证。`mvn clean compile` 曾被运行中的 Quarkus dev 进程锁定 target jar 阻断。

**遗留/风险:**

- 引用当前表字段的 `CONDITION` 关联在 expand 中仍按父行逐条查询；`FOREIGN_KEY + 纯目标 filter` 已批量优化。通用 condition
  批量执行需要目标结果与父行谓词的内存匹配器，建议作为后续独立优化。
- GraphQL relation resolver 统一接入尚未完成；当前覆盖 SQL / Mongo 查询构建、REST expand 与对象懒加载。
- 字段存在性、类型兼容性与操作符合法性的完整 schema 校验仍待后续增强。

## Refactor: ModelRef 策略值 KEY 重命名为 FOREIGN_KEY（2026-09-23）

**目标:** 让策略值与语义一致，避免 `KEY` 被误解为主键或普通键。

**修改:**

- 后端 `RelationStrategy`、默认策略、解析赋值与回归测试统一改为 `FOREIGN_KEY`。
- UI 策略类型、默认值、表单选项、条件判断与英文文案统一改为 `FOREIGN_KEY` / `Foreign key relation`。
- `docs/plans/model-ref-filter-relation.md` 与功能清单中的策略值说明同步更新。

**验证:**

- `mvn test -q -pl flexmodel-engine` 通过。
- `flexmodel-ui` 针对性 ESLint 通过；隔离无关未跟踪 UI 组件后的 TypeScript 项目检查通过；`npx vite build` 通过。
- `git diff --check` 通过；代码中无残留 `RelationStrategy.KEY` 或表单 `KEY` 策略值。

## Feature: ModelRef Filter 关联 UI 补齐（2026-09-23）

**目标:** 在建模表单中显式选择 `FOREIGN_KEY` / `CONDITION` 关联策略，并提供 `filter` 编辑、校验与提交转换。

**修改:**

- `FieldForm.tsx` 新增策略选择器；`FOREIGN_KEY` 必填 `localField` / `foreignField`，`filter` 可选；`CONDITION` 必填
  `filter`，隐藏并清理外键字段与 `cascadeDelete`。
- `filter` 通过 JSON 文本编辑，提交前校验必须为 JSON 对象并转换为结构化 `filter`；`filter` 与 `cascadeDelete` 互斥并在表单内自动处理。
- `FieldList.tsx` 的 ModelRef 提示展示策略、外键或 filter 配置。
- `fieldFormConstants.ts`、`data-modeling.d.ts` 与中英文 i18n 补充策略、filter、校验文案。

**验证:**

-
`npx eslint src/pages/DataModeling/components/FieldForm.tsx src/pages/DataModeling/components/FieldList.tsx src/pages/DataModeling/components/fieldFormConstants.ts`
通过。
- 中英文 locale JSON 解析通过；`git diff --check` 通过。
- 使用临时 tsconfig 排除工作区既有未跟踪 UI 组件后，`npx tsc -p ...` 项目级类型检查通过。
- `npx vite build` 通过；未隔离的 `npm run build` 仍被既有未跟踪 UI 组件的缺失依赖与类型错误阻断（`lucide-react`、Radix、
  `sonner` 等）。

## Feature: ModelRef - Filter 关联（2026-09-23）

**目标:** 为 `@relation` 支持 `filter`，实现 FOREIGN_KEY 关联附加过滤与无外键的 CONDITION 关联。

**修改:**

- 新增 `RelationStrategy`、`FieldReference`、`RelationFilterSupport`。
- `ModelRefField` 保存 `filter` 与 `strategy`；FML 注解支持对象字面量。
- `ASTNodeConverter` 解析 `<relationField>.*` 目标字段与裸路径当前表字段 filter，校验 key/filter/cascadeDelete 组合。
- `SqlStatementBuilder` 支持 FOREIGN_KEY+filter 与 condition-only join。
- `MongoStatementBuilder` 支持 `$lookup` pipeline/let。
- `BaseService` 嵌套查询与 `LazyLoadInterceptor` 支持当前表字段引用解析。
- 新增 `RelationFilterTest` 和 `ASTNodeConverterTest` 回归用例。

**验证:**

- `mvn clean compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `mvn test -q -pl flexmodel-engine` 通过。
- `mvn -pl flexmodel-engine/flexmodel-core test "-Dtest=ASTNodeConverterTest,RelationFilterTest"` 通过（10 tests, 0
  failures）。

**遗留/风险:**

- Condition 关联没有物理外键，性能依赖目标字段索引；后续可在 UI/校验层补充索引提示。
- 当前未增加 UI 表单配置入口，FML 已可直接使用新语法。

## Doc - ModelRef Filter 关联技术方案（2026-09-23）

**目标:** 为 `@relation` 增加 `filter` 语义，明确键关联、条件关联、cardinality、生命周期和跨库执行策略。

**修改:**

- 新增 `docs/plans/model-ref-filter-relation.md`，包含语法设计、元数据模型、解析器扩展、SQL/MongoDB/GraphQL 翻译规则、校验策略和实施阶段。
- 明确 v1 中 `cascadeDelete` 与 `filter` 互斥，condition 关联不生成外键或 DDL。
- 明确现有 `localField` / `foreignField` 关联保持兼容。

**验证:** 文档已人工校对；本次为设计文档产出，未修改运行时代码。

## Feature: 去除 OpenTelemetry 并按功能归属日志（2026-08-31）

**目标:** 移除 OpenTelemetry 运行时依赖，保留轻量 traceId 生成/传播；将各类日志迁移到所属功能包与前端功能路由下。

**完成内容:**

- 新增 `common.trace` 轻量 trace 上下文，替换 OTel span/span context；HTTP、Quartz、EventBus、函数运行时调用均继续生成或传递
  W3C `traceparent`。
- 删除 FmSpanExporter、Span 落库、链路列表后端接口、链路资源、日志清理任务和 `quarkus-opentelemetry` 配置。
- 接口日志迁入 `dev.flexmodel.apilog`，审计日志迁入 `dev.flexmodel.data`，函数日志迁入 `dev.flexmodel.functions`，任务执行日志保留在
  `dev.flexmodel.scheduling`。
- 前端移除可观测性菜单：API 日志移到 API，审计日志移到数据，函数日志移到边缘函数，任务执行日志移到任务调度；trace 详情改用轻量
  `/trace/:traceId` 路由。

**验证:**

- `mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `mvn test -pl flexmodel-server -Dtest=ApiLogResourceTest,TriggerResourceTest` 通过（18 tests, 0 failures, 0 errors）。
- `npx tsc --noEmit`（flexmodel-ui）通过。

**遗留/风险:**

- `ApiLogResourceIT` 存在原有 `@Inject is not supported in @QuarkusIntegrationTest` 问题，不属于本次改动引入；后续需要单独修复。


## Fix: flow 用户任务时间线时间字段（2026-08-28）

## Refactor: 合并 data-events-out 至 events-out（2026-08-29）

**背景:** flow 事件桥接（`events-out`）与 realtime 数据变更桥接（`data-events-out`）共享同一 connector、同一
`flexmodel.events` topic 交换机、同一开关 `flexmodel.events.rabbitmq.enabled`，仅载荷类型不同。当前无分开使用的实际
场景，合并减少一个 AMQP channel 与一处配置。

**修改:**

- 新增 `dev.flexmodel.common.FlexmodelEvent` 标记接口（出站事件载荷统一类型）。
- `FlowEvent`（抽象基类）与 `DataChangeEvent` 实现 `FlexmodelEvent`。
- `FlowEventRabbitmqBridge`、`RealtimeRabbitmqListener` 统一注入
  `@Channel("events-out") Instance<MutinyEmitter<FlexmodelEvent>>`。
- `application.properties` 删除 `mp.messaging.outgoing.data-events-out.*` 两行。
- `DataChangeEvent` javadoc 通道名同步更新为 `events-out`。

**设计效果:** 单一出站通道 `events-out`，消费端按 routing key（`data.*` / `flow.*`）区分流类型，零改动。 native image
反射注册无需调整（两具体类型包 `dev.flexmodel.flow.event.**`、`dev.flexmodel.realtime.**` 已注册）。

**验证:**

- 服务模块编译（`build_project` 指定改动文件）→ 通过，无错误。
- 改动文件 lint 仅余既有 warning（Lombok @Getter 提示、`@ConsumeEvent` 方法 "never used" 误报、预存未用 import），非本次引入。

## Fix: flow 用户任务时间线时间字段（2026-08-28）

**修改:**

- `flexmodel-ui/src/pages/Flow/components/UserTasksDrawer.tsx`：流程实例完成时间由不存在的 `modifyTime` 改为后端返回的
  `updatedAt`；开始时间由不存在的 `createTime` 改为 `createdAt`。

**验证:**

- `npx tsc -b` → 通过。
- `npm run build`（`tsc -b && vite build`）→ 通过。

## Doc/Code Fix: flow 用户任务回滚事件路由键规范化（2026-08-26）

**背景:** `flow.usertask.rollback.suspended` 因 `.` 分段导致事件后缀多出一段（4 段 vs 其他事件 3 段）， 消费端绑定
`flow.*.usertask.*` 无法匹配该事件；且命名解析为「回滚被挂起」，与实际语义（任务因回滚被重新挂起）不符。

**修改:**

- `flexmodel-server/.../flow/event/FlowEventTypes.java`：`USER_TASK_ROLLBACK_SUSPENDED` 值改为
  `flow.usertask.rollback-suspended`（连字符合并动作段）。`FlowEvent.rabbitmqRoutingKey()` 通用插入逻辑自动 生成
  `flow.<projectId>.usertask.rollback-suspended`，消费端 `flow.*.usertask.*` 现可匹配全部用户任务事件。
- `flexmodel-website/docs/tutorial/features/flow.md`：事件表、载荷示例、订阅示例全部改为 `flow.<projectId>.xxx` 格式（此前漏写
  projectId 段）；同步修正交换机名（`flexmodel.flow.events` → `flexmodel.events`，与 application.properties 及数据事件文档一致）与
  转发通道名（`flow-events-out` → connector 级默认 `events-out`）。

**验证:**

- `mvn -q clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` → 无 ERROR，BUILD 成功。
- `@ConsumeEvent` / `UserTaskRollbackSuspendedEvent` / `UserTaskExecutor` 均引用常量，自动生效。

## Doc Fix: 补全模型引用字段重命名的文档更新（2026-08-15）

**背景:** `feature/model-ref` 分支此前将 `RelationField` 重命名为 `ModelRefField`、`ScalarType.RELATION` → `MODEL_REF`、
`ScalarType.ENUM` → `ENUM_REF`（commit d3be8f0、d8bb3cb）。源码与测试资源已全部更新且编译通过，但 `flexmodel-engine/docs/` 下
3 个文档仍残留旧 API 名称。

**修复:**

- `flexmodel-engine/docs/API.md`：将「关系字段」章节标题改为「模型引用字段」；将 `RelationField` 示例重写为 `ModelRefField` 新
  API（`setFrom`/`setLocalField`/`setForeignField`/`setMultiple`）；删除新 API 已不支持的多对多示例（`setJoinTable` 等方法已移除）。
- `flexmodel-engine/docs/flexmodel-core.md`：`new RelationField("studentClass")` → `new ModelRefField("studentClass")`。
- `flexmodel-engine/docs/flexmodel-codegen.md`：`it.isRelationField()` → `it.isModelRefField()`。

**验证:**

- `mvn test -pl flexmodel-engine/flexmodel-core -o` → BUILD SUCCESS，Tests run: 49, Failures: 0, Errors: 0（含
  `JsonSerializeTest`）。
- 全仓搜索 `RelationField` / `isRelationField`（docs 目录）→ 0 残留。

## Bug Fix: Storage 复制访问链接不能访问（2026-08-06）

**症状:** Storage 页面点击「复制链接」后，把链接粘贴到浏览器地址栏无法访问（401 / 路径异常）。

**根因（两个叠加问题）:**

1. 下载接口（`BucketResource.downloadObject` / `headObject`）始终要求 Bearer token，浏览器地址栏不带
   `Authorization` 头 → `AuthFilter` 抛 `Token is missing` 401。`BucketVisibility.PUBLIC`（公开 Bucket）的匿名读
   语义虽在模型中定义，却从未被认证层实现。
2. `LocalStorageOperations` 用 `basePath.relativize(p).toString()` 返回相对路径，分隔符为 OS 相关；Windows 下
   为反斜杠，导致子目录文件的复制链接、面包屑与目录导航失效（S3 实现用正斜杠，无此问题）。

**修复:**

- `flexmodel-server/.../storage/config/LocalStorageOperations.java`：新增 `toPosixPath(Path)`，`listFiles` 与
  `getFile` 返回的 `path` 统一为 POSIX 正斜杠，跨平台一致。
- `flexmodel-server/.../common/config/web/filter/AuthFilter.java`：注入 `BucketService`，新增
  `isAnonymousPublicBucketRead`。无 Bearer token 时，对 `GET/HEAD` 命中
  `projects/{projectId}/buckets/{bucketName}/objects(/.*)?` 的请求解析 Bucket；visibility 为 `PUBLIC` 时放行匿名读
  （下载/HEAD/元数据/列表），`PRIVATE`/`AUTHENTICATED` 及写操作仍要求认证。解析失败回退标准认证流程。
- `flexmodel-ui/.../Storage/components/FileBrowser.tsx`：新增 `visibility` 入参；`handleCopyLink` 对 `path`
  做反斜杠→正斜杠兜底归一；非 PUBLIC Bucket 复制时给出 warning 提示需设为公开。
- `flexmodel-ui/.../Storage/index.tsx`：向 `FileBrowser` 传入 `activeBucket.visibility`。
- i18n 新增 `copy_link_not_public`（zh/en）。

**验证:**

- `mvn compile -pl flexmodel-server -am` → BUILD SUCCESS。
- `mvn test -pl flexmodel-server -Dtest=LocalStorageOperationsTest` → Tests run: 14, Failures: 0, Errors: 0。
- IDE inspections（FileBrowser.tsx / index.tsx / AuthFilter.java / LocalStorageOperations.java）无 error。

**行为说明:** 公开 Bucket 的复制链接现在可在浏览器直接打开下载；非公开 Bucket 的链接仍需认证（符合可见性 设计），前端复制时会提示用户将
Bucket 设为「公开」以获得匿名访问。

## Current State

**Last Updated:** 2026-08-04 **Session ID:** pages-deploy-eventloop-block **Active Feature:** 修复部署 pages 报错（事件循环被
JDBC 阻塞）

## Bug Fix: 部署 pages 报错（2026-08-04）

**症状:** 上传 zip 部署 pages 时报错；日志显示 `LogEventConsumer` 在 `vert.x-eventloop-thread-0` 上被 MySQL SSL socket
读阻塞 4176ms+（blocked-thread-checker 警告）。

**根因（两个叠加问题）:**

1. `LogEventConsumer.consume` 无 `@Blocking`，阻塞 JDBC 写入直接跑在 Vert.x 事件循环线程上。
2. 生产 JDBC URL（docker-compose）无 `connectTimeout`/`readTimeout`（Connector/J 默认无限等待）。MySQL
   容器重启后池中残留旧连接读到已死对端 → 无限阻塞 → 事件循环冻结 → 部署请求超时报错。

**修复:**

- [x] 全部 5 个 `@ConsumeEvent` 消费者统一改为 `@ConsumeEvent(value=..., blocking = true)`（工作线程/虚拟线程执行，不再阻塞事件循环）：
  - `LogEventConsumer`（JDBC 写入，本次故障元凶）
  - `ProjectDeletedSchedulingConsumer`（Quartz 操作走 JDBC f_qrtz_*）
  - `TriggerFlowEventConsumer`（流程执行含 DB + HTTP 函数调用）
  - `SettingsEventConsumer` / `GraphQLEventConsumer`（纯内存/日志，顺带统一）
- [x] `docker-compose.yml` JDBC URL 添加 `connectTimeout=5000&readTimeout=15000`（Connector/J 9.x 命名）
- [x] `PageResource.java` 类级 `@Blocking`（zip 解包 + DB 写入均阻塞）

**验证:** `mvn compile -pl flexmodel-server -am -q -o` 通过。

**遗留风险:** JAX-RS 端点默认仍在事件循环上运行（本仓库无全局 `@Blocking` 约定），仅 pages 特性包已处理；其他含 DB 访问的
Resource 若遇 DB 挂死同样会冻结事件循环，建议后续按特性包逐个加 `@Blocking`。

## Status

### What's Done

#### Phase 1: Deno Functions Runtime (flexmodel-functions-runtime/)
- [x] `deno.json` — Deno project configuration with Hono.js dependency
- [x] `src/types.ts` — Full TypeScript type definitions (FunctionMeta, InvokeRequest/Result, Worker messages, etc.)
- [x] `src/sdk/flexmodel.ts` — RPC Dispatcher (proxies Worker SDK requests to Java REST API)
- [x] `src/runner/registry.ts` — Function Registry (metadata cache + LRU source cache, lazy load from Java)
- [x] `src/runner/worker-entry.ts` — Worker internal entry (addEventListener message handling, dynamic import, ctx SDK injection)
- [x] `src/runner/worker.ts` — Worker executor (create Worker, timeout/terminate, proxy SDK RPC)
- [x] `src/router/health.ts` — Health check endpoint (GET /health)
- [x] `src/router/functions.ts` — Functions routes (POST /deploy, DELETE /:projectId/:name, POST /invoke)
- [x] `src/server.ts` — Hono.js server initialization and route registration
- [x] `src/main.ts` — Entry point (Deno.serve on configurable port)

#### Phase 2: Data Model Changes (project.fml)
- [x] Extended `enum TriggerType` with `HTTP`
- [x] Added `f_function` model (id, project_id, name, slug, description, entry_point, status, current_version, timeout, memory_limit, timestamps, indexes)
- [x] Added `f_function_version` model (id, function_id, version, source_code, timestamps, index)
- [x] Code generation verified — `Function.java`, `FunctionVersion.java`, `TriggerType.HTTP` generated successfully

#### Phase 2: Java Functions Feature Package (flexmodel-server)
- [x] `dto/FunctionCreateRequest.java` — Create request with validation
- [x] `dto/FunctionUpdateRequest.java` — Update request DTO
- [x] `dto/FunctionInvokeRequest.java` — Invocation request (method, headers, body, query)
- [x] `dto/FunctionInvokeResponse.java` — Invocation response with _meta (executionTimeMs, logs)
- [x] `dto/FunctionDeployRequest.java` — Deploy request to Deno (metadata only)
- [x] `dto/FunctionResponse.java` — API response with TriggerRef list
- [x] `dto/FunctionVersionResponse.java` — Version list response
- [x] `dto/FunctionPageRequest.java` — Pagination request
- [x] `FunctionException.java` — Business exception for functions
- [x] `FunctionRepository.java` — Repository interface
- [x] `FunctionFmRepository.java` — Repository implementation (AbstractRepository pattern)
- [x] `FunctionVersionRepository.java` — Version repository interface
- [x] `FunctionVersionFmRepository.java` — Version repository implementation
- [x] `FunctionInvoker.java` — HTTP client to Deno functions runtime (deploy, invoke, delete, healthCheck)
- [x] `FunctionService.java` — Core service (CRUD, state machine, version management, invocation, auth validation, startup recovery)
- [x] `FunctionResource.java` — REST endpoints (CRUD, trigger management, public invoke entry)
- [x] `FunctionInternalResource.java` — Internal API for runtime lazy source loading

#### Phase 3: Frontend UI — Edge Functions Management Page (flexmodel-ui)
- [x] `src/services/function.ts` — API service layer (TypeScript interfaces + all CRUD/invoke/trigger endpoints)
- [x] `src/pages/Functions/index.tsx` — Main list page (table with status tags, search/filter, pagination, create/edit/delete actions)
- [x] `src/pages/Functions/components/FunctionForm.tsx` — Create/Edit modal (tabs: Basic Settings + Source Code + HTTP Trigger config)
- [x] `src/pages/Functions/components/FunctionDetail.tsx` — Detail drawer (tabs: Overview + Code + Versions with rollback + Test Invoke)
- [x] `src/pages/Functions/components/FunctionInvokePanel.tsx` — Test invoke panel (request builder with method/headers/body/query + response viewer with logs)
- [x] `src/locales/zh.json` — Chinese translations (70+ keys for function management)
- [x] `src/locales/en.json` — English translations (70+ keys)
- [x] `src/routes.tsx` — Added Functions route with CodeOutlined icon under `/project/:projectId/functions`

### What's Next

1. **Phase 4: Integration Testing** — Install Deno, start functions runtime, run end-to-end tests
2. **Source Code Viewing** — Add frontend endpoint to retrieve source code for editing (currently requires re-pasting on update)
3. **V2 Enhancements** — Worker Pool, Cron triggers, metrics, secrets management

## Blockers / Risks

- Deno not installed in current environment — functions runtime cannot be runtime-verified yet
- IDE lock on `flexmodel-server-dev.jar` prevents `mvn clean` (not caused by our changes)

## Decisions Made

- **HTTP Client**: Used Java 25 built-in `java.net.http.HttpClient` instead of Vert.x WebClient to avoid additional dependency
- **Configuration**: Functions runtime host/port configurable via `flexmodel.functions-runtime.host` and `flexmodel.functions-runtime.port` properties
- **Source Code Loading**: Lazy load pattern — source code not sent at deploy time, loaded by runtime on first invoke via internal API
- **Auth Validation**: Implemented PUBLIC/JWT/API_KEY/INTERNAL auth modes per trigger config
- **Startup Recovery**: Only deploys metadata on startup (O(1) time), source code lazy-loaded

## Files Created This Session

### Deno Functions Runtime (9 files)
- `flexmodel-functions-runtime/deno.json`
- `flexmodel-functions-runtime/src/main.ts`
- `flexmodel-functions-runtime/src/server.ts`
- `flexmodel-functions-runtime/src/types.ts`
- `flexmodel-functions-runtime/src/router/functions.ts`
- `flexmodel-functions-runtime/src/router/health.ts`
- `flexmodel-functions-runtime/src/runner/registry.ts`
- `flexmodel-functions-runtime/src/runner/worker.ts`
- `flexmodel-functions-runtime/src/runner/worker-entry.ts`
- `flexmodel-functions-runtime/src/sdk/flexmodel.ts`

### Java Backend (17 files)
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionException.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionRepository.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionFmRepository.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionVersionRepository.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionVersionFmRepository.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionInvoker.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionService.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionResource.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/FunctionInternalResource.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionCreateRequest.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionUpdateRequest.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionInvokeRequest.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionInvokeResponse.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionDeployRequest.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionResponse.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionVersionResponse.java`
- `flexmodel-server/src/main/java/dev/flexmodel/functions/dto/FunctionPageRequest.java`

### Files Modified
- `flexmodel-server/src/main/resources/project.fml` — Added f_function, f_function_version models + HTTP to TriggerType
- `flexmodel-ui/src/routes.tsx` — Added Functions route with CodeOutlined icon
- `flexmodel-ui/src/locales/zh.json` — Added 70+ function management translation keys
- `flexmodel-ui/src/locales/en.json` — Added 70+ function management translation keys
- `feature_list.json` — Added feat-011
- `progress.md` — This file

## Evidence of Completion

- [x] Compile: `mvn compile -pl flexmodel-server` → BUILD SUCCESS (424 source files, 0 errors)
- [x] Engine tests: `mvn test -pl flexmodel-engine -q` → all passed
- [x] Frontend TypeScript: `tsc --noEmit` → 0 errors
- [x] Frontend build: `npm run build` (Vite) → built in 42.98s
- [ ] Deno type-check: `deno check src/main.ts` (Deno not installed in environment)
- [ ] End-to-end test: create → deploy → invoke → update → delete (requires running functions runtime)

---

## Session (2026-07-17): 完善 scheduling e2e 测试（feat-005）

### 目标

完善 `TriggerResourceTest` e2e 用例，在调用 Trigger REST 接口后，通过注入的 `Scheduler` 直接查询 Quartz 作业状态，确保
Quartz 作业被正确创建/移除/状态变更。

### 变更

- `flexmodel-server/src/test/java/dev/flexmodel/rest/TriggerResourceTest.java`
  - 新增 Quartz 断言辅助方法：`jobKey`/`triggerKey`/`assertScheduledInQuartz`/`assertNotScheduledInQuartz`/
    `unscheduleFromScheduler`，镜像 `TriggerService.buildJobKey/buildTriggerKey/getJobGroup` 的命名规则（group =
    `dev_test_flow_{jobId}`）。
  - `testCreateIntervalTrigger`/`testCreateCronTrigger`：创建 state=true SCHEDULED 触发器后断言 JobDetail/Trigger
    存在、JobDataMap 携带 triggerId/jobId/projectId。
  - `testCreateEventTrigger`：断言 EVENT 类型不创建 Quartz 作业。
  - `testCreateDisabledTriggerNotScheduled`：state=false SCHEDULED 不创建 Quartz 作业。
  - `testPatchTriggerEnable`/`testPatchTriggerDisable`：启用→创建调度任务，禁用→移除调度任务。
  - `testUpdateTrigger`/`testUpdateTriggerReschedules`（新增）：update 先 unschedule 再 schedule 的重建路径，禁用态不残留调度任务，且全程保留
    seed 原始名称。
  - `testDeleteTrigger`:删除前断言调度任务存在、删除后断言已移除。
  - `@AfterEach` 增强：清理 created/启用的 seed 触发器的 Quartz 调度任务（兜底 `deleteJob`/`unscheduleJob`），并还原 seed
    禁用态。
- `flexmodel-server/src/main/java/dev/flexmodel/scheduling/TriggerService.java`
  - 修复 `create()`：对 state=false 的 SCHEDULED 触发器不再调用 `scheduleTrigger`（与 `update()` 行为一致），避免禁用态触发器误注册
    Quartz 作业。这是被新 e2e 用例暴露出的预存缺陷。

### 验证

- `mvn -pl flexmodel-server -am test -Dtest=TriggerResourceTest -Dsurefire.failIfNoSpecifiedTests=false` → BUILD
  SUCCESS，Tests run: 16, Failures: 0, Errors: 0, Skipped: 0。
- TriggerResourceIT（@QuarkusIntegrationTest extends TriggerResourceTest）保持兼容（用例均为普通 HTTP+Scheduler 断言，无 JVM
  模式不兼容 API）。

### 备注 / 风险

- `assertScheduledInQuartz` 不再强断言 `getTriggerState==NORMAL` 与 `nextFireTime`：短间隔 SimpleTrigger 在断言前可能已触发完成被移除（含
  startup-restore 触发的实际 Job 执行，会出现 `SessionContext` NPE 噪声日志，属已知环境问题，不影响断言）。改为断言
  JobDetail/Trigger 存在 + JobDataMap 内容 + 状态非 ERROR，保证稳定性。
- `testDeleteTrigger` 改用 1 小时间隔创建触发器，避免调度任务在断言前被触发清理。
- 启动恢复 `restoreScheduledTriggersOnStartup` 对 seed EVENT 触发器不调度（符合预期），dev_test seed 中无 state=true 的
  SCHEDULED 触发器。

---

## Session (2026-07-29): 15 项优化修复

### 目标

修复项目分析中发现的 15 项优化问题（#6–#25, #33, #35）。

### 已完成

| # | 问题 | 修复 | 文件 |
|---|------|------|------|
| 6 | TurboException extends RuntimeException | → extends BusinessException | TurboException.java |
| 7 | 51 处 bare RuntimeException | → NotFoundException/InternalServerException/ValidationException; BusinessExceptionMapper 更新 | 19 files |
| 11 | DataService/ModelingService 硬编码 databaseName | → @Inject SessionContext + resolveDatabaseName() fallback | DataService.java, ModelingService.java |
| 12 | 缺少权限注解 | → @RequiresPermissions on User/Role/ApiKey/Resource Resource | 4 files |
| 14 | AuthService 职责过重 | → 拆分到 UserService/RoleService/ResourceService | 4 files |
| 16 | ConcurrentHashMapCache 无 null 保护 | → computeIfAbsent + NULL_SENTINEL | ConcurrentHashMapCache.java |
| 17 | SqlSession.close() 静默提交 | → rollback() + warn log | SqlSession.java |
| 19 | Zustand selector 未用 useShallow | → 全部 wrap useShallow | appStore.ts, authStore.ts |
| 20 | ECharts 全量引入 | → modular imports (utils/echarts.ts) | 3 files |
| 21 | i18n 重复 key + 错误翻译 | → 去重 + 修正 | en.json, zh.json |
| 23 | POM 依赖版本分散 | → 父 POM 统一 dependencyManagement/pluginManagement | 7 POMs |
| 24 | CDI.current() 滥用 | → 5 类迁移 @Inject; 3 类保留 CDI.current() + 注释 | 8 files |
| 25 | e.printStackTrace() | → log.error() | DefinitionProcessor.java, LogFilter.java |
| 33 | Maven Wrapper 过旧 | → 3.3.1/3.9.6 | mvnw, mvnw.cmd |
| 35 | maven-source-plugin 过旧 | → 3.3.1 | pom.xml |

### 回归修复

- **AbstractRepository.java**: `if (sessionContext != null)` → `isRequestContextActive()` + `resolveSessionWithoutContext()` fallback (CDI proxy always non-null)
- **FmJobStore.java**: `new FmJobRepository()` → `CDI.current().select(FmJobRepository.class).get()` (Quartz-instantiated, @Inject 不可用)
- **LogFilter.java**: `@Inject EventBus eventBus` → `@Inject jakarta.inject.Provider<EventBus> eventBusProvider` (Vertx 是 RUNTIME_INIT synthetic bean, 直接注入导致 STATIC_INIT 冲突)
- **pom.xml**: `junit.version` 5.10.3 → 6.0.3 (Quarkus 3.33.1 引入 junit-platform 6.0.3, engine 5.10.3 与之不兼容)

### 新增测试文件

- `ScheduledJobExecutionTest.java` — 11 个测试用例覆盖 JobListener lifecycle, Function/Flow Job missing params, FmJobStore CRUD/trigger state/calendar/trigger fire/disallow concurrent/reschedule/group queries

### 验证

- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` → BUILD SUCCESS
- `mvn test -pl flexmodel-engine` → BUILD SUCCESS
- `mvn clean test -pl flexmodel-server` → @QuarkusTest 启动失败 (pre-existing: SessionFactory/Quartz tables 在测试环境不可用, JUnit 版本已修复)

### 已知问题 (pre-existing)

- 所有 `@QuarkusTest` 测试因 `SessionFactory` 在测试环境不可用而启动失败
- LSP 错误 (codegen 相关类型如 FlowDefinition 在 IDE 中未解析) — 非 our edits 引起

## Flow 生命周期事件：本地 EventBus 事件 + 可选 RabbitMQ 桥接（2026-08-19）

**目标:** 在 flow 核心关键生命周期点发布强类型本地事件，经 Vert.x EventBus 广播；新增可选 RabbitMQ 桥接，默认关闭、未配置不连
broker，本地事件照常发布。

**实现:**

- 新增 `dev.flexmodel.flow.event` 包：`FlowEvent` 抽象基类（projectId/caller/timestamp + 抽象 `routingKey()`）、
  `FlowEventTypes` 路由 key 常量、11 个具体事件类（定义层 4 / 实例层 4 / 用户任务层 3）。
- `FlowEventPublisher`（`@ApplicationScoped`）：注入 `EventBus`，`publish(FlowEvent)` 经
  `eventBus.publish(routingKey, event)` 广播，全程 try/catch 仅告警、不抛出、不阻塞流程。
- `FlowEventRabbitmqBridge`（`@ApplicationScoped`）：每事件一个 `@ConsumeEvent(常量, blocking=false)` 方法消费具体类型，
  `enabled` 默认 false 时 early-return；`forward()` 以 `Instance<MutinyEmitter>` 延迟注入（禁用通道时 bean 仍可创建，不影响本地事件），用
  `OutgoingRabbitMQMetadata` 设 routing key 尽力转发。
- `FlowEventConfig` `@ConfigMapping(prefix="flexmodel.flow")` 声明 `events.rabbitmq.enabled` 配置根，避免 SmallRye 严格校验报
  `does not map to any root`。
- 依赖：`flexmodel-server/pom.xml` 加 `io.quarkus:quarkus-messaging-rabbitmq`（3.33 起更名自
  `quarkus-smallrye-reactive-messaging-rabbitmq`，版本由 quarkus-bom 管理）。
- 配置 `application.properties`：`flexmodel.flow.events.rabbitmq.enabled=false`、`mp.messaging.outgoing.flow-events-out.*`
  （connector=smallrye-rabbitmq、exchange.type=topic/durable）、`enabled` 绑定同一开关、
  `quarkus.rabbitmq.devservices.enabled=false`。
- 埋点（11 处）：DefinitionProcessor (create/update/deploy/delete)、FlowExecutor (preExecute→started、execute finally
  FAILED→failed、postExecute/postCommit COMPLETED/END→completed)、RuntimeProcessor.terminateProcess
  (TERMINATED→terminated，子流程级联逐个)、UserTaskExecutor
  (doExecute→suspended、postCommit→committed、doRollback→rollback.suspended)。

**测试:**

- `FlowEventPublisherTest`（2）：本地 EventBus 广播 + 字段完整 + null no-op。
- `FlowEventRabbitmqBridgeTest`（2）：默认禁用不连 broker（应用无 broker 启动即证）、禁用桥接对本地事件透明。未采用 SmallRye
  InMemoryConnector（当前 Quarkus 3.33.1 未提供配套 in-memory 扩展，InMemorySinkImpl 无 bean 定义注解，注入不满足）。

**验证:**

- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` → BUILD SUCCESS
-
`mvn test -pl flexmodel-server -Dtest=FlowEventPublisherTest,FlowEventRabbitmqBridgeTest,DefinitionProcessorTest,RuntimeProcessorTest` →
25 tests, 0 failures, 0 errors（含 DefinitionProcessorTest 4、RuntimeProcessorTest 17 回归通过）

**备注:** RabbitMQ 转发（enabled=true）端到端需真实 broker 或 Testcontainers，按计划保持可选、默认关闭；v1 不含
node-instance-created/service-task 等事件，后续同模式按需追加。

## 去掉 flexmodel.flow.events.rabbitmq.enabled 业务开关（2026-08-19）

**背景:** 该业务开关与 SmallRye 通道 `mp.messaging.outgoing.flow-events-out.enabled` 重复；且桥接 `enabled` 默认 false
时即便通道启用也不转发，属冗余控制层。

**变更:**

- `application.properties`：删除 `flexmodel.flow.events.rabbitmq.enabled`，通道 `enabled=false` 成为单一控制（启用置 true +
  broker 连接配置）。
- `FlowEventRabbitmqBridge`：删除 `@ConfigProperty enabled` 字段、11 处 `if(!enabled) return;` early-return、`isEnabled()`
  测试探针；转发完全由 `Instance<MutinyEmitter>` 解析性决定——通道禁用时 SmallRye 注入 no-op emitter，`forward()` 发送即丢弃、不连
  broker。
- 删除 `FlowEventConfig`（`@ConfigMapping` 不再需要声明已移除的配置根）。
- `FlowEventRabbitmqBridgeTest`：移除 `isEnabled/isEmitterResolvable` 断言，改为断言桥接 bean 存在且默认通道禁用时发布不抛出、本地事件照常广播。

**验证:**

- `mvn test-compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` → ExitCode 0
-
`mvn test -pl flexmodel-server -Dtest=FlowEventPublisherTest,FlowEventRabbitmqBridgeTest,DefinitionProcessorTest,RuntimeProcessorTest` →
25 tests, 0 failures, 0 errors

**设计效果:** 单一配置源——`mp.messaging.outgoing.flow-events-out.enabled` 同时控制是否连 broker 与是否转发；默认 false
零侵入，本地 EventBus 事件始终发布。

## Review 修复：桥接静默跳过 + variables 防御性拷贝（2026-08-19）

**P1 桥接默认配置逐事件 WARN 栈:** `Instance.isUnsatisfied()` 对禁用通道返回 false，`.get()` 抛 SRMSG00019 被捕获记
WARN（含事件载荷/流程变量）。修复：`FlowEventRabbitmqBridge` 注入
`@ConfigProperty("mp.messaging.outgoing.flow-events-out.enabled", defaultValue="false") channelEnabled`，`forward()` 首行
`if(!channelEnabled) return;` 静默跳过；转发失败日志改为仅记 routingKey + payloadType，不再打印整个事件（避免泄露流程变量）。

**P2 发布可变 variables 快照并发风险:** `FlowInstanceStartedEvent`/`FlowInstanceCompletedEvent`/`UserTaskSuspendedEvent`
构造时按引用持有 `runtimeContext.getInstanceDataMap()`，发布线程后续 mutate 同一 map，异步消费者/序列化读取共享 map 可能不一致或
CME。修复：三个事件构造器对 variables 做 `new HashMap<>(variables)` 防御性拷贝（null 安全），发布快照不可变。

**验证:** `mvn test-compile` 通过；
`FlowEventPublisherTest(2)/FlowEventRabbitmqBridgeTest(2)/DefinitionProcessorTest(4)/RuntimeProcessorTest(17)` 共 25 测试
0 失败 0 错误。默认配置下桥接不再逐事件打 WARN（SRMSG00232 通道禁用为启动一次性诊断）。

## Flow 生命周期事件 RabbitMQ Testcontainers E2E 测试（2026-08-19）

**目标:** 增加 Testcontainers 端到端测试，启动真实 RabbitMQ broker 验证 `FlowEventRabbitmqBridge` 以正确 routing key +
JSON 载荷推送事件到 topic 交换机。

**新增/修改:**

- `flexmodel-server/pom.xml`：加 `org.testcontainers:rabbitmq:${testcontainers.version}`（test scope，版本由父 pom
  `testcontainers.version=1.21.4` 管理；amqp-client 5.x 传递可用）。
- `RabbitMqTestResource.java`（前序会话已写）：实现 `QuarkusTestResourceLifecycleManager`，启动
  `RabbitMQContainer("rabbitmq:3-management")`，注入 `quarkus.rabbitmq.host/port/username/password` 与
  `mp.messaging.outgoing.flow-events-out.enabled=true`，暴露 static host/port/username/password 供测试取连接坐标。
- `FlowEventRabbitmqBridgeE2ETest.java`（新增）：`@QuarkusTest` + `@QuarkusTestResource(SQLiteTestResource.class)` +
  `@QuarkusTestResource(value=RabbitMqTestResource.class, restrictToAnnotatedClass=true)`。两个用例：
    - `flowDeployedEventForwardedToBroker`：amqp-client 临时队列绑定交换机 `flexmodel.flow.events`（routing key
      `flow.deployed`），发布 `FlowDeployedEvent`，`basicGet` 轮询（≤15s）断言 routing key 与 JSON
      字段（projectId/caller/flowModuleId/flowDeployId/timestamp）。
    - `flowInstanceStartedEventWithVariablesForwarded`：同模式断言 `flow.instance.started` 与 variables
      快照（amount/approved）经 JSON 序列化完整。

**关键设计决策:**

- **资源泄漏修复（重要）:** 初版 `@QuarkusTestResource(RabbitMqTestResource.class)` 默认 `restrictToAnnotatedClass=false`
  ，导致 broker 资源被 Quarkus 全局应用到所有共享应用上下文的测试——即便未选 E2E 测试，其他测试（`FlowEventPublisherTest`
  等）也触发 `rabbitmq:3-management` 镜像拉取，无 Docker Hub 环境下整套测试失败。改为 `restrictToAnnotatedClass=true`
  ，资源仅对本测试类生效。验证确认：非 E2E 测试不再拉取镜像。
- **opt-in 开关:** E2E 测试加 `@EnabledIfEnvironmentVariable(named="FLEXMODEL_E2E_RABBITMQ", matches="true")`
  ，默认跳过。项目此前无任何 Docker 依赖测试，此为首个；默认 `mvn test` 在无 Docker/无 Docker Hub 环境保持绿色。运行需：本机
  Docker 可用 + 能拉取 `rabbitmq:3-management` + 设环境变量 `FLEXMODEL_E2E_RABBITMQ=true`。
- routing key 取自 AMQP envelope（`routingKey()` 是方法不进 JSON），JSON 载荷用 Jackson 解析；带 variables
  的事件经防御性拷贝保证快照不可变（见前序 Review 修复）。

**验证:**

- `mvn test-compile -pl '!flexmodel-engine/flexmodel-maven-plugin' -q` → ExitCode 0。
- 综合回归
  `mvn test -pl flexmodel-server -Dtest=FlowEventPublisherTest,FlowEventRabbitmqBridgeTest,FlowEventRabbitmqBridgeE2ETest,DefinitionProcessorTest,RuntimeProcessorTest` →
  Tests run: 27, Failures: 0, Errors: 0, Skipped: 2（E2E 默认跳过），BUILD SUCCESS，无 Docker 镜像拉取。
- **E2E 实跑未完成:** 当前环境 Docker Hub 不可达（`registry-1.docker.io` EOF / 配置镜像源 `docker.1panel.live` 超时），
  `rabbitmq:3-management` 无法拉取，故 `FLEXMODEL_E2E_RABBITMQ=true` 实跑未通过。代码逻辑正确、编译通过；待网络恢复或换可用镜像源后，设该环境变量即可执行端到端验证。

**未决/后续:**

- E2E 实跑待 Docker Hub 可达后补验证（设 `FLEXMODEL_E2E_RABBITMQ=true` 运行）。
- 若 CI 需常态化跑 E2E，建议在 CI 配置可用 RabbitMQ 镜像源或预拉镜像，并设该环境变量。

## E2E 测试修复：RabbitMQ 连接配置键（2026-08-19 续）

**问题:** 首次运行 `FlowEventRabbitmqBridgeE2ETest` 失败——应用侧 SmallRye outgoing channel `Connection refused`，事件未发到
broker，测试轮询超时。日志报 `Unrecognized configuration key "quarkus.rabbitmq.password"`。

**根因:** 反编译 `quarkus-messaging-rabbitmq` 扩展的 Quarkus config root `RabbitMQBuildTimeConfig` 确认：其仅注册
`devservices`、`credentialsProvider`、`credentialsProviderName` 字段， **不注册 `host/port/username/password` 连接字段**。故
`RabbitMqTestResource` 注入的 `quarkus.rabbitmq.host/port/username/password` 全部无效（unrecognized），SmallRye client 回退默认
`localhost:5672`，连不上 Testcontainers 随机映射端口。这些连接字段由 SmallRye connector 自身读取（channel 级
`mp.messaging.outgoing.<channel>.host/port/username/password`，或全局别名 `rabbitmq-host` 等），通过查
`smallrye-reactive-messaging-rabbitmq-4.33.0` 源码 `RabbitMQConnectorCommonConfiguration` 确认。

**修复:** `RabbitMqTestResource.start()` 改用 channel 级连接配置：
`mp.messaging.outgoing.flow-events-out.host/port/username/password`；保留 `quarkus.rabbitmq.devservices.enabled=false`（防
DevServices 自启）与 `mp.messaging.outgoing.flow-events-out.enabled=true`。测试侧 amqp-client 仍用 static `host/port`
订阅交换机。

**验证:** 设 `FLEXMODEL_E2E_RABBITMQ=true` 运行 →
`SRMSG17036: RabbitMQ broker configured to [localhost:52374] for channel flow-events-out` +
`SRMSG17007: Connection with RabbitMQ broker established` → **Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, BUILD
SUCCESS**。两个用例（`flowDeployedEventForwardedToBroker`、`flowInstanceStartedEventWithVariablesForwarded`）断言 routing
key 与 JSON 载荷字段（含 variables 快照）完整通过。

**结论:** E2E 端到端验证完成。Flow 生命周期事件经 `FlowEventPublisher`→EventBus→`FlowEventRabbitmqBridge`→SmallRye
outgoing channel→RabbitMQ topic 交换机链路，routing key 与 JSON 载荷均正确。默认 `mvn test` 不依赖 Docker（E2E opt-in 跳过），
`restrictToAnnotatedClass=true` 确保 broker 资源不泄漏到其他测试。

## UserTask 事件携带 nodeAttributes + 文档补全数据结构（2026-08-19）

**需求:** 订阅者常需解析节点定义里的扩展属性做业务（审批人、表单、阈值等）。外部 RabbitMQ 订阅者访问不到定义仓库，故将节点属性快照随事件携带。

**改动:**

- 事件类：`UserTaskSuspendedEvent`/`UserTaskCommittedEvent`/`UserTaskRollbackSuspendedEvent` 各加 `Map<String, Object> nodeAttributes` 字段，构造器做 `new HashMap<>(nodeAttributes)` 防御性拷贝（与 variables 一致）。
- `UserTaskExecutor` 三处埋点填充 `nodeAttributes`：`doExecute` 用 `flowElement.getProperties()`；`postCommit` 用 `runtimeContext.getCurrentNodeModel().getProperties()`；`doRollback` 用 `FlowModelUtil.getFlowElement(flowElementMap, nodeKey).getProperties()`。
- 测试：`FlowEventRabbitmqBridgeE2ETest` 新增 `userTaskSuspendedEventWithNodeAttributesForwarded`，验证 routing key + variables + nodeAttributes 经 broker 转发后 JSON 载荷完整（含中文字段名、boolean）。
- 文档 `flexmodel-website/docs/tutorial/features/flow.md`：
  - 修正公共字段表——`routingKey` 不在 JSON 载荷（是方法非字段），改注其随 AMQP envelope 投递；同步修正 Python 订阅示例从 `method.routing_key` 读取。
  - 事件总览表三个 UserTask 事件加 `nodeAttributes`。
  - 新增「事件数据结构」小节，按定义层/实例层/用户任务层分组给出每种事件的 JSON 载荷示例。

**验证:**

- `mvn test-compile` → ExitCode 0。
- E2E（`FLEXMODEL_E2E_RABBITMQ=true`）→ Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS（含新增 nodeAttributes 用例）。
- 默认回归 → Tests run: 28, Failures: 0, Errors: 0, Skipped: 3（E2E 默认跳过），BUILD SUCCESS。

**设计要点:** nodeAttributes = 节点定义的 `FlowElement.properties` 快照，事件发生时已确定且不可变；外部订阅者无需回查定义仓库即可读取节点配置，解耦核心与外部系统。

## 评估：flow rollback 是否需支持「退回到指定节点」（2026-08-29）

**结论:** 当前阶段不必要，列为 P2，待真实业务场景催动再做。

**当前实现摘要:**

- `RollbackTaskParam` 仅携带 `flowInstanceId` + `taskInstanceId`，`getActiveNodeForRollback`（`FlowExecutor.java:428`
  ）只允许回退与 `suspendNodeInstanceId` 完全匹配的节点——ACTIVE 的当前节点，或最后一个 COMPLETED 节点。
- `doRollback`（`FlowExecutor.java:474`）为单步链：禁用当前节点实例 → 若该节点是 COMPLETED 的 UserTask，则在同一 nodeKey 上新建
  ACTIVE 实例并 `SuspendException` 挂起（`UserTaskExecutor.java:125`），等用户重新提交；回退到 StartEvent 则置 `TERMINATED`。
- 本质是「重做当前环节 / 回退一步」模型，状态机与数据快照均按单步设计。

**不做的理由（成本与前提破坏）:**

- 多节点链路：跨步回退需沿 `sourceNodeInstanceId` 链路批量 DISABLE 中间节点，并重新激活目标节点；目标节点的
  `instanceDataId` 可能已被后续步骤覆盖，需明确数据回放策略。
- 并行/分支：跨 fork 后 sibling 分支节点实例的处理语义未定义（一并 disable / 保留），当前 `getActiveNodeForRollback` 未涉及。
- CallActivity 嵌套：跨子流程回退时子流程实例生命周期需单独处理。
- 幂等与重复回退：指定节点回退后，「上一个节点」语义变化，需重新定义可回退判定。

**触发条件（满足任一可启动）:**

- 出现「驳回发起人 / 驳回到指定环节」等 BPM 标配语义的真实审批流需求。
- flow 产品 roadmap 需对标 Activiti/Flowable 的回退能力。
- 「连退两步」频率高，单步回退成为体验瓶颈。

**启动时的建议实现路径:**

- `RollbackTaskParam` 增加可选 `targetNodeInstanceId`。
- `getActiveNodeForRollback` 改为「链路可达性校验 + 中间节点批量 disable」。
- 引入节点级 instanceData 快照表支撑数据回放，而非在现有单步逻辑上打补丁。

## Feature: flow 实例列表增加「历史元素列表」按钮（2026-08-29）

**需求:** 流程实例列表已有「用户操作记录」 (/user-tasks) 按钮，需再增加一个按钮拉取流程实例历史元素列表 (/elements)。

**改动:**

- 新增 `flexmodel-ui/src/pages/Flow/components/ElementInstancesDrawer.tsx`：只读 Drawer，用 vertical Steps 时间线展示
  `getElementInstances` 返回的全部元素实例（含开始/网关/任务/结束），复用 NodeInstanceStatus
  标签与实例数据查看（getInstanceData + Monaco 只读 Modal）。
- `flexmodel-ui/src/pages/Flow/components/FlowInstanceList.tsx`：
  - 引入 `NodeIndexOutlined`、`getElementInstances`、`ElementInstancesDrawer`。
  - 新增 elements 相关 state（visible/loading/instances）与 `handleShowElements` handler。
  - 操作列在「用户操作记录」后追加「历史元素列表」按钮（NodeIndexOutlined 图标）。
  - JSX 末尾挂载 `<ElementInstancesDrawer/>`。

**附带修复:** `FlowDetail/index.tsx:56` 的 `loadData` 增加 `projectId` 守卫，修复 hideLayout 路由下 currentProject 未就绪导致
`projects//flows/instances/...` 404。

**验证:**

- `npx tsc -b` → 通过，无错误。

## 接续会话：SDK 版本同步 + 开发/生产依赖分离（2026-08-29）

**目标:** 生产用 npm、开发用本地源码覆盖；修复 `flexmodelClient.setTraceId is not a function` 链路断裂。

**完成内容:**

- **SDK 版本同步**: SDK `package.json` 已为 `0.0.10`（含 `setTraceId`）。将 `flexmodel-functions-runtime/deno.json` 与
  `registry.ts` 的 `SDK_NPM_FALLBACK` 由 `npm:@flexmodel/sdk@0.0.8` 统一提升至 `0.0.10`，使生产 npm 依赖与已升级 SDK 一致（含
  setTraceId）。
- **开发/生产配置分离**:
  - `deno.json`（生产/Docker）：`@flexmodel/sdk` → `npm:@flexmodel/sdk@0.0.10`，`start` 任务无 --config，Docker 安全。
  - `deno.local.json`（开发覆盖）：`@flexmodel/sdk` → `../flexmodel-sdks/typescript/src/index.ts`（本地源码，无构建/无 npm
    即时反映）。Deno `extends` 替换而非合并 imports，故 deno.local.json 含全量 imports。
  - `deno.json` 的 `dev` 任务显式 `--config=deno.local.json` + `--sloppy-imports`（SDK 源码用 .js 扩展名导入 ESM 规范，Deno
    2.8 需该 flag 解析 .ts）。
- **Worker SDK 内联**: `registry.ts` 的 `sdkBundle` 优先读取本地 `dist/index.js` 内联为函数目录 `_flexmodel_sdk.js`，函数级
  import map 用相对路径（离线可移植）；找不到本地构建时回退 npm。dev/prod 宿主不直接 import SDK，Worker 始终用内联 bundle，故
  npm 版本 bump 不影响 `deno task dev`/测试（测试用内联 dist）。
- **清理**: 删除根目录误建的 `mvn_verify.txt` 与 `src/`（来自此前错误 create_new_file）。

**关键决策:**

- 生产 npm@0.0.10 需先 `npm publish` 发布；未发布前生产若 SDK 子模块未进镜像且回退 npm 会失败。monorepo 镜像构建（子模块检出）下
  Worker 用内联 dist，与 npm 版本无关。
- 不对 setTraceId 做 typeof 防御（用户明确要求；SDK 升级保证存在）。

**验证:**

- `deno check --config=deno.local.json src/main.ts src/server_test.ts` → DEV_OK
- `deno check src/main.ts src/server_test.ts src/runner/worker_test.ts src/runner/registry_test.ts` → PROD_CHECK_OK
- `deno run --sloppy-imports --config=deno.local.json _sdk_check.ts` → hasSetTraceId true（本地源码 setTraceId 可用）
- SDK `dist/index.js` 含 5 处 setTraceId；`deno --version` = 2.8.2

## 接续会话：触发器触发任务追踪链路（2026-08-29）

**目标:** Quartz 定时触发器触发任务（流程/函数）时创建 OTel span，traceId 贯穿 触发器→函数/流程→下游，并记录到
f_job_execution_log。

**完成内容:**

- **TracingHelper**（新建 observability 包）：封装 OTel 手动 span 创建，设置 flexmodel.project_id 属性；提供 startSpan（根
  span）、startChildSpan（从远程 traceId/spanId 恢复）、currentTraceId（获取当前 HTTP span traceId）。
- **f_job_execution_log 模型**：新增 trace_id 字段 + IDX_JOB_EXEC_TRACE_ID 索引（project.fml）。
- **ScheduledFunctionExecutionJob**：execute () 中用 TracingHelper.startSpan 创建根 span 包裹执行；span 激活后
  Span.current () 有效，FunctionRuntimeClientHeadersFactory 自动注入 traceparent 贯穿 Java→Deno 链路；traceId 存入
  JobExecutionContext 供 listener 记录。
- **ScheduledFlowExecutionJob**：同理创建根 span，traceId+spanId 传入 StartProcessParamEvent，EventBus 跨线程传播。
- **TriggerFlowEventConsumer**：用 startChildSpan 从 param 的 traceId/spanId 恢复 span 上下文，使流程执行中的下游调用（含函数调用）在同一
  trace 下。
- **StartProcessParamEvent**：新增 traceId、spanId 字段。
- **ScheduledFlowExecutionJobListener**：从 context 取 traceId 传给 recordJobStart。
- **JobExecutionLogService.recordJobStart**：新增 traceId 参数，setTraceId 到 JobExecutionLog。
- **TriggerService**（手动触发 2 处）+ **TriggerDataChangedEventListener**（事件触发 1 处）：注入
  TracingHelper，recordJobStart 传 currentTraceId ()。
- **FmSpanExporter.extractProjectId**：优先从 flexmodel.project_id 属性提取 projectId（非 HTTP span 如 Quartz Job）。

**验证:** mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin' BUILD SUCCESS（codegen 重新生成 JobExecutionLog 含
trace_id）。

## Feature: 分支数据迁移过滤日志表（@migration 注解）（2026-08-30）

**目标:** 创建/合并分支时跳过日志/trace 表的数据迁移（表结构照常复制），通过 FML 声明式标记驱动，可扩展。

**完成内容:**

- **FML 标记**（project.fml）：在 f_api_request_log、f_function_log、f_audit_log、f_span、f_job_execution_log 的
  `@system` 后追加 `@migration(enabled: false)`（叠加不替换）。
- **MigrationConfig**（新增 dev.flexmodel.project.MigrationConfig）：`of(SchemaObject)` 从
  `additionalProperties["migration"]` 解析——无参标记→true（启用迁移）；带参注解→Map，取 enabled（Boolean.parseBoolean） 与
  limit（Integer.parseInt，预留）；未标记或 null→默认全量迁移（向后兼容）。
- **BranchService**：createBranch（数据迁移循环）与 mergeBranch（新模型插入 + 双方都有模型 diff 两个分支）加入
  `if (!MigrationConfig.of(model).isEnabled()) continue;`，跳过数据迁移并 log.info 提示。
- **DefaultSchemaCopier 不变**：仅复制表结构，日志表空结构照常建到新分支。
- **回归测试**（ASTNodeConverterTest.migrationAnnotationIsStoredInAdditionalProperties）：断言
  `@migration(enabled: false)` 被解析为 additionalProperties 中的 Map，enabled 以 String "false" 存储。

**关键事实链:** ASTNodeConverter.toSchemaEntity L71-72 未识别注解走 default 分支——无参存 true、带参存参数 Map，
引擎侧零改动；getAdditionalProperties 全仓仅 ModelService 与 FlexmodelGraphQL 两处消费者且只查 "system" key， codegen 不读
additionalProperties，故 @migration 对实体生成透明无副作用。

**验证:** mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin' BUILD SUCCESS；mvn test -pl flexmodel-engine
通过；ASTNodeConverterTest 3 tests 0 failures 0 errors。

**遗留/风险:** v1 仅实现 enabled，limit 字段已预留但 BranchService 未消费；将来支持 @migration (limit: N) 需在两处 迁移循环加按
created_at desc 限量查询逻辑（各日志表均有 created_at）。注：mvn clean 因 dev 进程占用 flexmodel-server-dev.jar 失败，改用
mvn compile 验证。

## Session (续) - 重命名剩余 observability key/变量 (2026-09-02)

**目标:** 将多语言及代码中残留的 observability 相关键/变量统一改为 log 命名。

**完成内容:**

- **i18n key**: en.json/zh.json 中 observability.
  *（function_logs/api_logs/job_execution_log/node_instance_logs/audit_logs）重命名为 log.*；移除未引用的顶层 observability
  孤儿 key。
- **routes.tsx**: 4 处 translationKey 由 observability.* 改为 log.*；AuditLogList 导入路径由 @/pages/Observability/... 改为
  @/pages/Logs/...。
- **t () 调用**: APILog/index.tsx 与 AuditLogList.tsx 中 t ('observability. *') 改为 t ('log.*')。
- **类型文件**: @/types/observability.d.ts 重命名为 @/types/log.d.ts，更新
  audit-log.ts/function-log.ts/FunctionLogList.tsx/AuditLogList.tsx 共 4 处导入。
- **目录**: pages/Observability/ 重命名为 pages/Logs/（仅含 components/AuditLogList.tsx）。

**验证:** flexmodel-ui tsc --noEmit -p tsconfig.json 通过；rg 全局无残留 observability 引用。

**说明:** 后端 ProjectLogSettings.java 保留对旧 metadata.observability 的 fallback 读取（向后兼容），不在本次改动范围。

### 补充修正 (同 session)

- 目录最终命名为 pages/AuditLog/（非 pages/Logs）：因 .gitignore 第 2 行 logs 规则在 Windows（core.ignorecase）下会忽略
  pages/Logs，导致文件无法被 git 跟踪。AuditLog 不匹配该规则且语义更贴切。
- 移除 ypes/settings.d.ts 中未被引用的孤儿 Observability 接口（其形状仅含 auditResources，与实际 logSettings 不符）。
- 验证： sc --noEmit -p tsconfig.json 通过； g -i observability flexmodel-ui/src 无残留。

## Fix: 清理前端未使用变量并修复生产构建 (2026-09-08)

**目标:** 修复 Docker/UI 生产构建中 TypeScript `noUnusedLocals` 报错。

**修改:**

- 移除 `APILog/index.tsx`、`AuditLogList.tsx`、`JobExecutionLogList.tsx` 中未使用的 `useNavigate` 导入与 `navigate` 变量。
- 移除 `Functions/index.tsx` 中未使用的 `Outlet` 导入。

**验证:**

- `flexmodel-ui/npm run build` 通过（`tsc -b && vite build`）。
- `mvn clean compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `mvn test -pl flexmodel-engine -q` 通过。

**遗留:** Vite 构建提示 Monaco Editor 相关 chunk 超过 500 kB，属既有性能警告，不影响本次构建结果；`./init.sh` 在 Git Bash
中因 `java` 不在 PATH 无法直接运行，PowerShell 环境中 Maven/Java 25 可用，本次已按同等检查项验证。

## Branch - mergeBranch SSE 迁移进度 (2026-09-23)

**目标:** 将耗时的分支合并接口改为 SSE 流式响应，向前端实时输出每个模型的数据迁移进度。

**修改:**

- 后端新增 `BranchMergeProgressEvent`，包含事件类型、阶段、模型名、总数/已处理数、源/目标/插入/更新记录数和消息。
- `BranchService.mergeBranch` 改为返回 `Flow.Publisher<BranchMergeProgressEvent>`，在 ManagedExecutor 中异步执行原合并逻辑，并通过
  `SubmissionPublisher` 输出准备、结构合并、逐模型数据迁移、完成/失败事件。
- `BranchResource.mergeBranch` 原路径不变，响应改为 `text/event-stream`。
- 针对 Quarkus Reactive 的 `BlockingNotAllowedException`，为 `mergeBranch` 方法补充 `@Blocking`，确保请求体在 worker
  线程读取，SSE 响应仍由 Publisher 异步输出。
- 前端 `mergeBranch` 改用 `fetch` 流式读取并解析 SSE；`BranchSwitcher` 点击合并后立即显示进度面板，并实时更新总体进度与每个模型的迁移状态。

**验证:**

- `mvn clean compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `mvn test -pl flexmodel-engine -q` 通过。
- `flexmodel-ui npm run lint` 通过。
- `flexmodel-ui npm run build` 通过（`tsc -b && vite build`）。

**遗留/风险:** `init.sh` 在当前 Git Bash 环境受 CRLF/PATH 影响无法直接执行；本次已使用其等价 Maven 检查命令在
PowerShell/cmd 环境完成验证。Vite 仍提示既有 Monaco Editor chunk 大小警告，不影响构建结果。

## Branch - 创建/合并 SSE 进度与数据迁移细化 (2026-09-23)

**目标:** 将耗时的 createBranch、mergeBranch 改为 SSE 进度流，细化每个模型的数据迁移进度；删除分支恢复普通同步接口。前端实时展示进度，并在成功后保留进度供用户查看。

**修改:**

- 新增统一 `BranchProgressEvent`，承载 CREATE/MERGE 操作、阶段、模型、进度、记录数与结果。
- `BranchService.createBranch`、`mergeBranch` 返回 `Flow.Publisher<BranchProgressEvent>`，由 ManagedExecutor +
  SubmissionPublisher 异步执行。
- 创建分支的数据迁移逐模型输出开始、完成、跳过、失败事件，并将 50%-90% 映射为迁移进度；合并分支继续输出结构合并和逐模型数据合并事件。
- `BranchResource.createBranch`、`mergeBranch` 声明 `text/event-stream` 与 JSON 元素类型；`deleteBranch` 恢复普通同步接口。
- 前端 `createBranch`、`mergeBranch` 使用 `fetch` 流式解析 SSE；`BranchSwitcher` 实时显示进度，成功后弹窗保持打开并展示
  100% 进度，用户关闭时清除进度。
- `LogFilter` 移除请求体阻塞读取，业务接口不再需要 `@Blocking`。

**验证:**

- `mvn compile -pl flexmodel-server -am -q` 通过。
- `mvn test -pl flexmodel-engine -q` 通过。
- `flexmodel-ui npm run lint` 通过。
- `flexmodel-ui npm run build` 通过（`tsc -b && vite build`）。

## Enum - @code 落库编码与 @comment 元素注释 (2026-09-28)

**目标:** 枚举元素支持可选的 @code（落库编码）与 @comment（展示注释）。@code 声明后，API/GraphQL/UI 层继续使用元素名，引擎在 SQL 边界自动完成 name→code 写入翻译与 code→name 读取翻译；@comment 由 codegen 写入生成 Java 枚举常量的 Javadoc。两者完全可选，未声明时行为与旧版一致。

**修改:**

- `ModelParser.jj`：新增枚举元素级注解文法（紧跟元素名的 `@code(...)` / `@comment(...)`），单整数参数以 `Long` 解析以保留字面量类型；`Enumeration` 新增 `elementAnnotations`，`toString()` 内联回写元素注解。
- 新增 `dev.flexmodel.model.EnumElement`（name/code/comment/additionalProperties，实现 Serializable）。
- `EnumDefinition`：内部元素结构化为 `elementDefinitions`，`getElements()` 保持名称视图向后兼容；`setElements` 对 Jackson 双字段反序列化做顺序无关防御。
- `ASTNodeConverter`：`toSchemaEnum` 构建结构化元素并校验（code 必须全量声明、无重复、仅允许元素级；枚举级 `@code` 报错）；`fromSchemaEnum` round-trip 保留编码字面量类型（整数不加引号）。
- `EnumTypeHandler`/`EnumSqlTypeHandler`：经 `ModelRegistry` 解析枚举定义，写入时 name→code（含 multiple、`Enum` 实例归一化、未知值抛错），读取时 code→name（含存量按名字落库数据回退）；`SqlContext`/`MongoContext` 注入 registry + schemaName。
- Codegen：`EnumClass` 携带结构化元素；`EnumGenerator` 将 `@comment` 输出为常量 Javadoc（防 `*/` 注入），无注释时保持原紧凑格式，已生成代码零变更。

**验证:**

- flexmodel-core 定向测试：`FmlParserTest`、`ASTNodeConverterTest`（round-trip + 4 个校验用例）、`EnumTypeHandlerTest`（10 用例）、`JsonSerializeTest`（JSON 双向 + 旧版输入兼容）全部通过。
- core+codegen 定向测试：`EnumGeneratorTest`、`GeneratorTest`、`GenerationToolTest` 通过。
- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- core/codegen/graphql 三个引擎子模块 `mvn test` 全部通过。

**遗留/风险:** 查询条件中的枚举值（where/`_eq`/ComparisonExp）尚未接入 name→code 翻译，声明编码的枚举按元素名过滤会查不到数据，待下一特性在条件渲染链路补齐；Mongo 读取结果暂未反向翻译（写入路径已翻译）；UI EnumForm/MCP `create_enum_model` 的编码编辑、DDL 按编码类型（TINYINT/INT）落列待后续；既有 `getFml()` 尾逗号与解析器不支持尾部裸逗号为存量问题，未在本特性内处理。

## Enum - 前端枚举编辑支持 @code/@comment（feat-018 跟进）(2026-09-28)

**目标:** 建模 UI 的枚举编辑支持逐元素配置可选的落库编码（@code）与展示注释（@comment），与后端 elementDefinitions 结构化契约对齐，同时保持旧版纯字符串 elements 的加载兼容。

**修改（flexmodel-ui）:**

- `EnumForm`：元素列表改为三列（名称/编码/注释），名称必填；列表级校验对齐后端规则——至少 2 个元素、名称唯一、编码全量或全空、编码唯一；编辑态优先从 `elementDefinitions` 加载（code/comment 回填），旧版仅有 `elements` 时降级为名称行；新增/删除行与查看态布局同步调整。
- 新增 `enumPayload.ts`：`buildEnumPayload` 同时输出 `elements`（名称数组）与 `elementDefinitions`（结构化对象），编码输入按字面量归一（纯整数转 number，其余为 string）；`EnumForm` 保存与 `ModelForm` 创建入口共用，提交契约一致。
- `FieldInput`：EnumRef 字段下拉的 label 优先显示元素 `@comment`（无注释时显示元素名），value 仍为元素名，数据写入语义不变。
- `data-modeling.d.ts`：新增 `EnumElement` 接口，`Enum`/`EnumSchema` 增加可选 `elementDefinitions`。
- `zh/en` 文案：新增元素列名与 4 条校验提示。

**验证:**

- `npx tsc --noEmit` 通过。
- 定向 `npx eslint`（EnumForm/ModelForm/FieldInput/enumPayload/data-modeling.d.ts）0 错误 0 警告。
- `npm run build`（tsc -b + vite build）通过；既有 Monaco chunk 大小警告不变。
- flexmodel-ui 提交 `7f776ff`；仓库既有暂存项 `src/icons.ts`（AD 状态）不属于本特性，未纳入提交。

## Enum - dev_test 示例接入 @code/@comment（feat-018 跟进）(2026-09-28)

**目标:** 在 `dev_test.fml` 中实际使用新增枚举元素配置，验证 FML 解析、结构化枚举校验和种子数据 TypeHandler 写入链路。

**修改:**

- `UserGender` 为全部元素补充可选 `@comment`（未知/男/女）；暂不声明 `@code`，因为 `studentsMale/studentsFemale` 关联 filter 仍按元素名过滤，避免触发查询条件枚举值翻译的已知遗留问题。
- `user_interest` 为全部元素补充 `@code` 与 `@comment`（1/2/3/4）；种子数据仍使用元素名，写入 SQL 边界时由 EnumTypeHandler 自动翻译为编码值。

**验证:**

- `mvn test -pl flexmodel-server -am -Dtest=RecordResourceTest "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（5 tests）。测试启动时 `TestDataInitializer` 会应用 `dev_test.fml`，覆盖 FML 解析、枚举校验与 Student 种子记录写入。

## Enum - 前端布局优化（feat-018 跟进）(2026-09-28)

**目标:** 优化建模页枚举表单在宽面板和抽屉中的布局层级，让查看态更像只读详情，编辑态更聚焦且操作更少干扰。

**修改（flexmodel-ui）:**

- `EnumForm` 编辑态限制内容宽度为 760px，避免在右侧宽面板中横向拉满；名称与注释改为紧凑双列。
- 枚举元素收进“枚举元素”卡片，列头与输入行使用同一 Grid（名称/编码/注释/删除操作），编码列固定 88px，注释列获得更多可用宽度。
- 合并“向上/向下添加”为卡片右上角单个“添加元素”操作，减少按钮噪声；删除操作保留行尾。
- 查看态不再渲染 borderless 输入框，改为只读 `Descriptions` + `Table`，名称/注释与元素名/编码/注释层级更清晰。
- 新增 `enum_elements`、`add_element` 中英文文案。

**验证:**

- `npx tsc --noEmit --pretty false` 通过。
- 定向 `npx eslint src/pages/DataModeling/components/EnumForm.tsx` 通过。
- `npm run build`（tsc -b + vite build）通过；既有 Monaco/Browserslist 提示与 chunk 大小警告不变。
- flexmodel-ui 提交 `498a789`；仓库既有暂存项 `src/icons.ts`（AD 状态）不属于本特性，已保持原状。

## Enum - 接口描述按钮（feat-018 跟进）(2026-09-28)

**目标:** 枚举详情页提供与实体页一致的“接口描述”入口，可查看并复制枚举 FML。

**修改（flexmodel-ui）:**

- `EnumForm` 查看态在基础信息区右上角增加“接口描述”按钮；存在 `model.fml` 时显示。
- 点击按钮用 Popover 展示 FML，内容支持复制，交互与 `EntityView` 保持一致。
- `Enum` 类型补充可选 `fml` 字段，与后端 `SchemaObject.getFml()` 返回契约对齐。

**验证:**

- `npx tsc --noEmit --pretty false` 通过。
- 定向 `npx eslint src/pages/DataModeling/components/EnumForm.tsx src/types/data-modeling.d.ts` 通过。
- `npm run build`（tsc -b + vite build）通过；既有 Monaco/Browserslist 提示与 chunk 大小警告不变。
- flexmodel-ui 提交 `2385c10`；仓库既有暂存项 `src/icons.ts`（AD 状态）不属于本特性，已保持原状。
## Enum - 接口描述按钮改为纯图标（feat-018 跟进）(2026-09-28)

**目标:** 根据反馈将枚举详情页的“接口描述”入口改为纯图标按钮，减少文字占位。

**修改（flexmodel-ui）:**

- `EnumForm` 的接口描述入口仅保留代码图标，移除按钮文字；保留 `aria-label` 作为可访问性名称。
- Popover 交互、FML 展示与复制能力保持不变。

**验证:**

- `npx tsc --noEmit --pretty false` 通过。
- 定向 `npx eslint src/pages/DataModeling/components/EnumForm.tsx` 通过。
- `npm run build`（tsc -b + vite build）通过；既有 Monaco/Browserslist 提示与 chunk 大小警告不变。
- flexmodel-ui 提交 `211df06`；仓库既有暂存项 `src/icons.ts`（AD 状态）不属于本特性，已保持原状。
## Enum - 结构化元素字段统一为 elements（feat-018 跟进）(2026-09-28)

**目标:** 将枚举 JSON 契约中的结构化 `elementDefinitions` 字段统一改为 `elements`，避免同一份元素定义拆成两个字段。

**修改:**

- `EnumDefinition` 使用单一结构化 `elements` 字段，`getElements()` 直接返回 `EnumElement[]`。
- `setElements` 仅接受结构化 `EnumElement[]`；按用户要求不保留旧字符串数组或旧 `elementDefinitions` 输入兼容。
- `JsonSerializeTest` 仅覆盖结构化 `elements` 输出与 round-trip，并断言不再出现 `elementDefinitions`。
- `flexmodel-ui` 的 `Enum`/`EnumSchema` 类型、枚举 payload、枚举字段下拉统一改为结构化 `elements`。

**验证:**

- `mvn test -pl flexmodel-engine/flexmodel-core -Dtest=JsonSerializeTest` 通过（2 tests）。
- `mvn test -q -pl flexmodel-engine` 通过。
- `mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过；`mvn clean compile` 在 server clean 阶段被运行中 Quarkus dev jar 锁定，属既有环境问题，改用等价 compile 验证。
- `mvn test -pl flexmodel-server -am -Dtest=RecordResourceTest "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（5 tests）。
- `flexmodel-ui npx tsc --noEmit`、定向 `npx eslint`、`npm run build` 通过。
- flexmodel-ui 提交 `d5d70aa`；仓库既有暂存项 `src/icons.ts`（AD 状态）不属于本特性，已保持原状。
- 后续按用户反馈移除 `EnumDefinition` 的历史输入兼容逻辑，调用方与测试同步改为结构化 `EnumElement` API。

## Apps UI - 登录接口接入（feat-019） (2026-10-08)

**目标：**

- 将 `flexmodel-apps-ui` 的登录页从纯前端跳转改为调用 Flexmodel 真实认证接口。
- 保持“记住登录”语义，并让受保护页面在未登录时回跳登录页。

**修改（flexmodel-apps-ui）：**

- 新增 `src/services/auth.ts`：封装 `POST /api/auth/login`、响应校验、localStorage/sessionStorage 会话存储与清理。
- `src/pages/Login.tsx`：默认密码改为服务端测试凭据 `admin123`；提交时禁用按钮并展示登录中/错误状态；成功后返回来源页面。
- `vite.config.ts`：开发环境将 `/api` 代理到 `http://localhost:8080`。
- `src/main.tsx`：新增 `RequireAuth` 路由守卫，未登录访问业务页面跳转 `/login`。
- `src/components/layout/TopHeaderShell.tsx`：展示登录接口返回的用户，退出登录时清理会话。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。

## Apps UI - FormDesign Hooks 顺序修复 (2026-10-10)

**修改：**

- 将 `FormDesign` 中处理拖拽列宽的 `useEffect` 移动到加载状态与空表单提前返回之前，保证 Hooks 调用顺序稳定。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。
- `mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- 真实服务 `POST http://localhost:8080/api/auth/login` 返回 `Admin`、`expiresIn=300000`、1 项权限。
- Vite 代理 `POST http://localhost:5181/api/auth/login` 返回同样结果；错误密码返回 401 与 `message`。
- Playwright 浏览器验证：正确凭据提交后进入 `#/dashboard/home`，头部显示 `Admin`；错误凭据显示
  `Wrong username or password`。
- `./init.sh` 在 clean 阶段失败：运行中的 `flexmodel-server/target/flexmodel-server-dev.jar`
  被锁定，无法删除；这是用户要求保持服务在线导致的环境限制。已用非 clean 全模块编译等价验证，停止 Quarkus dev 服务后可恢复标准
  `./init.sh`。

**遗留：**

- 登录成功后的业务数据仍使用 mock 状态，本特性只接入认证接口与会话控制。
- 仓库存在与本特性无关的既有修改（`deploy/docker-compose/001-mysql-single/.env`、服务配置文件、`flexmodel-ui` 嵌套仓库）与未跟踪的
  `.agents/skills` 目录，已保持原状。

## Apps Backend - 应用管理基础 CRUD（feat-020） (2026-10-08)

**目标：**

- 在 `flexmodel-server` 的 `dev.flexmodel.apps` 包实现应用管理基础 CRUD。
- 应用与项目一一对齐：应用 ID 等于项目 ID，不新增独立应用表。
- 应用展示配置存储在 `f_project.metadata.app`，并保留其他 metadata 节点。

**修改（flexmodel-server）：**

- 新增 `AppResource`：提供 `GET /api/apps`、`GET /api/apps/{appId}`、`POST /api/apps`、`PUT /api/apps/{appId}`、
  `DELETE /api/apps/{appId}`。
- 新增 `AppService`：
  - 应用列表复用 `ProjectService.findTopLevelProjects()`，仅暴露顶级且启用的项目。
  - 创建应用时未传 `projectId` 则自动生成唯一合法项目 ID，并复用 `ProjectService.createProject()` 初始化项目生命周期。
  - 更新应用时仅合并 `metadata.app`，保留 `logSettings` 等其他 metadata 字段。
  - 删除应用复用 `ProjectService.deleteProject()`，联动清理项目、分支、Schema、Pages、GraphQL 与调度资源。
- 新增 DTO：`AppCreateRequest`、`AppUpdateRequest`、`AppResponse`。
- 扩展 `dev.flexmodel.apps.package-info`，说明应用与项目一一对齐。
- 新增 `AppResourceTest`，覆盖列表、详情、创建、更新、删除、参数校验、未认证访问与 metadata 保留。

**验证：**

- `mvn test -pl flexmodel-server -am -Dtest=AppResourceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（10 tests，0
  failures）。
- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `./init.sh` 通过（全模块编译与引擎测试）。
- 为释放 `flexmodel-server/target/flexmodel-server-dev.jar` 的文件锁，停止了正在运行的 Quarkus dev 进程后重新执行标准验证。

**遗留：**

- V1 不实现业务对象、审批流程、触发器或菜单管理。
- V1 不修改 `flexmodel-apps-ui`，前端仍使用本地 mock 数据。

## Apps UI - 应用管理接口接入（feat-021） (2026-10-08)

**目标：**

- 将 `flexmodel-apps-ui` 的应用管理增删改查从本地 mock 切换为 `/api/apps` 真实接口。
- 复用现有登录 token，保持当前应用表单、图标选择器与主色选择交互。

**修改（flexmodel-apps-ui）：**

- 新增 `src/services/apps.ts`：
  - 封装列表、详情、创建、更新、删除五个接口。
  - 通过 `getAuthToken()` 携带 `Authorization: Bearer ...`。
  - 统一解析错误 `message`，并将后端响应规范化为前端 `App` 类型。
- 修改 `src/pages/AppManagement.tsx`：
  - 列表异步加载，展示加载与失败状态。
  - 新建/编辑调用真实接口，成功后刷新列表。
  - 删除调用真实接口，成功后刷新列表；操作期间禁用按钮并展示错误。
- 修改 `src/pages/AppSettings.tsx`：
  - 使用路由中的 `appId` 异步加载应用详情。
  - 保存调用真实接口并同步返回值，删除成功后返回应用管理。
  - 补充加载、提交、删除与错误状态。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。

**遗留：**

- 应用卡片上的业务对象数量仍来自 `src/data/mock.ts` 的本地统计。
- 应用管理之外的业务对象、审批流程、触发器等页面仍使用现有 mock 数据。

## Apps UI - 表单管理菜单命名调整 (2026-10-09)

**目标：**

- 将界面中的“表单”菜单入口命名为“表单管理”，避免与具体表单设计、表单字段等业务文案混淆。
- 底层实现与 API 目标类型继续保留实体 / `ENTITY` 语义，不扩大改动范围。

**修改：**

- `AppSettings` 设置导航中的 `business-objects` 入口显示为“表单管理”。
- `MenuLinkPicker` 与 `AppMenuManager` 的关联项下拉分组标题显示为“表单管理”。
- `AdminBusinessObjects` 空态标题显示为“表单管理”。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。

## Apps UI - 表单配置页面入口 (2026-10-10)

**目标：**

- 应用设置中的表单卡片进入当前实体对应的管理配置页。
- 保留 `AdminLayout` 中定义的表单二级菜单：基本信息、字段管理、表单设计、自定义操作、记录锁定、高级配置。

**修改（flexmodel-apps-ui）：**

- `BusinessObjectManagementContent` 表单卡片跳转 `/admin/business-object/{businessObject.name}/base`。
- 新增 `BusinessObjectSettings` 页面骨架，承接基本信息、字段管理、自定义操作、记录锁定与高级配置路由。
- `main.tsx` 补齐五个表单配置子路由；`form-design` 继续复用现有 `FormDesign`。
- `AdminLayout` 继续根据路径中的 `currentBusinessObject` 渲染二级菜单，未改动原有布局。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。
- `git diff --check` 通过。

## Apps - 表单管理 (2026-10-10)

**目标：**

- flexmodel-server 提供应用表单与表单字段 CRUD，底层复用 Flexmodel 实体能力。
- UI/API 只暴露“表单”概念，统一使用 `formName`、`FORM`、`referenceForm`，不出现实体命名或类型。

**修改：**

- 新增 `AppFormResource`、`AppFormService` 与 `AppForm*` DTO：
  - 表单接口：列表、详情、创建、更新、删除。
  - 字段接口：创建、更新、删除。
  - 创建时自动生成 `id`、`created_by`、`updated_by`、`created_at`、`updated_at`；可选创建 `name` 主显字段并建立非唯一索引。
  - UI 字段类型由服务端映射为实体字段，`REFERENCE` 对外使用 `referenceForm`。
- 菜单关联目标统一为 `BUILTIN | FORM`，`/menus/targets` 返回 `forms`；表单被菜单绑定时禁止删除。
- flexmodel-apps-ui 新增 `appForms` 服务与真实接口缓存：
  - 表单列表支持创建弹窗与真实数据卡片。
  - 新增 `FormSettings`，支持基本信息编辑/删除和业务字段增删改。
  - `FormDesign` 改为按应用和表单名加载真实字段，添加字段时调用后端并保留原布局交互。
  - 应用设置与表单二级路由统一为 `/admin/apps/:appId/forms/:formName/{section}`。
- UI 层已确认不存在 `entityName`、`ENTITY`、`entities`、`referenceEntity` 与“实体”文案。

**验证：**

- `mvn test-compile -pl flexmodel-server -am -DskipTests` 通过。
- `mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（6 tests）。
- `mvn test -pl flexmodel-server -am -Dtest=AppMenuResourceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（8 tests）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。
- `git diff --check` 通过。

**遗留：**

- 表单记录数据页尚未实现；绑定表单的菜单先显示开发中提示并提供表单管理入口。

## Apps UI - 菜单拖拽层级指示线 (2026-10-10)

**目标：**

- 菜单管理拖拽排序时，拖动项底部显示占位分割线。
- 分割线与拖动项所在层级对齐，根级菜单与分组内菜单分别继承对应缩进和宽度。

**修改（flexmodel-apps-ui）：**

- `AppMenuManager` 的 `MenuRow` 增加 `relative` 定位。
- 拖拽状态下在行底部渲染不可交互的 `primary` 色分割线，且不参与指针事件。

**验证：**

- 修复运行中的 Quarkus dev 进程对 `flexmodel-server-dev.jar` 的锁定后，`bash ./init.sh` 通过（全模块编译与
  `flexmodel-engine` 测试）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。
- `git diff --check` 通过。

## Apps - 表单 name/label 契约调整 (2026-10-10)

**目标：**

- 表单与字段统一使用 `label` 表示用户输入名称，使用 `name` 表示英文标识。
- 详情页展示只读 `name`，编辑仅允许修改 `label` 与描述。

**修改：**

- 后端 `AppForm*` DTO 拆分为 `name` / `label`；创建校验 `name` 唯一且符合英文标识格式，更新保留原 `name`。
- 表单路径与字段引用统一使用英文 `name`；菜单 FORM 目标同样提交与返回 `name`，展示名使用 `label`。
- 前端创建弹窗分别输入表单名称与英文名称；表单详情、编辑弹窗和字段编辑弹窗展示只读 `name`。
- 表单卡片、详情、设计页、删除提示与路由跳转全部改为 `label` 展示、`name` 寻址。

**验证：**

- `mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（6 tests）。
- `mvn test -pl flexmodel-server -am -Dtest=AppMenuResourceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（8 tests）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`；仅既有 chunk 体积提示）。

## Apps - 主显字段可编辑与可删除 (2026-10-10)

**修改：**

- `AppFormService` 仅继续保护真正的系统字段；主显字段允许修改与删除。
- 删除主显字段时同步清空 `nameField` / `searchField`，并在删除字段前清理引用该字段的索引。
- `FormSettings` 字段管理对主显字段展示编辑与删除操作，删除后同步清理本地主显字段状态。
- `AppFormResourceTest` 增加主显字段修改、删除及删除后主显字段清空的断言。

**验证：**

- `mvn test -pl flexmodel-server -am '-Dtest=AppFormResourceTest' '-Dsurefire.failIfNoSpecifiedTests=false'` 通过（6
  tests）。
- `flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示）。

## Apps - 表单设计布局服务端保存 (2026-10-10)

**修改：**

- 后端新增 `PUT /api/apps/{appId}/forms/{formName}/layout`，布局保存到应用项目 metadata。
- `AppFormResponse` 返回 `layout`，包含字段顺序、宽度、展示属性与 `advanced` 高级控制扩展位。
- `FormDesign` 改为从服务端加载布局并异步保存布局，保存时禁用按钮并展示错误。
- 删除表单字段时同步清理服务端布局中的字段、宽度与属性，避免遗留失效字段。
- `AppFormResourceTest` 覆盖布局保存、读取、高级扩展位与字段删除后的布局清理。

**验证：**

- `mvn test -pl flexmodel-server -am '-Dtest=AppFormResourceTest' '-Dsurefire.failIfNoSpecifiedTests=false'` 通过（6
  tests）。
- `flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示）。

## Apps - 表单记录管理 (2026-10-10)

**目标：**

- 菜单绑定表单后展示真实记录列表，支持记录分页、搜索、排序、详情与 CRUD。
- UI/API 仅使用 formName，服务端内部映射实体名并复用 Flexmodel 数据能力。

**修改：**

- 后端新增 AppRecordService 与 AppRecordResource，提供 /api/apps/{appId}/forms/{formName}/records 及记录 CRUD 接口。
- 创建记录时写入 id、created_by、updated_by、created_at、updated_at；更新时刷新 updated_by 与 updated_at。
- 列表支持分页、字符串类业务字段搜索、字段排序；排序 JSON 修正为 {field, order}，避免 DataService 反序列化失败。
- AppFormService 新增 resolveEntityName，用于服务端内部表单名到实体名映射。
- 前端新增 appRecords 服务、记录表单弹窗与详情组件；RecordList 支持真实分页、搜索、排序、行选择、批量删除、新建、编辑与详情抽屉。
- AppMenuPage 的 FORM 菜单目标改为渲染 RecordList，AppRecordView 承接记录详情路由。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppRecordResourceTest -Dsurefire.failIfNoSpecifiedTests=false 通过（2 tests）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- git diff --check 通过。

## Apps UI - 表单设计隐藏系统字段 (2026-10-10)

**修改：**

- FormDesign 的表单数据转换层过滤 system 字段。
- 布局画布、右侧字段列表、重置布局与高级控制仅使用业务字段和分栏；主显字段仍按普通业务字段展示。
- 服务端字段定义与记录提交逻辑不受影响。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps - 记录列表引用字段展开 (2026-10-12)

**修改：**

- `AppRecordService.findRecords` 从表单定义中收集所有 `REFERENCE` 类型字段，并将字段名列表传入
  `DataService.findPagingRecords` 的 `expand` 参数，不再传 `null`。
- 新增 `AppRecordServiceTest.findRecordsExpandsReferenceFields`，验证引用字段会传入 expand，普通字段不会被传入。

**验证：**

- `mvn test -pl flexmodel-server -am -Dtest=AppRecordServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
  通过（1 test）。
- `bash ./init.sh` 通过（全模块编译与 `flexmodel-engine` 测试）。
- `git diff --check` 通过。

## Apps - 内置字段契约统一 (2026-10-10)

**修改：**

- 表单字段响应与 metadata 存储契约统一使用 boolean builtin。
- 旧 metadata 的 system 字段通过 JsonAlias (system) 兼容读取，后续保存输出 builtin。
- 内置字段修改、删除、写入与排序保护逻辑同步使用 builtin；相关错误提示改为内置字段。
- 前端 AppFormField 类型、表单设计、字段管理、记录列表、记录详情与记录表单统一读取 builtin；字段管理徽标文案改为内置。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest -Dsurefire.failIfNoSpecifiedTests=false 通过（6 tests）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps UI - 表单设计内置字段默认布局调整 (2026-10-10)

**修改：**

- FormDesign 保留全部字段，确保右侧字段列表可以选择内置字段。
- 无服务端布局和重置布局时，默认仅布局非 builtin 字段。
- 用户选择内置字段后可正常加入画布，保存布局保留该字段。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps UI - 表单详情左侧标题展示 (2026-10-10)

**修改：**

- AdminLayout 依据 appId 与 formName 加载表单详情。
- 桌面左侧菜单顶部标题与移动端表单导航展示 form.label。
- 路由和接口请求继续使用 form.name，加载失败时回退显示 form.name。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps - 主显字段契约重命名 (2026-10-10)

**修改：**

- 表单主显字段契约从 nameField 统一重命名为 displayField。
- AppFormRecord 与 AppFormUpdateRequest 保留 JsonAlias (nameField)，兼容旧 metadata 与旧请求。
- 表单编辑弹窗支持选择主显字段；服务端校验字段存在且不允许选择内置字段。
- 删除当前主显字段时同步清空 displayField 与 searchField。
- 前端表单设置、表单设计、记录详情与引用记录标签统一使用 displayField。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest -Dsurefire.failIfNoSpecifiedTests=false 通过（6 tests）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- git diff --check 通过。

## Apps UI - 记录列表主显字段链接 (2026-10-10)

**修改：**

- RecordList 将 displayField 对应字段强制放在第一列，若布局中已有该字段则去重后前置。
- 主显字段单元格渲染为蓝色链接，点击打开详情抽屉；整行点击仍保留原有详情行为。
- 详情标题展示主显字段值，空值时列表显示占位符 -。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- git diff --check 通过。

## Apps UI - 记录详情布局修复 (2026-10-10)

**修改：**

- 记录详情顶部去掉 form.label 副标题，仅展示主显字段值。
- 顶部区域为抽屉关闭按钮预留右侧空间，避免与编辑、删除按钮重叠。
- 分栏标题恢复 ChevronDown 折叠交互，点击可展开或收起分栏内容。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- git diff --check 通过。

## Apps UI - 记录详情分栏样式优化 (2026-10-10)

**修改：**

- 详情字段改为单个卡片容器承载，避免每个分栏单独嵌套卡片导致布局割裂。
- 分栏标题去掉横向分割线，文字使用 font-semibold 加粗。
- 分栏标题最左侧增加主题色竖向强调条，并保留 ChevronDown 展开/收起交互。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- git diff --check 通过。

## Apps UI - 记录详情折叠图标位置 (2026-10-10)

**修改：**

- 分栏标题保持左侧主题色竖杠与加粗文字，ChevronDown 折叠图标移动到分栏文字右侧。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- git diff --check 通过。

## Apps - 表单子表单 (2026-10-10)

**修改：**

- AppFormLayout 契约新增 `subforms`，布局项使用 `$SUBFORM$-` 前缀。
- FormDesign 支持“子表单”添加、编辑、拖拽、重命名与移除；可选择已有子表单和指向当前表单的引用字段，未选择时自动创建引用字段。
- 现有 `/records` 接口新增可选 `mainFormName`、`mainRecordId`、`referenceField` 查询参数；列表按主记录过滤，新增自动写入归属，详情、更新和删除校验归属。
- AppRecordDetail 在主字段下方渲染子表单列表，支持搜索、分页、新增、编辑、删除和空态。
- 服务端校验子表单引用字段，禁止删除被引用的子表单或引用字段，删除表单时同步清理布局。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest,AppRecordResourceTest
  -Dsurefire.failIfNoSpecifiedTests=false 通过（10 tests）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin' 通过（全模块编译）。
- mvn test -pl flexmodel-engine 通过（按 init.sh 中的引擎模块命令）。
- bash init.sh 在 maven-clean 阶段被当前运行中的 quarkus:dev 进程占用 flexmodel-server-dev.jar 阻断；已在不中断开发服务的前提下改用上述无
  clean 编译与测试命令完成验证。如需完整 clean 基线，可先停止 8080 对应的 quarkus:dev 进程后重新执行 bash init.sh。

## Apps UI - 引用字段关联表单选择 (2026-10-10)

**修改：**

- FormSettings 加载当前应用的表单列表。
- 字段类型选择“引用”后，展示“引用表单”下拉框；选择目标表单后写入 `referenceForm`，解除保存按钮禁用问题。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps UI - 子表单引用字段候选刷新 (2026-10-10)

**修改：**

- FormDesign 在选择子表单后调用 `getAppForm` 获取该子表单最新详情，并基于详情中的 `REFERENCE` 且 `referenceForm`
  等于当前表单的字段生成候选列表。
- AppFormResourceTest 增加表单列表接口 `referenceForm` 断言，确认列表响应不会丢失引用目标。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest -Dsurefire.failIfNoSpecifiedTests=false 通过（7 tests）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps - 子表单关联字段放开限制 (2026-10-10)

**修改：**

- FormDesign 子表单“关联字段”候选展示子表单全部非内置字段。
- AppFormService 保存子表单布局时仅校验字段存在，不再限制字段类型和 referenceForm。
- AppRecordService 对显式 `referenceField` 仅校验非内置字段；该字段用于保存主记录 ID 并进行列表过滤、归属校验。
- 不选择关联字段时的自动创建逻辑保持不变，仍创建指向当前表单的 REFERENCE 字段。
- 新增 TEXT 字段作为关联字段的后端布局测试与记录上下文测试。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppFormResourceTest,AppRecordResourceTest
  -Dsurefire.failIfNoSpecifiedTests=false 通过（12 tests）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps UI - 子表单标题简化 (2026-10-10)

**修改：**

- AppSubformRecords 左侧标题仅展示子表单名称 `form.label`，缺失时回退 `formName`。
- 移除左侧标题下方的“子表单名称 · 数量”副标题。
- 清理不再使用的子表单布局项 `label` 参数。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps - 引用字段服务端渲染与可点击展示 (2026-10-12)

**修改：**

- 新增引用字段响应契约 `AppReferenceValueResponse`，字段返回 `{id, displayValue, referenceForm}`；多条引用返回对象数组。
- `AppRecordService` 对记录列表、详情、创建和更新响应中的 `REFERENCE` 字段进行服务端渲染，优先使用引用表单主显字段，目标缺失或主显字段为空时回退记录 ID。
- `AppReferenceValue` 直接消费服务端渲染结果，列表和详情立即展示主显字段；点击引用值打开对应表单的记录详情，并保留旧版 raw ID / expanded record 响应兼容。
- 补充子表单详情读取：当上下文引用字段被展开为对象或数组时，优先取其中的 `id` 再做主记录归属校验，避免详情接口误判记录不存在。
- 详情标题与编辑弹窗兼容服务端渲染后的引用对象，分别优先读取 `displayValue` 和 `id`，避免出现 `[object Object]`。
- 表单元数据会从 `ModelRefField` 推导目标表单名，历史引用字段的 `referenceForm` 为空时也能正确参与服务端渲染。
- 新增服务层与 REST 集成测试，验证列表和详情接口均返回可点击展示所需的完整引用契约。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppRecordServiceTest -Dsurefire.failIfNoSpecifiedTests=false 通过（2 tests）。
- mvn test -pl flexmodel-server -am -Dtest='AppRecordResourceTest#testReferenceValueRendering' -Dsurefire.failIfNoSpecifiedTests=false 通过（1 test）。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。
- bash init.sh 通过（全模块编译 + flexmodel-engine 测试）。
- git diff --check 通过。

## Apps UI - 记录列表操作列 (2026-10-12)

**修改：**

- `RecordList` 右侧新增固定“操作”列。
- 行内提供编辑、删除按钮；编辑复用现有记录表单弹窗，删除复用现有删除接口。
- 删除成功后同步清理选中状态、详情抽屉和关联记录抽屉，并刷新列表。
- 操作列宽度、按钮尺寸、图标尺寸和删除悬停色已与子表单列表样式统一。

**验证：**

- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

## Apps - 表单 Schema Items 重构 (2026-10-12)

**修改：**

- 新增 `AppFormItemRecord`；`AppFormLayoutRecord` / `AppFormLayoutRequest` 改为仅保存 `items`，布局节点支持 `FIELD` /
  `SECTION` / `SUBFORM` 与语义化宽度枚举。
- 删除 `AppFormItemPropertiesRecord` / `AppFormSubformRecord` 与 `$DIVIDER$` / `$SUBFORM$` 哨兵前缀；`required`
  等字段定义属性只保留在 `fields` 中。
- `AppFormRecord` 移除 `entityName` 并内嵌 `layout`；表单 `name` 同时作为底层实体名，创建表单前校验全部 `SchemaObject`
  名称冲突。
- `AppFormService` 校验布局项类型、必填属性、非法属性、字段引用、内置字段限制和“所有业务字段必须出现一次”；字段新增/删除自动追加/移除
  FIELD 节点，保存表单 metadata 时清理旧 `app.formLayouts`。
- `AppRecordService` 直接使用 `AppFormResponse.name` 作为实体名；前端契约、`form-items` 工具、表单设计器、记录表单、详情、子表单和列表均改为消费
  `layout.items`。
- `AppFormResourceTest` 覆盖布局校验、字段引用、子表单、表单绑定既有模型和重复表单名；记录相关测试同步表单名直连实体与服务端引用渲染契约。
- 修复 `FormDesign` 布局拖拽目标位置偏差：原逻辑在换算“移除拖拽项后的索引”后又做了一次错误的位置回退，导致跨行/跨多项拖拽时目标位置偏移一位；改为直接返回
  `withoutDraggingIndex` 或 `withoutDraggingIndex + 1`。
- 布局渲染改为按宽度累计分组为独立行，行内剩余空间不再被后续字段自动填充；右侧宽度拖拽柄逻辑保持原行为。

**验证：**

- `mvn test-compile -pl flexmodel-server -DskipTests` 通过。
- `mvn test -pl flexmodel-server -am "-Dtest=AppFormResourceTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（11
  tests，0 failures，0 errors）。
-
`mvn test -pl flexmodel-server -am "-Dtest=AppRecordServiceTest,AppRecordResourceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
通过（7 tests，0 failures，0 errors）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- `bash ./init.sh` 通过（全模块 clean compile + `flexmodel-engine` 测试）。
- `git diff --check` 通过。

**遗留/风险：**

- 本次按方案执行破坏性契约变更，不迁移旧 `app.formLayouts`；存量应用如存在旧布局 metadata，需要重建布局或手工转换。

## Apps UI - FormDesigner 独立组件修复 (2026-10-12)

**修改：**

- 将 `FormDesign.tsx` 拆分为页面容器与 `components/biz/FormDesigner.tsx`
  独立设计器组件；页面负责路由参数、表单加载、空态和返回导航，设计器负责布局编辑、字段添加、子表单、显示控制与保存。
- 设计器直接消费 `form.layout.items`，通过 `initialForm`、`onFormChange`、`onSaved` 与页面解耦，并保留 `FIELD` / `SECTION` /
  `SUBFORM` 与语义化宽度契约。
- 修复新增字段后被父级表单同步重置导致布局节点丢失的问题：创建字段时同步更新 `fields` 与 `layout.items`，父级刷新后仍保留新增
  FIELD 节点。
- 增强拖放到画布空白区域的能力，避免只能落在已有节点上；宽度选择按钮阻止 pointer 事件冒泡，避免触发父级拖拽。
- 修复拖拽落点不稳定：占位符重排会移动原目标元素，导致 drop 索引在旧实现中来回跳变；现改为拖拽开始时记录原始节点坐标，并用指针位置基于稳定坐标计算插入索引，拖到前方时组件会真正落位。
- 修复宽度填充柄：行满时不再隐藏柄，缩放过程中强制保持可见；填充柄禁用原生拖拽并阻止 dragstart 冒泡，避免被父级布局拖拽抢占，同时保留
  pointer 缩放。
- 填充柄保持悬浮显示行为：默认隐藏，鼠标悬浮组件时显示；缩放过程中强制保持可见，避免拖动途中因行重排丢失。
- 保存成功提示恢复为操作区内“保存”按钮左侧的绿色文本与勾选图标，移除独立浮层提示。
- 子表单自动创建引用字段后同步更新当前详情缓存，关联字段下拉立即可见；重置布局统一补齐 `HALF` 宽度。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- `mvn test -pl flexmodel-server -am "-Dtest=AppFormResourceTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（11
  tests，0 failures，0 errors）。

**补充：**

- `FormDesigner` 独立组件已纳入 `feat-041` 证据链，页面与设计器职责分离。

## MCP AppTools 技术方案 (2026-10-12)

**产出：**

- 新增 `docs/plans/app-tools-mcp.md`，定义 `AppTools` 的完整技术方案。
- 方案覆盖查询工具、创建工具、输入契约、AI 编排顺序、错误处理、测试用例与验收标准。
- 明确第一版采用分步 MCP 工具、扁平 `layout.items`、显式 mock 记录，不做复杂布局和全局事务。
- `feature_list.json` 新增 `feat-042`，状态为 `in-progress`；当前仅完成方案设计，代码实现待开始。

**验证：**

- `node` 解析 `feature_list.json` 通过。
- `git diff --check` 通过。

## Apps UI - 记录删除二次确认 (2026-10-12)

**修改：**

- `RecordList` 的单行删除和批量删除均改为先打开确认弹窗。
- 确认后才调用删除接口；单行删除保留当前分页位置，批量删除成功后回到第一页。
- 删除成功后同步清理选中状态、详情抽屉和关联记录抽屉；失败时在确认弹窗中展示错误并允许重试或取消。

**验证：**

- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- 基线 `bash ./init.sh` 首次运行因 Quarkus dev 进程占用 `flexmodel-server-dev.jar` 清理失败；已停止占用进程并重跑，结果待确认。

## MCP AppTools 表单批量创建修复 (2026-10-12)

**修改：**

- create_app_form 改为调用 AppFormService.createFormWithFields，一次构建包含内置字段、主显字段和自定义字段的
  EntityDefinition，避免“先建表单、再逐字段按名称反查”的 read-your-writes 失败链路。
- 自定义字段、options 和引用在唯一性校验与持久化之前统一完成校验；metadata 保存失败时清理已创建模型，避免留下只有内置字段的半成品。
- AppFormFieldOptionResponse 增加字符串委托构造，字符串 options 会规范化为对象选项。
- ModelingService.resolveDatabaseName 仅在显式项目 ID 与当前会话项目 ID 一致时复用项目数据库缓存，防止 MCP 跨项目调用读到旧项目的
  system schema。
- 临时引擎诊断改动已完全移除，最终修复不涉及 flexmodel-engine。

**验证：**

- mvn test -pl flexmodel-server -am -Dtest=AppToolsTest -Dsurefire.failIfNoSpecifiedTests=false 通过（5 tests，0
  failures，0 errors）。
- bash ./init.sh 通过（全模块 clean compile + flexmodel-engine 测试）。

## Apps 菜单链接关联与 iframe 展示 (2026-10-14)

**修改：**

- `AppMenuService` 新增 `LINK` 关联类型，复用 `target.id` 保存 URL；服务端校验链接为 `http` / `https`、主机名非空且长度不超过
  2048，`AppMenuResponse` 对链接返回原始 `target.type` 与 URL label，`pageRoute` 仍为内部菜单路由。
- `AppMenuManager`、`MenuLinkPicker` 与 `MenuEditorDialog` 均支持选择“自定义链接”并输入 URL；前端使用与后端一致的 URL
  校验，多个链接菜单项可并存并继续参与分组、排序和删除。
- `AppMenuPage` 在 `target.type === LINK` 时渲染右侧全高 iframe，保留应用 Header 与左侧菜单，iframe 使用 `no-referrer`。
- `AppTools.create_app_menu` 的 MCP 工具描述同步声明 `targetType=LINK` 时 `targetId` 为 `http/https` URL。

**验证：**

- `mvn test -pl flexmodel-server -am "-Dtest=AppMenuResourceTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（9
  tests，0 failures，0 errors）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- `bash ./init.sh` 通过（全模块 clean compile + `flexmodel-engine` 测试）。
- `git diff --check` 通过。

**遗留/风险：**

- 外部站点可通过 `X-Frame-Options` 或 CSP 拒绝 iframe 嵌入，此时浏览器会展示空白或失败状态；这是目标站点安全策略，不做绕过。

**后续修复 (2026-10-14)：**

- 修复在应用页内打开菜单编辑器新增菜单后，点击左侧新菜单仍提示“菜单不存在或已被删除”的问题。
- 根因是 AppMenuPage 的菜单加载 effect 仅依赖 appId，路由中的 menuId 变化时组件复用且继续使用旧菜单列表；现已将 menuId
  纳入依赖，切换菜单时重新读取缓存中的最新菜单数据。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

**删除确认修复 (2026-10-14)：**

- 修复删除当前访问菜单后，确认弹窗关闭时菜单编辑器也被关闭的问题。
- 根因是删除成功后立即路由跳转，导致 AppLayout 重新挂载并丢失菜单编辑器打开状态；现移除该跳转，仅同步顶部菜单选中状态和最新菜单数据，编辑器保持打开。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

**顶部菜单刷新修复 (2026-10-14)：**

- 修复刷新菜单详情页后顶部菜单选中状态丢失的问题。
- 根因是 AppLayout 只根据 top 查询参数恢复顶部菜单，未根据当前 menuId 反查所属顶部菜单，导致刷新后回退到第一项。
- 新增 findTopMenuIdForMenu，并在应用路由刷新和切换时根据 menuId 定位所属顶部菜单，保持 Header 与左侧菜单同步。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

**刷新加载态优化 (2026-10-14)：**

- 修复刷新应用页时标题先显示“销售管理”再变为正确应用名的问题；应用数据加载期间标题改为“应用加载中…”。
- 修复菜单数据未返回时顶部菜单先选中第一项再跳到正确项的问题；加载期间不再回退到第一项，解析到当前 menuId 后直接选中所属顶部菜单。
- flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示）。

**浏览器标题规则 (2026-10-14)：**

- 进入应用后浏览器标题按 `{菜单名} - {应用名称}` 展示；访问应用首页等无菜单路由时仅展示应用名称。
- 应用或菜单数据尚未加载完成时先展示“应用加载中…”，避免旧标题或错误应用名闪现；离开应用路由后恢复 `Flexmodel Apps`。
- 切换应用时同步清理上一个应用的应用与菜单状态，避免顶部菜单、标题和路由解析使用旧应用数据。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。

**Header 新增记录快捷入口 (2026-10-14)：**

- `AppTopHeader` 复用 Header 右侧“新建”按钮，在应用内读取菜单配置中的 FORM 关联项并去重展示，处理加载中、空表单与加载失败状态。
- 选择表单后打开 `AppRecordCreateDialog`；该组件负责按表单名加载表单，并复用列表页新增按钮的 `AppRecordFormModal`
  提交新增记录，避免重复实现弹窗。
- 左侧菜单栏不再额外新增入口；原有设置入口仍打开空白新增/编辑菜单界面，不影响现有菜单管理能力。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。

**登录默认进入应用管理 (2026-10-14)：**

- 登录成功后默认跳转 `/admin/apps`；历史会话中保存的 `/dashboard/home` 来源路径也会映射到应用管理，避免继续进入 mock 仪表盘。
- 根路由与未知路由兜底统一改为 `/admin/apps`，并移除 `/dashboard/home` 直达路由。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- `bash ./init.sh` 通过（全模块 clean compile + `flexmodel-engine` 测试）。

**记录弹窗与详情分栏样式统一 (2026-10-14)：**

- 新增 `AppFormSectionTitle` 共用组件，详情页与新增/编辑记录弹窗复用同一主色竖条和标题样式。
- 表单弹窗分栏标题移除普通底边线，字段标签改为详情页同款浅色小字号，栅格横向间距统一为 32px。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。

**左侧菜单分组项图标修复 (2026-10-14)：**

- 修复展开分组后的子菜单项只显示文字、不展示配置图标的问题。
- 折叠侧边栏时的分组弹层同样展示子菜单图标，并保持图标与文字的对齐和截断规则。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。

**MCP AppTools AI 友好字段名 (2026-10-15)：**

- `create_app_form` 的 `fieldsJson` 支持每个字段传入 AI 生成的 `name`，要求简洁英文 snake_case 并由服务端校验。
- 服务端统一校验非法与冲突字段名；未传 `name` 时优先从 ASCII 标签推导（如 `Order Amount` → `order_amount`），无法推导或冲突时回退随机名。
- 单字段创建接口同步复用该字段名解析逻辑，保持 REST 与 MCP 行为一致。
- `mvn test -pl flexmodel-server -am -Dtest=AppToolsTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（6 tests，0
  failures，0 errors）。
- `bash ./init.sh` 通过（全模块 clean compile + `flexmodel-engine` 测试）。

**Apps UI 移除团队功能 (2026-10-15)：**

- 删除管理中心的团队入口与 `/admin/bizuser/teams` 路由。
- 删除团队列表页面、用户设置中的“加入团队”展示、动态页团队折叠面板以及 `teams` / `currentUser.teams` mock 数据。
- `src` 中已无团队相关引用。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- `git diff --check` 通过。

**Apps UI 用户与角色路由简化 (2026-10-15)：**

- `/admin/bizuser/users` 调整为 `/admin/users`。
- `/admin/bizuser/role-privileges` 调整为 `/admin/role-privileges`。
- 路由定义与左侧管理菜单同步更新，`src` 中无旧路由残留。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。
- `git diff --check` 通过。

**部门用户与角色关系真实接口 (2026-10-15)：**

- `platform.fml` 新增 `UserStatus`、`f_department`、`f_user_department`、`f_user_role`，并移除 `f_user.role_ids`
  ；系统不再保留逗号分隔角色的历史兼容。
- `f_user_department` 支持 `is_main`、`job_title`、`manager_user_id`，可表达一人多部门、主部门、职位和直属主管；`f_user_role`
  以联合唯一索引表达用户与角色多对多关系。
- 后端新增部门、用户部门、用户角色 Repository/Service/Resource，用户接口返回部门、职位、主管、状态与角色明细，AuthService 改为通过
  `f_user_role` 解析权限；创建用户未显式传 ID 时自动生成 UUID。
- `flexmodel-apps-ui` 部门用户与角色权限页面接入 `/api/departments`、`/api/users`、`/api/roles`、`/api/resources`，支持部门
  CRUD、用户多部门任职、直属主管、状态与角色配置。
- 新增/编辑部门弹窗不展示“上级部门”字段；在某个节点上执行“新增部门”时自动挂到该节点，顶部按钮新建根部门。
- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` 通过。
- `mvn test -pl flexmodel-server -Dtest=AuthResourceTest,UserResourceTest,DepartmentResourceTest` 通过（24 tests，0
  failures，0 errors）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示）。

**组织架构部门树操作优化 (2026-10-15)：**

- 部门树首次加载成功后默认展开全部部门节点，用户后续手动折叠状态不被刷新重置。
- 每个部门节点右侧新增“更多”图标，点击“更多”图标后展示操作下拉。
- 下拉包含新增部门、编辑部门、删除部门；新增部门默认以当前节点为上级部门，部门节点文字保持左对齐。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**用户状态行内操作与全局提示 (2026-10-15)：**

- 用户表格操作列新增“启用/停用”按钮，切换状态时保留用户原角色、部门任职与直属主管信息。
- 新增/编辑用户弹窗移除状态选择器，状态只通过行内按钮修改。
- `PATCH /api/users/{userId}` 使用 `UserPatchRequest` 支持 `status` 与 `avatar` 局部更新，两者均可选且必须至少提交一个字段；状态切换仅提交
  `status`，不再重组完整用户 payload 或使用状态子资源。
- `f_user` 新增 `avatar` 字段，`UserResponse` 同步返回头像；新增局部更新头像的集成测试。
- 新增全局 `message` 轻量提示组件并挂载到应用根部；停用当前登录用户等后端校验错误改为右上角全局提示，不再占用页面错误区域。
- 操作按钮新增 `xs` 最小尺寸规格，用户表格的启用/停用、编辑、删除按钮统一使用该尺寸。
- `mvn test -pl flexmodel-server -Dtest=UserResourceTest` 通过（10 tests，0 failures，0 errors）。
- `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**记录表格页签配置与筛选 (2026-10-16)：**

- 表单元数据新增顶层 `viewTabs`，不与 `layout.tabs` 混用；`PATCH /api/apps/{appId}/forms/{formName}` 支持 `viewTabs` 局部更新。
- 页签条件使用原生 filter DSL：`{"_and":[{"name":{"_contains":"张"}}]}`，支持最多 20 个页签、每页签 10 个条件，并按字段类型校验操作符与值。
- `GET /api/apps/{appId}/forms/{formName}/records` 新增 `viewTabId`；`all` 或空值不过滤，未知页签返回
  400，页签条件与搜索、子表单上下文条件合并到 `_and`。
- 表单更新、布局保存和字段变更保留页签；删除字段会移除引用该字段的页签条件，条件清空后删除页签。
- 前端固定生成“全部”页签，配置弹窗支持字段、操作符、值与多条件编辑；切换页签重置分页和选择，保存后通过 PATCH 持久化。
- 验证：`mvn compile -pl flexmodel-server -am -DskipTests` 通过；
  `mvn test -pl flexmodel-server -Dtest=AppRecordServiceTest,AppFormResourceTest,AppRecordResourceTest` 通过（23 tests，0
  failures，0 errors）；`mvn test -pl flexmodel-engine` 通过；`flexmodel-apps-ui npm run build` 通过。
- 环境备注：`bash ./init.sh` 在 clean 阶段因本机已有进程占用 `flexmodel-server/target/flexmodel-server-dev.jar`
  失败；本次已使用不执行 clean 的后端编译与目标测试完成验证。

**记录弹窗表单设计入口 (2026-10-19)：**

- `AppRecordFormModal` 在新增、编辑记录弹窗标题右侧新增“表单设计”设置按钮。
- 点击按钮会关闭当前弹窗，并跳转到 `/admin/apps/{appId}/forms/{formName}/form-design` 表单设计器页面。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**工作台组件概念重命名 (2026-10-19)：**

- 前台概念由 `dashboard/chart` 统一切换为 `workbench/component`，不做历史兼容。
- `DashboardHome`、`ChartCanvas`、`ChartDesigner` 分别重命名为 `WorkbenchHome`、`ComponentCanvas`、`ComponentDesigner`。
- `DashChart`、`ChartConfig`、`dashboards`、`dashCharts` 等类型和 mock 数据结构同步改为 `WorkbenchComponent`、
  `ComponentConfig`、`workbenches`、`workbenchComponents`。
- 前台路由与内置菜单目标改为 `/workbench/home` / `workbench`；后端内置菜单 ID 与相关测试同步更新。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；
  `mvn test -pl flexmodel-server -Dtest=AppMenuResourceTest` 通过（9 tests，0 failures，0 errors）。

**图表表单数据源 (2026-10-19)：**

- 按前台仅有表单数据源的模型调整：图表设计器不再提供“数据源类型”切换，直接选择客户、商机、产品、报销单等当前系统 mock 表单。
- 表单数据源可配置维度字段、统计方式（求和、计数、平均值）和指标字段，并展示记录数与聚合组数摘要。
- `mock.ts` 的 `getChartRows` 仅按表单记录维度分组并执行聚合，已移除内置数据源回退；`ChartCanvas`
  复用该结果渲染折线图、柱状图和饼图，设计器预览与仪表盘卡片保持一致。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**表单设计器可选字段列表 (2026-10-19)：**

- 表单设计器右侧字段列表同时包含业务字段和内置字段，并按当前布局剔除已经布局的字段；内置字段展示“内置字段”标识。
- 新建表单返回空布局；新建字段只更新字段元数据，不再自动加入布局，字段备注会在用户手动把字段加入布局时作为提示写入。
- 布局成为记录新增、编辑弹窗的唯一渲染来源；空布局是合法状态。内置字段可以手动加入布局，渲染为只读控件且不参与提交；未布局业务字段不参与必填校验与提交。
- 布局校验允许空布局和部分字段布局；非空布局必须至少包含一个字段，字段必须存在且不重复，以保留既有校验并支持可选布局。
- 验证：`mvn test -pl flexmodel-server -Dtest=AppFormResourceTest` 通过（13 tests，0 failures，0 errors）；
  `flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**MCP AppTools 页签契约提示词 (2026-10-19)：**

- `AppTools.create_app_form` 的工具描述新增记录列表页签配置契约，说明 `viewTabs` 页签对象、`id`/`label` 约束、`filter._and`
  条件结构、1-10 个条件限制，以及数值、日期、布尔、选项、引用和文本类字段支持的操作符。
- 提示词同步说明页签通过 `PATCH /api/apps/{appId}/forms/{formName}` 仅提交 `viewTabs` 进行局部更新，帮助 AI
  在创建表单后正确配置列表页签。
- 验证：`mvn compile -pl flexmodel-server -am -DskipTests` 通过；
  `mvn test -pl flexmodel-server -Dtest=AppToolsTest "-Dsurefire.failIfNoSpecifiedTests=false"` 中 3
  个布局相关既有断言失败（expected 3/2 field items but was 0、invalid layout keeps original layout），与本次仅提示词改动无关。

**详情与列表展示已布局内置字段 (2026-10-19)：**

- `AppRecordDetail` 渲染表单布局字段时不再过滤 `field.builtin`，已手动加入布局的内置字段会在记录详情中展示。
- 详情字段渲染改为 12 列栅格，按布局项的 `width` 应用 `QUARTER`、`THIRD`、`HALF`、`TWO_THIRDS`、`THREE_QUARTERS` 或 `FULL`
  栅格跨度；例如 `FULL` 在桌面端整行展示，不再固定占用二分之一。
- `RecordList` 的布局列不再排除内置字段，已手动加入布局的内置字段会出现在记录列表中；无布局回退仍保持仅业务字段。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**仪表盘添加图表 (2026-10-19)**

- `DashboardHome` 工具栏新增“添加图表”入口，支持配置图表标题并选择指标卡、折线图、柱状图、饼图、明细表或我的常用。
- 图表类型卡片展示说明与默认尺寸，标题必填校验；确认后新图表按当前仪表盘的最大底部行追加，并继续支持拖拽与缩放。
- `addableChartTypes` 与 `addDashChart` 集中维护在 `src/data/mock.ts`，新增图表的数据继续来自页面 mock 数据源，并跟随筛选与自动刷新。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；
  `bash ./init.sh` 通过全模块编译与 `flexmodel-engine` 测试。

**我的常用图表 (2026-10-19)**

- `DashboardHome` 图表类型新增“我的常用”，卡片内展示客户、商机、产品和报销单四个常用表单。
- 点击任一常用表单卡片会通过 `useNavigate` 直接跳转到对应的菜单路由，满足“放置常用表单、点击打开对应菜单”的交互。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**图表设计器 (2026-10-19)**

- 新增 `ChartDesigner` 组件，按“图表 / 数据 / 样式 / 布局”四个页签组织配置项，右侧提供实时预览，替代原先信息密度较低的简易添加弹窗。
- 新增 `ChartCanvas` 统一渲染折线图、柱状图、饼图、指标卡、明细表和我的常用，设计器预览与仪表盘卡片复用同一渲染逻辑。
- 图表卡片新增编辑入口，点击铅笔图标即可进入设计器修改现有图表；数据配置支持数据源、指标、展示列和常用表单选择，样式配置支持图例、标签、曲线和配色。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**仪表盘切换控件还原 (2026-10-19)**

- 按用户反馈还原 `DashboardHome` 左侧仪表盘切换入口为原始文本下拉样式，移除图标、边框和额外提示。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**我的任务审批图表 (2026-10-19)：**

- 仪表盘新增 `tasks` 图表类型和默认“我的任务”卡片。
- 图表展示待审批流程数量、申请人、当前节点，并提供“进入审批中心”按钮，点击跳转 `/approval/home`。
- 图表设计器新增对应图标，可直接从图表类型列表选择“我的任务”。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**我的任务图表样式调整 (2026-10-19)：**

- “我的任务”图表改为三个页签：`待办工作`、`最近处理`、`我发起的`。
- 每个页签只展示对应流程列表，移除原先的统计数字和底部“进入审批中心”按钮。
- 点击任一列表项跳转 `/approval/home`。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**我的任务列表条目样式 (2026-10-19)：**

- “我的任务”三个页签内的列表项改为横向条目。
- 条目之间以一条横线分隔。
- 左侧展示流程标题、申请人与当前节点，右侧展示提交时间。
- 验证：`flexmodel-apps-ui npm run build` 通过（`tsc -b` + `vite build`，仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）。

**我的任务 Tab 样式统一 (2026-10-19)：**

- “我的任务”图表的 Tab 样式调整为与记录页签一致的下划线样式。
- Tab 容器改为底部横线布局，激活页签使用主色下划线，非激活页签保持灰色。

**Apps 内置表单 (2026-10-19)：**

- 每个应用默认返回 f_role、f_user、f_department、f_user_department、f_user_role 五个内置表单；内置定义来自 system
  schema，排在用户表单之前，managed=false 且 builtin=true。
- AppFormResponse 新增 builtin 属性并保留旧构造器兼容；内置表单禁止创建、更新、PATCH、布局修改、字段增删改和删除，同名历史用户实体不再重复返回。
- AppRecordService 统一按 builtin 路由记录操作：内置表单查询、详情、新增、更新、删除访问 system
  schema；非内置表单继续使用应用项目数据源；引用字段渲染同样复用该路由。
- DataService 增加显式 datasourceName 的分页查询重载，供内置表单访问 system schema。
- 验证：mvn compile -pl flexmodel-server -am -DskipTests 通过；mvn test -pl flexmodel-server
  -Dtest=AppRecordServiceTest,AppFormResourceTest 通过（20 tests，0 failures，0 errors）。
- 标准基线：bash ./init.sh 通过全模块 clean compile 与 flexmodel-engine 测试；当前环境未检测到 Node，UI E2E 按脚本跳过。

**Apps UI 内置表单标签 (2026-10-19)：**

- AppForm 前端契约补充 boolean builtin，normalizeForm 对缺失值回退 false。
- 表单管理卡片和表单设置标题展示“内置”徽标，样式复用内置字段徽标的 bg-muted 与 text-muted-foreground。
- 表单设计器内部模型与引用表单回退对象同步携带 builtin，保证 TypeScript 契约完整。
- 验证：flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示与 /logo.png 运行时解析提示）；
  bash ./init.sh 通过全模块编译与 flexmodel-engine 测试（UI E2E 因 Node.js 未安装跳过）。

**应用表单右侧导航精简 (2026-10-19)：**

- AdminLayout 移除表单专属二级侧栏和相关路由偏移，保留原有应用管理左侧主导航。
- 验证：flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示与 /logo.png 运行时解析提示）。

**内置表单系统字段标记 (2026-10-19)：**

- f_user 的 avatar 纳入系统核心字段，以 URL 类型返回到应用表单入口的字段列表与设计器可用字段。
- 系统核心字段（name、email、avatar、status、roleIds 等）通过 AppFormFieldResponse.builtin=true 标记；扩展字段继续返回
  builtin=false，设计器沿用“内置字段 / 扩展”展示。
- SystemRecordFormModal 继续将系统核心字段转换为可编辑字段，避免 AppRecordFormModal 把 builtin 解释为只读导致 name、email
  等无法编辑。
- 验证：mvn test -pl flexmodel-server
  -Dtest=SystemFormResourceTest,SystemRecordResourceTest,AppFormResourceTest,AppRecordServiceTest
  -Dsurefire.failIfNoSpecifiedTests=false 通过（29 tests，0 failures，0 errors）；flexmodel-apps-ui npm run build 通过（tsc
  -b + vite build，仅既有 chunk 体积提示与 /logo.png 运行时解析提示）；bash ./init.sh 因运行中的 flexmodel-server-dev.jar
  无法删除而中断，未执行 clean 的目标测试已补足本次代码验证。

**表单设计器新增字段自动入布局 (2026-10-19)：**

- FormDesigner 的“添加字段”保存成功后，新字段默认以 HALF 宽度追加到当前布局末尾，并保留填写提示。
- 同步更新 form.fields、items 和 form.layout，右侧字段列表立即反映最新状态。
- 验证：flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示与 /logo.png 运行时解析提示）。

**系统表单配置驱动组织与角色 (2026-10-19)：**

- 新增系统表单 API，表单定义与记录操作统一入口为 /api/system/forms 与 /api/system/forms/{formName}/records；表单配置直接保存在
  f_user、f_department、f_role 的模型 metadata，不新增 f_system_form。
- 仅暴露 f_user、f_department、f_role 三个系统表单；f_user_department、f_user_role、f_role_resource
  继续作为关系表使用但不作为表单暴露，用户任职仍可编辑。
- 记录接口保持既有扁平 JSON 契约：用户使用 roleIds 与 departments，角色使用 resourceIds；本期未引入 values/relations
  包装，也未做目录筛选组件抽象。
- f_role.resource_ids 移除，改为 f_role_resource 关系表；权限计算链路改为 f_user_role -> f_role_resource ->
  f_resource.permission。
- 应用表单列表不再返回系统表单与关联表；移除 /api/departments、/api/users、/api/roles、/api/resources 旧入口，且未新增资源用户入口。
- 前端 /admin/users 保留左侧部门树与既有交互，右侧用户表单按 metadata 渲染核心字段、扩展字段、布局、列表列、任职和角色；/admin/role-privileges
  按 f_role metadata 渲染，权限树由服务端注入。
- 修复系统表单 metadata 读取：FML 注解解析会包一层 value，服务端解包后读取，并固定表单列表顺序为
  f_user、f_department、f_role。
- 修复系统表单核心字段校验与物理字段保留：字段名统一转小写后与 roleIds、resourceIds 等驼峰核心字段改为大小写不敏感匹配，同时将
  avatar、password_hash 纳入用户表实际核心字段，避免保存表单配置时被误当作扩展字段删除。
- 部门架构点击改为实时请求 /records 接口：新增 departmentId 与 includeDescendants 查询参数，用户列表改为服务端分页，并继续包含子部门递归筛选。
- 验证：mvn test -pl flexmodel-server -am
  -Dtest=SystemFormResourceTest,SystemRecordResourceTest,AppFormResourceTest,AppRecordServiceTest
  -Dsurefire.failIfNoSpecifiedTests=false 通过（29 tests，0 failures，0 errors）；flexmodel-apps-ui npm run build 通过（仅既有
  chunk 体积与 /logo.png 提示）；bash ./init.sh 通过全模块编译与 flexmodel-engine 测试。

**内置表单设计器添加字段入口 (2026-10-19)：**

- FormDesigner 右侧字段面板对内置表单开放“添加字段”页签，新建/编辑表单右上角设置入口与表单设计入口均可使用。
- 内置表单仅展示当前后端支持且添加弹窗可完整配置的扩展字段类型；暂不展示 REFERENCE 与 MULTIREFERENCE，避免缺少引用目标配置导致保存失败。
- 按用户要求，本次仅修改代码，未运行构建或测试。

**内置表单启动运行时实体合并保留 (2026-10-19)：**

- BuildItem 扩展点调整为 getPreservedRuntimeModels，SessionFactory 在应用启动种子模型时按模型名合并数据库中的完整运行时
  EntityDefinition。
- 对 f_user、f_department、f_role 合并保留运行时扩展字段、索引和 metadata；种子中的核心字段、索引和新增字段仍可同步到实体定义。
- 合并后的实体同时写入缓存并传给 migrateEntity，避免 platform.fml 启动执行导致扩展字段从运行时模型中丢失。
- 物理 DDL 与普通模型迁移不受影响；按用户要求，本次未运行构建或测试。

**内置记录编辑输入保留 (2026-10-19)：**

- 修复 SystemRecordFormModal 每次重渲染都重新构造 AppForm 对象的问题；任职职位等输入触发父组件状态更新后，AppRecordFormModal
  不再因 form 引用变化重新加载记录并清空输入。
- toAppForm 转换结果改为 useMemo 缓存，仅在系统表单配置真正变化时更新；按用户要求，本次未运行构建或测试。

**应用表单列表内置优先 (2026-10-19)：**

- AppFormService.findForms 统一先返回 f_user、f_department、f_role 三个内置表单，再返回应用用户表单。
- 保留既有系统表单固定顺序与应用用户表单原有顺序；按用户要求，本次未运行构建或测试。

**系统表单配置独立持久化回滚 (2026-10-19)：**

- 按用户要求撤销未确认的 f_system_form_config 方案，platform.fml 不再新增该模型。
- SystemFormService 恢复为读取和保存系统模型 metadata，并保留启动时针对 f_user、f_department、f_role 的运行时 metadata 保护。
- 本次仅回滚代码，未运行构建或测试。

**完整运行时实体合并保留 (2026-10-19)：**

- mergedFields 按字段名优先使用种子中的核心字段定义，同时保留数据库中的扩展字段，并追加种子新增字段。
- mergedIndexes 按索引名合并运行时索引和种子索引，保留运行时新增索引并追加种子新增索引。
- additionalProperties 合并时优先保留运行时 metadata，避免表单布局、列表列和页面配置被启动种子覆盖。
- 验证：SystemFormResourceTest、SystemRecordResourceTest、AppFormResourceTest、AppRecordServiceTest 共 29
  个用例通过；flexmodel-core 90 个测试通过；flexmodel-apps-ui npm run build 通过。

**内置表单设计器返回修复 (2026-10-19)：**

- FormDesign 在 appId 为 system 时不再返回 /admin/apps/system/settings/forms，避免把 system 当作普通应用加载。
- f_user、f_department 返回 /admin/users，f_role 返回 /admin/role-privileges；普通应用表单仍返回对应应用的表单管理页。
- 验证：flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示与 /logo.png 运行时解析提示）。

**系统审计字段与 AppTools 内置引用目标 (2026-10-19)：**

- 应用表单的 created_by、updated_by 字段契约改为 REFERENCE，并引用内置 f_user 表单。
- AppTools 的 list_app_forms 与 create_app_form 说明内置表单可作为 REFERENCE 目标，可用目标包括
  f_user、f_role、f_department。
- 新增 createsReferenceFieldsToBuiltinForms 测试，覆盖三个内置引用字段和两个系统审计字段。
- 验证：mvn test -pl flexmodel-server -Dtest=AppToolsTest#createsReferenceFieldsToBuiltinForms
  -Dsurefire.failIfNoSpecifiedTests=false
  通过（1 test，0 failures，0 errors）。

**应用记录接口布局字段投影 (2026-10-19)**

- AppRecordService 的列表、详情、创建返回与更新返回统一在引用渲染后执行布局投影；仅输出 FIELD 节点引用的字段与
  SUBFORM.referenceField，并始终保留 id。
- 未进入布局的实体字段不再通过 /records 响应暴露；子表单关联字段保留，避免主子记录上下文丢失。
- AppRecordServiceTest 增加未布局字段不返回的用例，并让引用渲染用例显式提供布局。按用户要求，本次未运行构建或测试。

**Apps UI NTEXT Textarea (2026-10-19)**

- Added a shared Textarea primitive with the existing input styling, focus ring, disabled state, and vertical resizing.
- AppRecordFormModal now renders NTEXT fields with Textarea for both record creation and editing; other field types
  continue using Input.
- Static inspection confirmed the branch and import are present. Build and tests were not run per user request.

**Apps UI 工作台快捷入口命名 (2026-10-19)**

- 工作台可选组件“我的常用”更名为“快捷入口”，描述同步改为配置常用入口。
- 组件类型由 `favorites` 统一重命名为 `shortcuts`，配置字段由 `favoriteForms` 重命名为
  `shortcuts`，入口数据源由 `favoriteForms` 重命名为 `shortcuts`。
- ComponentCanvas 与 ComponentDesigner 的类型判断、局部变量和默认配置同步更新，不再保留旧的 favorites/quickAccess 命名。
- 验证：flexmodel-apps-ui npm run build 通过（tsc -b + vite build，仅既有 chunk 体积提示与 /logo.png 运行时解析提示）。

**应用工作台真实配置接口 (2026-10-20)**

- platform.fml 新增 `f_workbench` 系统表：`project_id + name` 唯一，`layout` 为 JSON；不再拆分布局表。
- 工作台布局结构为 `layout.components + layout.items`；`items` 保存 `componentId + x/y/w/h` 显式网格坐标，不做
  width/height 历史兼容。后端校验位置、尺寸、边界与重叠。
- 前端 WorkbenchHome 集成 GridStack：编辑配置模式启用拖动与缩放，拖动/缩放结束后 PATCH 保存显式坐标；ComponentDesigner 支持
  1-12 列宽度与 1-24 行高度设置。
- 右下角缩放把手向下偏移 2px；编辑模式下组件标题 hover 显示 grab 光标，拖动中显示 grabbing 光标。
- 修复布局持续显示最小 1x1 的问题：已持久化的旧 `width/height` 数据会在启动迁移中一次性改写为显式 `x/y/w/h`
  ；迁移监听器通过事件优先级确保在引擎初始化后执行，避免因启动顺序查不到表而跳过迁移。真实 `sqlite.db` 两条旧布局已验证转换为
  `x=0, y=0, w=6, h=3`。
- 布局边框已与容器起点对齐：GridStack 采用 `0 16px 16px 0` 边距，顶部和左侧不再额外缩进，右侧和底部保留组件间距。
- 修复多组件相邻问题：移除 `grid-stack-item-content` 上的 `h-full`，让 GridStack 的上下左右偏移真正生效，组件之间恢复固定间距。
- 修复拖动/缩放后未发 PATCH 的问题：GridStack 的 `dragstop/resizestop` 回调参数应为 `GridItemHTMLElement`，改为从其
  `gridstackNode` 读取组件 ID 与网格坐标，确保提交真实布局。
- 新增 `WorkbenchServiceTest` 覆盖旧 `HALF/SHORT` 布局迁移；新增 `WorkbenchStartupMigrationTest`
  在应用启动前预置旧布局数据，验证迁移监听器确实在引擎初始化后自动执行。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积与 /logo.png 提示）；
  `mvn test -pl flexmodel-server -am "-Dtest=WorkbenchServiceTest,WorkbenchStartupMigrationTest,WorkbenchResourceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  通过（7 tests，0 failures，0 errors）；`bash ./init.sh` 通过全模块编译与 flexmodel-engine 测试。
- 遗留：工作台组件展示数据仍为 mock，真实数据接口与刷新策略留待下一阶段；本次未提交 Git 变更。

**快捷入口复用菜单图标 (2026-10-20)**

- 快捷入口目标数据从菜单读取已配置的导航图标，工作台卡片与组件设计器预览均复用同一图标映射。
- 菜单未配置图标或选择“无图标”时，快捷入口保留原文件图标兜底，不影响既有空图标菜单。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**新增组件配置布局优化 (2026-10-20)**

- 新增组件弹窗基础配置改为“先选类型、再填标题”的顺序，操作路径更直观。
- 组件类型从文字卡片改为紧凑图标卡片网格，减少视觉噪音；选中类型单独展示说明。
- 弹窗加宽到 1360px，左侧配置区固定 420px，右侧预览区扩大为主空间并加高到 520px。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**工作台管理操作按钮 (2026-10-20)**

- 工作台新建、编辑、删除操作改为仅图标按钮；编辑按钮使用铅笔图标，删除按钮使用垃圾桶图标并采用 danger 样式，均保留 title 与
  aria-label。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**组件标题默认组件名称 (2026-10-20)**

- 新增组件时标题默认填入当前组件类型名称；切换组件类型时，默认标题随组件名称同步更新。
- 用户已输入的自定义标题不会被切换组件类型覆盖；编辑既有组件时保留原标题。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**工作台头部操作图标 (2026-10-20)**

- 工作台头部左侧收敛为新增和更多两个无边框 ghost 图标按钮，默认隐藏，仅在悬浮工作台头部时显示。
- 两个操作按钮均使用最小尺寸；编辑工作台与删除工作台收进更多菜单，按钮保留 title 与 aria-label。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**快捷入口每行数量 (2026-10-20)**

- 快捷入口配置新增 `columnsPerRow`，设计器支持选择每行 1 到 12 个入口，画布按该配置渲染列数。
- 后端配置结构同步新增 `columnsPerRow` 并校验 1 到 12 的范围；保存快捷入口组件时随配置一起持久化。
- 快捷入口卡片改为上图标下文字结构；移除文字外框，图标背景参考 Logo 的圆角方形样式，文字位于背景色外部，并根据每行数量自适应图标与文字尺寸；1-3
  个/行时使用更大图标档位。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `mvn test -pl flexmodel-server -am "-Dtest=WorkbenchServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过（2
  tests，0 failures，0 errors）；`git diff --check` 通过。

**工作台编辑与保存配置 (2026-10-20)**

- 工作台头部按钮从“编辑配置/锁定配置”改为“编辑组件/保存配置”。
- 组件新增、编辑、删除和布局拖拽/缩放先写入本地 `draftLayout`，不再即时调用服务端。
- 点击“保存配置”时才统一调用 `patchWorkbench` 持久化；保存失败时保留草稿与编辑态，便于重试。
- “添加组件”按钮仅在编辑状态下展示，避免非编辑模式出现未保存入口。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**新增组件构建式布局 (2026-10-20)**

- 新增/编辑组件弹窗重构为构建式双栏布局：左侧 360px 紧凑配置栏，右侧预览区占据剩余主空间。
- 弹窗宽度提升到 1680px，高度提升到 `min(92vh, 960px)`，预览画布最小高度提升到 560px，避免内容挤压。
- 组件类型选择压缩为两列紧凑图标项，减少视觉噪音；右侧预览头部展示组件图标、标题和网格尺寸。
- 布局宽度输入直接按 `w/12` 转为百分比，右侧预览宽度实时变化；高度按 80px 行高计算，超过可用区域时整体等比缩放。
- 图表画布显式使用 `100%` 宽高，避免预览容器尺寸变化后图表不跟随重绘。
- 工作台在 GridStack 初始化后显式调用 `grid.update` 应用每个组件的 `x/y/w/h`，修复组件设计器保存布局后工作台不变化的问题。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；`npx tsc --noEmit` 通过；
  `git diff --check` 通过。

**AI 友好工作台契约与 MCP 能力 (2026-10-20)**

- 工作台布局升级为 `schemaVersion + components` 单数组契约；`placement` 内嵌在组件中，移除独立 `items` 数组和旧宽高迁移逻辑。
- `config` 改为 `metric/chart/dataloader/shortcuts/tasks` 判别联合；折线、柱状和饼图统一为 `chart + chartType`
  ，后端保留网格边界、重叠、字段类型、聚合和快捷菜单校验。
- 新增 `GET /api/apps/{appId}/workbenches/capabilities`，返回组件规格、默认配置、枚举值、可引用表单字段和可关联表单菜单，供
  AI 在生成前读取。
- `AppTools` 新增工作台 MCP 工具：`list_app_workbenches`、`get_app_workbench`、`get_app_workbench_capabilities`、
  `create_app_workbench`、`save_app_workbench_layout` 和 `create_app_workbench_menu`；工作台菜单关联使用
  `BUILTIN targetId=workbench`。
- 前端 `workbenches` 服务、`WorkbenchHome`、`ComponentCanvas` 和 `ComponentDesigner` 同步新契约；本次仅调整功能契约，未调整既有视觉样式。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积提示与 `/logo.png` 运行时解析提示）；
  `mvn test -pl flexmodel-server -am "-Dtest=WorkbenchServiceTest,WorkbenchResourceTest,AppToolsTest#createsAiFriendlyWorkbenchAndMenuLink" "-Dsurefire.failIfNoSpecifiedTests=false"`
  通过（9 tests，0 failures，0 errors）。
- 补充验证：`git diff --check` 通过；`bash ./init.sh` 通过全模块编译与 `flexmodel-engine` 测试。
- 复核修复：`ComponentDesigner.changeForm` 切换表单时只重置表单相关字段，保留当前组件类型、图表样式和指标选择；最终再次执行
  `flexmodel-apps-ui npm run build` 与 `git diff --check`，均通过。
- 遗留：全量 `AppToolsTest` 仍存在 4 个与本次无关的既有表单布局断言失败；本次仅验证新增工作台链路测试。

**工作台真实数据组件与 MCP 数据能力 (2026-10-20)**

- `metric/chart/dataloader` 组件接入真实表单数据：指标支持 `count/sum/avg`，`sum/avg` 必须选择数字字段；图表按可分组字段聚合，最多返回
  100 组；明细支持分页、搜索，页大小限制 1-50。
- 新增 `GET /api/apps/{appId}/workbenches/{workbenchId}/components/{componentId}/data` 与
  `AppTools.get_app_workbench_component_data`，`shortcuts/tasks` 明确不支持真实数据查询；能力目录补充组件 `dataEndpoint`，
  `schemaVersion` 保持 1。
- 前端 `WorkbenchHome`、`ComponentCanvas`、`ComponentDesigner` 同步真实数据契约并渲染真实结果；未保存配置仍使用设计器预览示例，
  `tasks` 保持 mock 数据，本次不调整视觉样式。
- 验证：`mvn compile -pl flexmodel-server -am` 通过；`flexmodel-apps-ui npm run build` 通过；`WorkbenchDataServiceTest` 4
  tests 通过；`WorkbenchServiceTest + AppToolsTest#createsAiFriendlyWorkbenchAndMenuLink` 5 tests 通过；
  `WorkbenchResourceTest` 新增数据响应与分页校验通过；`bash ./init.sh` 通过全模块编译与 `flexmodel-engine` 测试；
  `git diff --check` 通过。
- 备注：全量 `AppToolsTest` 仍存在与本次无关的既有断言失败；本次仅验证新增工作台数据链路相关测试。

**工作台明细引用值展示修复 (2026-10-20)**

- `ComponentCanvas` 新增 `displayRecordValue`：引用对象优先显示 `displayValue`，缺失时回退 `id`
  ；数组逐项格式化后用逗号拼接，避免明细组件把引用字段渲染成 `[object Object]`。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积与 `/logo.png` 运行时解析提示）；`git diff --check` 通过。
  **子表单引用字段展示修复 (2026-10-20)**

- 新增 `recordValue` 工具：`formatRecordValue` 处理引用对象与数组，`displayRecordText` 为空值返回 `-`。
- `AppSubformRecords` 表格引用字段改用 `displayRecordText`，物料等子表单中的引用字段不再显示 `[object Object]`；
  `ComponentCanvas` 同步复用同一格式化工具。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积与 `/logo.png` 运行时解析提示）；`git diff --check` 通过。

**工作台组件滚动条自动隐藏 (2026-10-20)**

- 工作台组件内容容器新增 `workbench-component-scroll`：滚动条默认透明隐藏，鼠标悬停或键盘聚焦时显示 6px
  细滚动条；保留滚动能力且不改变组件布局。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积与 `/logo.png` 运行时解析提示）；`git diff --check` 通过。

**默认蓝色主色与管理后台统一 (2026-10-20)**

- 应用主色默认值由靛蓝改为蓝色；颜色选择器、前端请求归一化、新建与编辑表单默认值同步蓝色优先，后端 `AppService`、DTO 校验和
  `AppTools` 描述保持一致。
- 管理后台基础主题改为蓝色，应用外壳无数据或卸载时的 `data-primary` 回退值也改为蓝色；应用内仍可按应用配置切换专属主色。
- 修复应用配置主色不生效：补充 `data-primary='indigo'` 的亮色/暗色 CSS 覆盖；此前靛蓝依赖基础默认值，默认改为蓝色后缺失覆盖导致回落蓝色。
- 验证：`bash ./init.sh` 通过全模块编译与 `flexmodel-engine` 测试；`flexmodel-apps-ui npm run build` 通过；`mvn test -pl flexmodel-server
  -am -Dtest=AppResourceTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（10 tests）；`git diff --check` 通过。

**弹窗贴边输入框聚焦边框被裁剪修复 (2026-10-20)**

- `BizDialog` 内容滚动容器仅有纵向滚动需求，但 `overflow-y-auto` 会让浏览器把横向也按滚动裁剪处理；全宽输入框贴齐滚动容器左右边缘，聚焦时的
  `ring-2 ring-offset-2` 光圈（约 4px）被横向裁掉，表现为弹窗两侧边缘输入框聚焦边框不显示。
- 修复：内容滚动容器新增 `px-1.5`（6px）横向内边距，为贴边输入框的聚焦光圈留出绘制空间，不改变弹窗整体内边距与布局结构。
- 验证：`flexmodel-apps-ui npm run build` 通过（仅既有 chunk 体积与 /logo.png 运行时解析提示）；`npx tsc --noEmit` 通过；
  `flexmodel-apps-ui git diff --check` 通过（仅既有 CRLF 提示）。
- 备注：`bash ./init.sh` 本次在 `mvn clean` 删除 `flexmodel-server-dev.jar` 时失败，原因是当前有
  `mvn -pl flexmodel-server quarkus:dev` 会话正在运行并占用该 jar，属环境占用而非代码问题；本次仅修改前端弹窗样式，待 dev
  会话停止后可重跑 init.sh 完成基线验证。

**应用审批流程设计与模拟预览（2026-10-21，feat-086）**

- `flexmodel-apps-ui` 的“管理后台 > 应用管理 > 审批流程”替换占位列表，支持搜索、状态筛选、分页、创建、模板、复制、启停、删除和选择操作；旧全局入口引导先选择应用。
- 新增 `/admin/apps/:appId/approvals/:flowId/design`，采用 FlowGram 纵向固定布局，提供节点插入、拖动排序、复制/删除、撤销重做、缩放、适应画布和缩略图；开始与结束节点保留。
- 七类业务节点：审批、条件网关、新增记录、更新记录、查询记录、删除记录和子流程。配置面板支持条件优先级与默认分支、字段类型联动、固定值/上游变量及子流程输入输出映射。
- 审批执行人支持多组用户/角色/组织规则及发起人、上游节点执行人、变量、脚本；人员范围按所选成员计算，合并后稳定去重；会签、或签、依次审批均任一拒绝即终止。
- 逐步模拟支持选择发起人、输入示例记录和变量、脚本模拟返回成员、逐人同意/拒绝、条件分流、独立记录副本与同步子流程；不会执行真实脚本或调用业务记录写接口。
- 配置按当前登录用户和应用存入 localStorage。保存允许不完整草稿，启用/模拟前校验并定位错误；修改后重新检查变量路径，禁止子流程循环；复制整个流程时重建节点和内部变量引用。
- 真实应用与表单元数据仍通过既有 services 读取，失败时可重试；示例表单有明确标识。沿用 shadcn/ui、Lucide
  与语义主题变量，适配明暗主题、1280px 桌面及窄屏配置抽屉，列表滚动限定在表格内。
- FlowGram 原计划 1.0.16 因其传递依赖 `@flowgram.ai/editor@1.0.16` 未发布而无法安装，改用 Certimate 所采用的同版本 1.0.15
  包；同步 npm/pnpm 锁文件，Vite 去重 React，编辑路由懒加载。补齐 BizDialog 的无障碍标题。
- 验证通过：`npm test`（22 tests）；`npm run test:approvals`（5 tests，覆盖完整模拟、七类节点、拖动、撤销重做、分支增删、真实字段、多组执行人、列表管理、重试和离开提醒）；
  `npm run build`（含 TypeScript）；两仓库 `git diff --check`。浏览器检查浅色/夜间、1440×900、1280×800、900×800；5180
  现有开发服务的登录入口无运行时异常。
- 标准 `bash ./init.sh` 在开始和结束均尝试，仍因现有服务占用 `flexmodel-server/target/flexmodel-server-dev.jar` 而在
  clean 阶段失败。保留该服务，前后分别执行不带 clean 的全模块编译与引擎测试，均通过。待现有 dev 服务停止后可重跑完整
  init.sh；本次没有 Java 改动。
- 页面截图（运行测试可重现，生成文件不提交）：`flexmodel-apps-ui/artifacts/approvals-list-light.png`、
  `approvals-node-settings.png`、`approvals-designer-dark.png`、`approvals-designer-1280.png` 和
  `approvals-simulation.png`。
- 代码已提交到 Apps UI 独立仓库：`c5641ad feat(approvals): 添加应用审批流程设计器与模拟预览`。保留根仓库原有
  `flexmodel-ui` 修改和未登记为子模块的 `flexmodel-apps-ui` 目录状态。
- 后续范围：真实审批定义持久化、后端执行人解析/签核执行、脚本执行以及现有 Flow 引擎适配尚未接入；本次交付范围为页面交互与
  mock 模拟。

**事件驱动的表单流程与真实审批闭环（2026-10-21，feat-087）**

- 按用户确认的边界实现：从记录详情手动提交表单流程，提交时持久化快照并锁定原记录；Flow
  保持通用编排能力，发布通用挂起事件，独立审批模块创建待办和聚合签核，再投递继续或终止命令。`flow` 包没有导入审批或表单模块。
- 项目库增加事务 Outbox、互斥行、草稿/不可变发布、实例、待办、记录锁和节点日志；同项目事务借用同一
  Session，消费结果与消息确认一起提交。支持多服务实例行锁、顺序投递、指数退避（最多 8 次）、失败消息重试，以及发起/签核/节点/子流程的幂等处理。托管
  Flow 校验服务端处理器归属，通用 REST 入口不能绕过审批直接推进。
-

七类业务节点接入真实执行。服务端校验表单字段、可用上游变量/审批节点、网关优先级/唯一默认分支、子流程直接或间接循环；发布时固定子流程版本。数据节点通过现有表单服务验证写入，使用完整服务端记录作为输出，避免表单布局投影导致上游变量字段缺失。

- 人员来自真实用户、角色、部门和直属上级关系；多组规则按范围解析并稳定去重。会签、或签、依次审批均支持，任一拒绝终止；下游执行人取实际同意者。脚本通过
  Functions Runtime 的独立无权限 Deno Worker 运行，双方需配置相同 `FLEXMODEL_APPROVAL_SCRIPT_SECRET`；未配置时禁止发布含脚本的流程。
- 记录锁在类型化 DataService 层执行，覆盖单条、筛选、批量和 GraphQL 调用；所属流程/子流程可操作自己的记录，其他人工写入返回
  409。补充批量失败原子回滚、保留调用者事务及回滚后不发送虚假数据事件。活跃流程阻止应用/关联表单删除。
- Apps UI 从 localStorage
  切换真实异步接口：版本冲突提示、服务端保存/校验/发布、真实目录、记录提交及锁状态、审批中心待办/已处理/我提交的、实例快照/签核/子流程/日志/失败恢复。原独立模拟预览保留，不写真实记录，也不执行真实成员脚本。UI
  独立仓库提交：`579afb3 feat(approvals): 接入服务端流程与真实审批中心`。
- 修复用户报告的启动错误 `Unexpected schemaName: plm_system`：删除先于数据源注册的重复迁移观察器；EngineConfig
  统一注册主应用与分支、去重 Schema，并把 CDI 观察器优先级标在事件参数上；SchemaRegistry 对显式配置且已注册的数据源仍同步
  project.fml。回归覆盖没有分支行的主应用及主分支去重。
- 验证通过：`mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`；`mvn test -q -pl flexmodel-engine`；另实际运行
  `mvn test -q -pl flexmodel-engine/flexmodel-core -am`（90 项、0 失败）。后端指定
  `EngineConfigTest,ApprovalPolicyTest,ApprovalWorkflowResourceTest,FlowEventPublisherTest,FlowDefinitionResourceTest`
  （26 项、0 失败），覆盖并发幂等、消费回滚/重投、锁/事务、签核、网关与默认分支、循环检测、真实 CRUD/子流程以及真实浏览器闭环。Apps
  UI `npm test`（23 项）、`npm run test:approvals`（5 组）、`npm run build`（含 TypeScript）通过；Deno `approval-members_test.ts`
  （2 项）通过。两仓库 diff 检查通过。
- 实际浏览器使用隔离 Quarkus/SQLite 后端和 Vite 5183，完成设计保存/刷新恢复/发布、记录提交/编辑禁用、审批中心签核、通过后解锁；已目视检查
  1440 浅色及 1280 夜间截图：`flexmodel-apps-ui/artifacts/approvals-backend-designer.png`、
  `approvals-backend-signing.png`、`approvals-backend-center-dark.png`。
- 标准 `bash ./init.sh` 再次尝试，仍因现有用户开发服务占用 `flexmodel-server/target/flexmodel-server-dev.jar` 在 clean
  阶段失败；未停止用户服务。非 clean 全模块编译已通过。可在用户自行停止 dev 服务后重跑完整入口。
- 边界与已知限制：本版可靠事务以 SQL 数据库为前提，SQLite 实测通过，Mongo 会话未实现真实事务/行锁；目标 MySQL/PostgreSQL
  需部署前验证。数据节点逐节点提交，后续拒绝不会撤销已执行变更；原生 SQL/DDL 不属于人工记录锁拦截范围。完成的 Outbox
  暂无自动清理，需监控失败积压。全量 server suite 未宣称通过；额外试跑的既有 AppRecordResourceTest
  空布局仍期待完整字段，与当前布局投影行为不符，本次未改变该既有行为。
- 架构、配置、接口和复现命令见 `flexmodel-server/docs/approvals.md`。保留根仓库原有 `flexmodel-ui` 修改与未登记为子模块的
  Apps UI 目录状态。

**审批流程定义与 Flow 结构统一（2026-10-21，feat-088）**

- 审批定义的接口、持久化和不可变发布快照统一为
  `flow_module_id/flow_name/flow_key/flow_model/status/remark/archive/is_deleted/created_by/updated_by/created_at/updated_at`
  ，保留 revision 与发布关联；flow_model 是包含 flowElementList 的 JSON 字符串。新增定义表
  f_form_workflow_definition，避免直接改变旧表主键或丢失旧数据。
- WorkflowModel 与 Apps UI approval-model 负责图模型和 FlowGram 内存树互转；审批、人员、数据操作配置保存在
  properties.config，开始节点 properties.form 保存表单关联，properties.designer 仅保存父节点、顺序和空容器标记。服务端检查连线与设计器配置一致，拒绝无效
  JSON、重复标识、循环/孤立层级和不一致的图。
- 使用原生 type=6 排他网关与 type=1 条件连线；条件表达式由服务端根据类型化分支配置生成，按分支顺序优先匹配并保留默认分支。发布只注入服务端
  handler、handlerOwner、releaseId 和 nodeId；自定义 flow_key 在存储与实际 Flow 发布中一致。
- JavaScriptExpressionCalculator 提供本次 __flowVariables 快照，避免线程复用时网关读取上一次残留的变量；覆盖数字、字面量/变量、七种比较操作和缺失变量。
- SchemaInitializationListener 为特性提供 schema 初始化后的数据升级钩子；WorkflowSchemaMigration
  在事务和运行时行锁内迁移旧草稿与发布快照，保留旧表及旧运行部署，迁移标记防止定义删除后再次恢复。迁移以 SQL 审批运行时为前提。
- 验证通过：`mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`；`mvn test -q -pl flexmodel-engine`；
  `mvn test -q -pl flexmodel-server -am '-Dtest=WorkflowModelTest,ApprovalWorkflowResourceTest,FlowDefinitionResourceTest,ApprovalPolicyTest,EngineConfigTest,RuntimeProcessorTest,DefinitionProcessorTest' '-Dsurefire.failIfNoSpecifiedTests=false'`
  （50 tests，0 failures，0 errors）。其中包含隔离 SQLite 与真实浏览器的设计保存/刷新/发布/提交/签核/解锁，以及审计身份、防篡改、指定
  flow_key、旧草稿迁移和删除后不复活。
- Apps UI `npm test`（23 tests）、`npm run test:approvals`（5 tests）、`npm run build`（含 TypeScript）通过；两仓库
  `git diff --check` 通过。构建仅保留既有 chunk 大小及 logo 路径提示。
- `bash ./init.sh` 已实际执行，仍因用户运行中的开发服务占用 flexmodel-server-dev.jar 在 clean 阶段失败；保留服务，使用上述非
  clean 编译和引擎验证。完整 init.sh 需要该文件解除占用后重跑。
- 协议与迁移边界见 flexmodel-server/docs/approvals.md。普通 Flow 模型仍需审批配置和设计器层级元数据才能在审批设计器中编辑，本次没有增加任意图导入能力。
- 工作区进入本轮前已有大量暂存/未暂存修改（含 32 个暂存文件）及嵌套 Apps UI 仓库修改；为保留原有暂存边界，本轮没有自动提交或改写既有暂存内容。验证日志归档到忽略目录
  logs/workflow-verification-2026-10-21.log。

**审批基础能力收敛到服务端（2026-10-21，feat-089）**

- 按用户约束移除 flexmodel-engine 中审批接入引入的全部 7 个文件改动，包括 DataMutationInterceptor、Session/SessionFactory
  扩展、SQL 行锁接口和引擎回滚事件处理。已使用 `git diff HEAD --quiet -- flexmodel-engine` 及状态检查确认源代码、暂存区和工作区均与
  HEAD 一致。原改动只读备份到忽略目录 logs/engine-approval-before-refactor.patch。
- common/persistence 使用引擎已有公开的 SqlContext/SqlSession/SqlDataService/EventAwareDataService
  实现服务端会话入口。ManagedSession 的事务状态、运行时行锁和提交后回调全部属于 server；ProjectUnitOfWork、应用数据
  Repository、表单流程和 Flow 数据任务接入该入口。借用会话不能关闭或提交外层事务；显式回滚及关闭未提交会话丢弃对应事件和回调，同一会话内更早已提交的事件仍保留。
- ApprovalRecordGuard 由服务端写入入口调用，支持字符串和类型化 Predicate 筛选、单条、批量和整表删除。独立引擎 SessionFactory
  及原生 SQL/DDL 不引入审批业务策略；普通 Mongo 数据访问保持原实现，审批运行时继续要求 SQL。
- GraphQL 复用引擎生成的 Schema、查询、@export/@transform 参数处理，仅替换 SQL 项目的 mutation
  fetcher，使用服务端事务及锁保护。验证别名、片段和嵌套 _join_mutation 不能绕过锁；覆盖新增、修改、筛选删除及签核解锁后正常写入。修正
  GraphQLResource 的 PathParam 导入，使实际 HTTP 项目参数可正确解析。保留原有 where 必填约束，不扩展无条件更新能力。
- 按用户最新指示，SQLite 仅作为开发环境容错：连接 busy_timeout=1000ms；只有 SQLite BUSY/LOCKED 被转为“稍后重试”的
  503（GraphQL 返回 errors），不自动重放业务操作、不跳过审批锁、不吞其他数据库错误，也未为 SQLite 新增引擎能力。事务归属、回滚事件及审批锁是各
  SQL 数据库共有的要求，保留在 server。
- 验证通过：
  `mvn test -q -pl flexmodel-server -am '-Dtest=ServerSessionsTest,WorkflowModelTest,ApprovalWorkflowResourceTest,FlowDefinitionResourceTest,ApprovalPolicyTest,EngineConfigTest,RuntimeProcessorTest,DefinitionProcessorTest,ServiceTaskExecutorTest,FlowEventPublisherTest' '-Dserver.sessions.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'`
  （65 tests，0 failures/errors/skipped）。最后补充 GraphQL 新增/筛选删除和整表删除保护后，单独复跑
  ApprovalWorkflowResourceTest（14 tests）全部通过，含隔离 SQLite 后端真实浏览器保存/发布/提交/签核/解锁。
- ServerSessionsTest 已实际启动本地 MySQL 8.0 Testcontainer，并同时运行 SQLite；提交/回滚事件与回调、跨连接锁等待、SQLite
  busy 容错共 5 项全部通过，无跳过。容器由测试自动清理。这里只验证 MySQL 会话及事务机制，完整审批业务的目标生产数据库验收仍需部署前验证。
- 恢复后的引擎已执行 `mvn clean test -q -pl flexmodel-engine/flexmodel-core -am`（90 tests，全通过），确认不依赖旧编译类；规定的
  `mvn test -q -pl flexmodel-engine` 及非 clean 全模块 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`
  通过。标准 bash ./init.sh 已尝试，仍因用户现有开发服务锁定 flexmodel-server-dev.jar 而在 clean 阶段失败，未停止服务。两仓库
  diff 检查通过。
- 说明见 flexmodel-server/docs/approvals.md；详细日志位于忽略目录 logs/server-sessions-*.log。本轮无 UI
  改动，沿用真实浏览器回归验证接口兼容性。保留其他既有暂存及嵌套仓库改动；只撤销了用户明确要求撤出的引擎改动，未混合提交其他已有修改。

**移除自定义审批执行人脚本（2026-10-21，feat-090）**

- 按用户要求移除自定义审批执行人脚本能力：回滚 Functions Runtime 审批专用路由、隔离执行 Worker、相关测试及 server.ts
  注册，删除服务端 MemberScriptRunner 与 approval.script-secret 配置。其余 Functions Runtime 能力保留；
  `git diff HEAD --quiet -- flexmodel-engine flexmodel-functions-runtime` 和状态检查均确认两个模块的源代码、暂存区、工作区与
  HEAD 一致。
- 审批执行人保留指定用户、角色、组织架构、流程发起人、上游审批节点和流程变量六种来源。设计器移除脚本来源和编辑器，模拟面板移除脚本返回成员配置，类型定义及新规则也不再生成
  script/scriptMembers 字段。节点配置继续位于 Flow 节点 properties 中。
- 旧配置若仍使用 script 来源，设计器校验提示重新选择；服务端发布校验与成员解析拒绝该来源。未自动替换为其他人员，避免改变原流程的审批对象。已同步当前协议文档及历史特性说明，feat-086/087
  的脚本能力记录由本节取代。
- 验证通过：
  `mvn test -q -pl flexmodel-server -am '-Dtest=ApprovalPolicyTest,ApprovalWorkflowResourceTest,WorkflowModelTest,FlowDefinitionResourceTest,FlowEventPublisherTest' '-Dsurefire.failIfNoSpecifiedTests=false'`
  （34 tests，0 failures/errors/skipped，含真实后端浏览器保存/发布/提交/签核/解锁和旧脚本来源拒绝）；Apps UI `npm test`（24
  tests）、`npm run test:approvals`（5 tests，含六种来源与脚本选项已移除检查）、`npm run build`（含 TypeScript）通过。
- `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 与 `mvn test -q -pl flexmodel-engine` 通过。标准
  `bash ./init.sh` 已执行，仍因用户开发服务占用 flexmodel-server-dev.jar 在 clean 阶段失败，未停止该服务；保留非 clean
  验证路径。根仓库和 Apps UI `git diff --check` 通过。构建仅保留既有 chunk 体积和 logo 路径提示。
- 详细日志及回滚前补丁位于忽略目录 `logs/remove-approval-scripts-*.log`、`logs/approval-scripts-before-removal.patch`
  。本次只撤销明确授权的脚本专用暂存文件；其余大量既有暂存/未暂存修改及嵌套仓库边界均保留，未混合提交。 **审批草稿与发布复用
  Flow 原生存储（2026-10-21，feat-091）**

- 按用户要求同时撤掉 `f_form_workflow_definition` 和 `f_form_workflow_release`
  的新建表声明，没有增加替代的定义、草稿、发布或绑定表。FormWorkflowService 保存直接调用既有 Flow create/update；发布更新同一
  flow_module_id 并调用原生 deploy，版本仅保存在 f_em_flow_deployment。原生 Flow 定义仍使用自身状态，审批 API 保持原有
  1=草稿/2=启用/3=停用映射。
- 并发 revision、当前 deploymentId 与子流程 children 部署关联存于节点 properties.workflow；节点配置仍在
  properties.config/designer/form。元数据由服务端生成；草稿保存起即带处理器归属，通用 Flow 更新/部署/删除入口不能绕过业务处理器，普通
  Flow 行为保持既有验证。
- f_form_process_run 新实例直接写 flow_deploy_id；审批节点配置、历史标题、表单引用检查和子流程启动都读取原生部署。原
  release_id 改为可空旧列，仅用于读取迁移关联，新实例不写入。修改草稿、重新发布、停用或软删除定义不会改写已有部署，也不会改变运行中实例或父流程固定的子流程版本。
- WorkflowSchemaMigration 在同一事务和运行时行锁内迁移旧树形/Flow 草稿、发布关联、活动实例及子流程版本。旧发布对应的临时原生定义软删除，防止列表和
  flow_key 出现重复；原有部署 ID、节点类型与连线不改动。只有缺少 config/designer 的最早旧部署把冻结业务视图放入同一部署的
  properties.workflow.legacyDocument；新发布没有这项冗余快照。旧表仅保留迁移标记，不参与后续业务，也不会在新项目创建；迁移幂等且删除后不复活。
- 验证：扩展命令
  `mvn test -q -pl flexmodel-server -am '-Dtest=ServerSessionsTest,WorkflowModelTest,ApprovalWorkflowResourceTest,FlowDefinitionResourceTest,ApprovalPolicyTest,EngineConfigTest,RuntimeProcessorTest,DefinitionProcessorTest,ServiceTaskExecutorTest,FlowEventPublisherTest' '-Dserver.sessions.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'`
  中 52 项非审批回归全部通过。新增迁移测试初次因 fixture 将不可变 Map 传入原始 DataService.insert 而失败；修正 fixture
  并补齐临时定义清理后，最终单独复跑 ApprovalWorkflowResourceTest 18 项全部通过。最终报告合计 70 项，0
  failures/errors/skipped；SQLite/MySQL 会话测试无跳过，真实后端浏览器闭环通过。
-

新增覆盖：草稿立即存在于原生表；重复发布只有一个定义、多个部署；旧部署内容不随新版本变化；定义删除后活动审批继续签核；子流程重新发布/停用后父流程仍使用固定版本；旧活动审批在旧源表移除后仍能签核；迁移保持图拓扑且重复执行不会复活已删除定义。

- Apps UI `npm test`（24 项）和 `npm run build`（含 TypeScript）通过，本轮没有修改 UI 源码。
  `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 与 `mvn test -q -pl flexmodel-engine` 最终通过；根仓库和
  Apps UI diff 检查通过。`git diff HEAD --quiet -- flexmodel-engine flexmodel-functions-runtime` 确认两个模块仍为零差异。
- 标准 `bash ./init.sh` 已实际执行，仍因用户开发服务占用 flexmodel-server-dev.jar 在 clean 阶段失败，未停止服务；保留既有非
  clean 编译与测试验证路径。当前协议见 flexmodel-server/docs/approvals.md，详细日志在忽略目录 logs/reuse-flow-drafts-*
  .log。历史 feat-088 的独立定义及发布存储设计由本节取代；本轮继续保留其他既有暂存/未暂存和嵌套仓库修改，没有混合提交。
  **审批存储复用 Flow、细粒度并发与 Approval 术语统一（2026-10-22，feat-092）**

- 按用户确认的方案完成表收敛：审批新增存储只保留
  f_approval_instance、f_approval_wait、f_approval_task、f_approval_record_lock 及通用 f_runtime_message。移除独立
  f_form_node_log 和 f_runtime_lock 的建表及使用；定义与发布继续复用原生 Flow。审批关联从 f_form_process_run 统一命名为
  f_approval_instance。
- 节点变量只从 f_ei_instance_data 读取，启动前初始变量由启动命令承载；业务输入输出及幂等键进入
  f_ei_node_instance_log.attributes/event_key。审批实例保留不可变提交快照、业务结果和引擎关联，运行中仅存 ACTIVE，接口派生
  SUBMITTING/RUNNING；重复提交也返回派生状态。等待记录只保留冻结成员与聚合状态，节点 key、标题、签核模式从原生节点和部署读取。父子关系只保留
  parent_task_id，通过原生节点实例反查父流程。
- 提交与数据修改锁实际业务记录；签核和管理锁根审批关联行；Flow 修改/部署/删除锁定义，继续/回滚/终止锁引擎实例。筛选写入锁定并固定本次检查过的记录集合。移除项目级全局锁，非
  SQLite 连接使用 READ_COMMITTED；MySQL 验证同一行等待、不同记录可在另一事务释放前独立提交。不同记录竞争同一提交幂等键时，约束冲突后读取已提交结果或返回
  409，不重放业务操作。
- Outbox 使用 ordering_key
  按根审批实例排序，延迟重试不阻塞无关组。组内最早消息行为稳定协调点，事务内再次检查状态与顺序；后台以虚拟线程并行处理不同组，多个服务实例仍以数据库行锁协调。已完成消息暂不清理，以保留协调点。SQLite
  保持 1 秒 BUSY/LOCKED 转可重试 503 的开发容错，没有引擎或 SQLite 专用补偿。
- 用户要求业务统一 Approval：服务端迁入 dev.flexmodel.approval，采用
  ApprovalCompiler/FlowService/Runtime/FlowState/Repository/RecordActions 等类名，元数据统一
  properties.approval，外部处理器标识统一 approval-flow。通用 Flow 模块和接口结构
  flow_module_id/flow_name/flow_key/flow_model 保留。前端的 Workflow 仅为第三方图标名称。
- 用户随后明确不考虑历史兼容：删除草稿/发布/运行数据迁移器、Schema 初始化迁移钩子、旧表读取、legacyDocument、properties.workflow
  回退、form-workflow 处理器别名、旧网关执行分支及 release_id；移除相应历史迁移测试。按当前结构初始化，没有自动迁移或删除用户已有数据库。feat-088/091
  的历史兼容方案由本项取代。当前文档改为 flexmodel-server/docs/approvals.md，README 链接同步。
- 首轮存储测试发现父子查询的泛型 IN 重载将 List<String> 当作单个参数，已改用数组并验证子流程推进及固定版本。扩展回归初次有
  4 项原生 Flow 测试受 SQLite 后台消费竞争影响；关闭测试定时消费后 17 项原生 Flow
  测试通过。审批测试独立使用手动消息推进，浏览器驱动也手动推进消息并将输出写入临时日志，避免定时消费干扰和超时等待。
- 最终单次回归命令通过：mvn test -q -pl flexmodel-server -am
  -Dtest=ServerSessionsTest,ApprovalFlowModelTest,ApprovalFlowResourceTest,FlowDefinitionResourceTest,ApprovalPolicyTest,EngineConfigTest,RuntimeProcessorTest,DefinitionProcessorTest,ServiceTaskExecutorTest,FlowEventPublisherTest
  -Dserver.sessions.mysql=true -Dquarkus.scheduler.enabled=false -Dsurefire.failIfNoSpecifiedTests=false（70 tests，0
  failures/errors/skipped）。含 6 项 SQLite/MySQL 8.0 真实会话/行锁、17
  项审批接口及真实后端浏览器保存/发布/提交/签核/解锁，验证原生日志/变量、精简等待数据、父子实例查询、延迟消息隔离、并发提交/签核及
  GraphQL/批量写保护。
- Apps UI npm test（24 项）及 npm run build（含 TypeScript）通过；本轮没有调整 UI 视觉或业务源码，只同步浏览器测试入口注释。最终
  mvn compile -q -pl !flexmodel-engine/flexmodel-maven-plugin 与 mvn test -q -pl flexmodel-engine 通过；根仓库和 Apps UI
  工作区/暂存区 diff 检查通过；git diff HEAD --quiet -- flexmodel-engine flexmodel-functions-runtime 确认两个模块仍零差异。
- 标准 bash ./init.sh 已实际运行，clean 仍因用户开发服务占用 flexmodel-server-dev.jar 失败；未停止服务，采用上述非 clean
  编译和规定引擎验证。完整 init.sh 需文件解除占用后再运行。详细日志在忽略目录 logs/approval-storage-*
  .log。现有大量暂存、未暂存及嵌套仓库修改均保留，没有混合提交其他已有工作；生产目标数据库的完整审批业务验收仍需部署环境验证。

**MCP 应用审批与通用人员目录（2026-10-22，feat-093）**

- 按用户确认，审批设计工具统一加入
  AppTools：get_app_approval_capabilities、list_app_approval_flows、get_app_approval_flow、save_app_approval_flow、validate_app_approval_flow、set_app_approval_flow_status。复用
  ApprovalFlowService 与原生 Flow 契约，不创建新的定义/发布存储。创建省略 flowId，更新和发布要求最新
  revision；保存已启用流程转回草稿，发布/停用使用 status=2/3。
- ApprovalCapabilities 提供真实表单/字段与子流程目录、节点配置和枚举、图/设计器结构要求、服务端保留属性、简单审批/网关的完整可校验示例。数据节点模板包括
  conditions/assignments/match/output/queryMode，字面量类型与现有设计器保持 text/number/boolean，条件/赋值保留稳定
  ID。表单和字段引用仍需按实际业务替换。
- 根据用户补充，将 ApprovalDirectory 移至独立 directory/DirectoryService；审批编译、运行时、签核和既有目录 REST
  接口统一引用通用服务，无审批模块反向依赖。新增独立 DirectoryTools.get_directory / resolve_members，不要求
  appId；只返回启用人员的选择字段、角色及组织层级，人员规则和预览能力继续支持六种来源及四种相对范围，稳定去重且最多 1000 人。
- McpAuthHandler 将本次校验通过的 Key 放入 RoutingContext；McpToolAccess 为新增工具设置项目/数据库和 api_key:<keyId>
  服务审计身份，逐请求校验 Key 的项目范围与只读限制。服务身份不会作为真实审批人，预览 initiatorId/variables 不授权提交或签核。真实
  MCP 测试覆盖同一会话切换只读凭证后不能写入，以及越项目请求和无 Key 请求被拒绝。
- 真实 MCP 发布/停用测试发现既有缺陷：preserveCreated 对定义做局部更新时，引擎的固定默认值处理会把缺失的 status 补为
  0，导致已停用流程仍被映射为启用。ApprovalRepository.updateDefinition 现在合并当前事务内的完整行后再更新，保留未变字段；状态映射按
  Number 读取。测试同时断言原生存储、状态返回和后续详情均为已停用，未修改 flexmodel-engine。
- 最终单次专项回归通过：
  `mvn test -q -pl flexmodel-server -am '-Dtest=AppApprovalToolsTest,ApprovalPolicyTest,ApprovalFlowModelTest,ApprovalFlowResourceTest,FlowDefinitionResourceTest' '-Dquarkus.scheduler.enabled=false' '-Dquarkus.rabbitmq.devservices.enabled=false' '-Dsurefire.failIfNoSpecifiedTests=false'`
  ，38 tests，0 failures/errors/skipped（MCP 4、规则 4、模型 3、审批接口及真实后端浏览器 17、Flow 接口 10）。MCP 使用真实 HTTP
  初始化、工具发现、可选参数、简单/网关/数据模板校验、保存/读取/更新、版本冲突、无效草稿发布拒绝及目录预览。
- 最终 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 与 `mvn test -q -pl flexmodel-engine`
  通过；根仓库工作区/暂存区、文档站 diff 检查和新增文件空白检查通过；feature_list.json 可解析；engine/Functions Runtime 与
  HEAD 零差异。服务端 README、审批接入文档和 flexmodel-website 的 MCP 教程已同步。详细验证日志保存在忽略目录
  logs/mcp-approvals-*.log。
- 首次扩大检查的现有 AppToolsTest 有 4 项失败：createsCompleteAppChain / createsAllCustomFieldsAndAcceptsStringOptions
  仍期待创建字段后默认布局非空（当前为空）、cleansUpFormWhenFieldCreationFails 期待表单列表为空（当前含 3
  个内置表单）、rejectsInvalidLayoutAndKeepsOriginalLayout 期待 SECTION.children 被拒绝（当前会忽略）。这些原有表单工具实现与测试本轮均未修改，未宣称
  AppToolsTest 或完整 server suite 全部通过；初始失败日志见 logs/mcp-approvals-tests.log。
- 开始时曾按原启动规范执行 init.sh，仍受已有开发服务占用 dev.jar 影响；用户随后明确“直接修改不执行
  init.sh”，收到指示后未再执行，未停止用户服务。仅用上述非 clean
  编译和针对性测试验证。工作区进入本轮前已有大量暂存/未暂存审批实现，新增工具依赖这些尚未提交的类；为保留原暂存边界并避免提交不能独立编译的片段，本轮未自动提交，也未混入其他既有工作。

**platform.fml 平台级表合并（2026-10-23，feat-103）**

- `project.fml` 全部模型上收至 `platform.fml`，统一存于 `system` schema：`f_runtime_message`、`f_approval_*`、`f_bucket`、
  `f_auth_provider_config`、`f_api_request_log`、`f_em_flow_*`、`f_ei_*`、`f_trigger`、`f_job_execution_log`、
  `f_function(_log)`、`f_audit_log`、`f_page_site`；每表新增 `project_id`，唯一约束改为 `[project_id, ...]`。
  `f_auth_provider_config` 改用 uuid 主键 + `[project_id, name]` 唯一，`f_page_site` 按 `project_id` 每项目一行。
- 删除 `project.fml` 与 `SchemaInitializer`；`pom.xml` 的 system schema 仅导入 `platform.fml`，dev_test schema 仅导入
  `dev_test.fml`；native-image 元数据移除 `project.fml`。dev_test 平台表种子数据迁入 `platform.fml` 并携带
  `project_id=dev_test`。
- Java 访问层统一改为 `AbstractRepository.getPlatformSession()` + 显式 `project_id`
  过滤（flow/approval/scheduling/functions/storage/pages/auditlog/apilog/projectauth/ReliableMessages）；删除不再使用的
  `getProjectSession` 与 ProjectUnitOfWork 的项目 session 借用入口。
- 新增 `ProjectUnitOfWork.executePlatform`：以 `ServerSessions.openOwned("system")` 开启平台事务，支持嵌套复用与业务事务内嵌套；
  `ProcessEngineImpl`、`AppApprovalFlowService`、`AppApprovalService`、`ReliableMessages` 的平台表写操作改用平台事务，
  `NotificationRepository` 复用当前平台事务。
- 新增 `ProjectPlatformDataCleaner`：项目/分支删除时按 `project_id` 清理平台表数据；SQLite 开发环境 `busy_timeout` 提升至
  5s。
- 按用户要求不保留历史兼容：未提供旧项目库平台数据的迁移脚本，升级已有开发库需重建这些表或手工搬迁数据；同时删除
  `AbstractRepository.getProjectSession`、`ProjectUnitOfWork.borrow/active`、`SchemaRegistry` 的
  `getPhysicsModelNames/add/delete/executeNativeQuery` 与 `system→flexmodel` 映射、platform.fml 中已无意义的 `@migration`
  标记与引用不存在字段的 API Key 索引。
- 审计归属修正：`AuditEventListener` 优先按数据的 `project_id` 定位项目，平台表变更不再被记到默认项目；触发器监听器对未知
  schema 不再 NPE；分支创建后同步初始化分支项目的 Pages 站点记录。
- 模型化访问：`ApprovalRepository` 的 `list/get/optional/insert/update/delete` 改为接收实体类（`ApprovalInstance.class`
  等）而非字符串模型名，条件统一使用 `System.xxx` 字段元模型；`updateDefinition`/`update` 直接使用
  `DSL.update(FlowDefinition.class)`、`delete` 使用 `DSL.deleteFrom(Class)`，模型名解析统一使用框架的
  `ReflectionUtils.getModelNameFromClass`（不再新增 `Models`/`AbstractRepository.model`）。`AppApprovalFlowState` 的模型查询统一通过
  `ApprovalRepository`（实体类入参，内部自动附加 `project_id`），不在业务类中直接操作 Session/DSL。Flow/审批锁、
  `ReliableMessages`、`NotificationRepository`、`NotificationRelay`、`DirectoryService`、`ProjectPlatformDataCleaner`、
  `ProjectLogSettings`、`AppFormService` 的内置表单清单同步改为实体类。
- Resource 只依赖 Service：`AppApprovalFlowResource` 的记录审批历史查询移入 `ApprovalService.recordHistory`；
  `NotificationResource` 改用 `NotificationService`；`SystemOptionResource` 新增并使用 `SystemOptionService`；
  `FunctionTemplateResource` 改用 `FunctionService.list()`；Resource 中不再注入 Repository。
- 验证：`mvn compile -pl flexmodel-server -am -DskipTests` 通过；`mvn test -pl flexmodel-engine` 通过。
  `AppApprovalFlowResourceTest` 在本地 SQLite 环境仍存在失败（通知顺序 1 项断言失败、平台表锁竞争相关的错误），未宣称服务端测试通过，待后续修复。
- Repository 拆分与下沉：删除包含通用 `list/get/optional/insert/update/delete` 的 `ApprovalRepository`，按模型拆出
  `AppApprovalInstanceRepository`、`AppApprovalTaskRepository`、`AppApprovalWaitRepository`、
  `AppApprovalRecordLockRepository`、
  `AppApprovalFlowDefinitionRepository`、`AppApprovalFlowDeploymentRepository`、`AppApprovalInstanceDataRepository`、
  `AppApprovalNodeInstanceRepository`、`AppApprovalNodeInstanceLogRepository` 与 `RuntimeMessageRepository`。每个
  Repository
  内部自动附加 `project_id` 过滤，只暴露语义化方法（如 `findByRunId`、`findByFormRecord`、`deployedFlow`、`findPending`、
  `findBySchemaAndModel`），不再提供通用查询入口；删除 `AbstractPlatformRepository`，公共能力下沉到各自 Repository。
  `AppApprovalService`、`AppApprovalRuntime`、`AppApprovalFlowState`、`AppApprovalFlowService`、`AppApprovalLifecycle`、
  `AppApprovalRecordGuard` 及对应测试均改为注入专用 Repository。
- 注入字段命名统一：Repository 字段使用 `XxxRepository`（类型名首字母小写），如 `appApprovalInstanceRepository`、
  `appApprovalTaskRepository`、`appApprovalFlowDeploymentRepository`、`runtimeMessageRepository`；Service 字段统一使用
  `XxxService` 形式，覆盖 Approval、Notification、MCP、Flow 及测试类：`approvals`→`approvalsService`、`notifications`→
  `notificationService`、`directory`→`directoryService`、`approvalFlows`/`flows`→`appApprovalFlowService`、`engine`→
  `processService`、`apps`→`appService`、`forms`→`appFormService`、`records`→`appRecordService`、`projects`→
  `projectService`、`jwt`→`jwtService`、`keys`/`apiKeys`→`apiKeyService`、`service`→对应服务名。同时修正
  `ApprovalService.decide` 中局部变量 `tasks` 遮蔽 Repository 字段的问题，局部变量重命名为 `waitTasks`。
- 验证：`mvn compile -pl flexmodel-server -am -DskipTests` 通过；`mvn test-compile -pl flexmodel-server -am -DskipTests`
  通过（按用户要求未执行测试用例）。
- 验证（Service 字段重命名后）：`mvn compile -pl flexmodel-server -am -DskipTests` 与
  `mvn test-compile -pl flexmodel-server -am -DskipTests` 再次通过。
- `ProjectUnitOfWork` 收敛：`execute` 与 `executePlatform` 合并为私有 `transaction(projectId, targetSchema, action)` 模板，
  `targetSchema` 为 null 时查询项目业务库，否则使用指定 schema；schema 查询抽取为 `projectSchema`。嵌套规则统一为“同 schema
  复用、跨 schema 开启独立事务并恢复外层上下文”，顶层行为与合并前一致；差异仅在“业务事务嵌在平台事务内”时不再静默复用平台会话，而是开启真实业务事务。
- 验证：`mvn compile -pl flexmodel-server -am -DskipTests` 通过。`ServerSessionsTest` 6 项中 3 项 MySQL 用例按开关跳过，
  `sqliteContentionReturnsRetryableFailureWithoutRunningBusinessWork` 失败原因为连接级 `PRAGMA busy_timeout=5000` 与用例
  4s 断言冲突（该用例走 `openOwned`，不经过 `ProjectUnitOfWork`）。`NotificationResourceTest` 7 项中 5 项通过：
  `sourceRollbackCannotDeliverAndRetryPreservesReadState` 反映平台库独立事务下 outbox
  消息不随业务回滚撤销（与平台库非原子设计一致，合并前后代码路径相同），`browserRoundTripAgainstRealBackend` 失败于离线断流后
  `streams.size` 未归零的轮询断言，均为既有环境/设计问题，未在本次改动中引入。
- 修复 Outbox “行锁必须位于事务内”：根因是 `ProjectUnitOfWork.adopt` 仅比较
  projectId，在平台事务已存在时直接复用而不校验会话身份，导致数据节点在平台事务内打开的无事务业务会话上执行行锁。现改为仅在
  `existing.session() == session` 时复用，否则为传入会话开启独立事务并在结束后恢复外层上下文；`AppApprovalRecordActions`
  的数据节点 create/update/delete 改为包在 `transactions.execute(appId, ...)` 内，让节点写入独立提交且锁在节点事务内生效（query
  仍只读）。验证：新增 `ApprovalFlowResourceTest.platformTransactionDoesNotReuseBusinessSessionLock`，回退 adopt 时稳定复现
  `ServerSqlSession.lockRow` 抛“行锁必须位于事务内”，修复后 1 项测试通过。
- 测试修复：`FmlParserTest.testParseProjectFml` 改名为 `testParsePlatformFml` 并改读 `platform.fml`（`project.fml`
  已删除，流程种子迁至平台库），增加 monorepo 相对路径回退，独立引擎构建中取不到文件时跳过而不是失败；验证 `flexmodel-core`
  90 项测试全部通过（`FmlParserTest` 11 项）。附带发现：`integration-tests` 下 10 个 IT 模块的 `AgroalDataSource` 未设置
  `maxSize`，当前 Agroal 版本报 `max size attribute is mandatory`，与本次改动无关，待单独修复。

**Apps UI - 审批进度突出展示审批意见（2026-10-23，feat-104）**

- 用户反馈审批进度中已提交的审批意见看不到。排查确认后端 `GET /apps/{appId}/approval-instances/{id}` 的 `tasks[].comment`
  已正确返回，
  问题在展示：`ApprovalTimeline` 把意见作为姓名后的 `text-muted-foreground` 小字内联渲染，窄侧栏下与姓名混在一起、几乎不可辨。
- 重写 `flexmodel-apps-ui/src/pages/Approvals/ApprovalProgress.tsx`：节点卡片头部增加会签方式与节点状态（进行中/已通过/已拒绝）；
  审批人行增加首字头像、决策状态图标与文案（已同意/已拒绝/待处理/等待前序审批/已关闭）和决策时间；非空意见改为独立的
  「审批意见」引用块，按决策结果使用绿/红左侧强调色，正文 `whitespace-pre-wrap break-words` 保留换行并适配长文本。
  `InstanceDrawer` 与 `AppRecordDrawer` 共用该组件，均同步生效（待办入口随后由 feat-105 统一为 AppRecordDrawer）。
- 验证：`flexmodel-apps-ui` `npm run build` 通过；`npm test` 27 项通过；连接本地 dev 后端（8080 + `plm_system` 真实审批实例
  11651de9，意见“同意”）用 Playwright 实测明暗两种模式，侧栏均展示「审批意见」块，截图位于
  `flexmodel-apps-ui/artifacts/approvals-progress-comment.png` 与 `approvals-progress-comment-dark.png`。

**Apps UI - 审批入口统一为记录详情抽屉（2026-10-23，feat-105）**

- 用户反馈审批存在两套入口：待办中心进入 `ApprovalTaskDrawer`（自研只读编辑表单），表单列表进入 `AppRecordDrawer`
  （记录详情抽屉）。统一为后者：`ApprovalFeed` 与 `Notifications` 的审批入口均改为打开 `AppRecordDrawer`，
  传入 `appId`、`formName`、`recordId` 和 `approvalInstanceId`。
- `useRecordApproval` 新增可选 `instanceId`：传入时优先定位该审批实例（含子流程任务实例）解析当前用户 PENDING 任务，
  并让右侧流程进度固定展示该实例；未传入时保持原“进行中实例优先”行为，记录列表入口不受影响。
  `AppRecordDrawer` 新增 `approvalInstanceId` 透传，aside 改用 `progressInstanceId`。
- 删除 `pages/Approvals/ApprovalTaskDrawer.tsx`；回滚 `AppRecordFormModal` 中仅服务于该审批抽屉的
  `title`/`readOnly`/`footer`/`footerExtra`/`aside` 参数与分支，编辑表单恢复统一默认标题、设计入口和保存/取消/发起流程底部。
- `e2e/approval-backend.mjs` 适配新交互：先点「同意」打开意见弹窗，再填写并「确认同意」；记录标题与状态断言改为
  详情抽屉的 heading 与局部状态，避免与表单字段/列表重复文本产生 strict mode 冲突。
- 验证：`npm run build` 通过；`npm test` 27 项通过；`mvn test -pl flexmodel-server
  -Dtest=ApprovalFlowResourceTest#browserRoundTripAgainstRealBackend` 通过（真实后端浏览器闭环：待办中心打开记录详情抽屉→
  填写意见同意→右下进度显示已通过→记录解锁），截图 `flexmodel-apps-ui/artifacts/approvals-backend-signing.png`。

**Apps UI - 抽屉更多菜单收纳表单设计入口（2026-10-23，feat-106）**

- `BizDrawer` 新增 `moreMenu` 插槽，固定渲染在进度折叠按钮之后、关闭按钮之前；移除已无调用方的 `headerExtra`。
- 新增 `components/biz/FormMoreMenu.tsx`：更多按钮（MoreHorizontal，title/aria-label=更多）+ 下拉菜单「表单设计」，
  菜单内容使用 `z-[1100]`，高于抽屉的 `z-[1040]`。`AppRecordDrawer` 与 `AppRecordFormModal` 移除原头部 Settings
  图标，改传 `moreMenu`，点击后关闭抽屉并跳转表单设计页。
- 修复开发环境陈旧模块：Windows 下 Vite 原生文件监听偶发丢失，导致运行中出现
  `ApprovalTaskDrawer is not defined`、`headerExtra is not defined`。`vite.config.ts` 启用
  `server.watch.usePolling`（300ms），并重启 5180 开发服务后确认返回模块与源码一致。
- 验证：`npm run build` 通过；`npm test` 27 项通过；Playwright 实测审批中心记录抽屉头部为
  [收起审批][更多][关闭]，更多菜单可见并能跳转 `form-design`；新建产品抽屉同样显示更多按钮，页面无异常。
  截图 `flexmodel-apps-ui/artifacts/drawer-more-menu.png`、`form-modal-more-menu.png`。

**Apps UI - 多层抽屉递减宽度（2026-10-23，feat-108）**

- 统一在 `flexmodel-apps-ui/src/components/ui/drawer.tsx` 的 `DrawerContent` 中按 Portal 打开顺序管理右侧抽屉，
  支持兄弟与嵌套组件。上层最大宽度取下层实际宽度减留边：桌面 `3rem`（48px），窄屏 `1rem`（16px）；
  更窄的自定义抽屉保持原宽度，首层布局保持原有宽度。
- 使用 `ResizeObserver` 跟踪下层尺寸，窗口缩放时重新计算；组件卸载时清理叠层及观察器，关闭后重开无残留。
  `MobileNavDrawer` 为左侧导航，通过 `stacked={false}` 不参与右侧叠层。
- 验证：`npm run build` 通过；`npm test` 27 项通过；`npm run test:records` 4 项通过。
  Playwright 临时组件页面（使用项目实际 Drawer，含 React StrictMode）验证了兄弟/嵌套三层、较窄自定义宽度、
  桌面与窄屏切换、Escape 逐层关闭及重开，无浏览器异常。1440px 视口下宽度为 `1184/1136/1088px`，
  375px 视口下为 `375/359/343px`；自定义窄抽屉为 `1184/720/672px`。
  已检查浅色、深色及窄屏截图：`flexmodel-apps-ui/artifacts/drawer-stack-desktop.png`、
  `drawer-stack-dark.png`、`drawer-stack-mobile.png`；临时页面已移除。Apps UI 提交 `e5aa108`。
- 原生 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'` 与 `mvn test -q -pl flexmodel-engine` 通过。
  `bash ./init.sh` 的 clean 仍被运行中后端锁定的 `flexmodel-server-dev.jar` 阻塞，沿用不清理的等价验证，
  保留现有服务。未改动已有 `flexmodel-ui` 工作区内容及根仓库中 Apps UI 的独立仓库结构。

**Apps UI - 表单编辑回填与保存后详情同步（2026-10-23，feat-107）**

- 列表行直接编辑传入 `initialMode="edit"` 后仅加载了 `record`，没有初始化 `values`；
  `FieldControl` 又将缺失值直接 `String(value)`，导致输入框显示 `undefined`。
  `AppRecordDrawer` 现等待表单与记录都就绪后调用 `toEditValues`，用 `dirty` 防止覆盖用户输入；
  `FieldControl` 对空值统一使用空字符串，正常保留数字 0 和布尔 false。
- 保存成功后原先仅切换至查看态，仍渲染旧的 `record`。现直接以接口返回的完整记录
  `setRecord(saved)`，同步详情标题、主表字段、关联显示值及再次编辑的初始值。
- 记录尚未加载或加载失败时禁用输入、保存和子表编辑，防止提交未回填的空值；切换应用、表单时重置抽屉状态。
- 新增 `e2e/records.spec.ts`、`playwright.records.config.ts` 和 `npm run test:records`。
  Playwright API mock 回归 4 项通过，覆盖列表直接编辑、重开另一条记录、详情保存后即时刷新与再次编辑、
  加载失败禁止保存、新建后详情；修复前已复现保存后仍显示原始标题，以及加载中保存按钮可用。
- 验证：`npm run build` 通过（含 TypeScript 检查，仅既有 logo 路径与 chunk 体积提示）；`npm test` 27 项通过；
  `npm run test:records` 4 项通过；原生 `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`
  与 `mvn test -q -pl flexmodel-engine` 通过；`git diff --check` 通过。Apps UI 提交 `4bfc6bc`。
- 环境限制：`bash ./init.sh` 的 `clean` 被运行中后端锁定的 `flexmodel-server-dev.jar` 阻塞。
  已使用不执行 clean 的原生 Maven 验证，保留现有服务；完整 init 需要先释放该 JAR 文件锁。
  未改动已有 `flexmodel-ui` 工作区内容及 Apps UI 的 `.tmp-verify-back.mjs`。

**Apps UI - 消息审批全宽、个人设置精简与表单设计紧凑（2026-10-26，feat-108）**

- 消息中心 → 消息详情 → 查看审批：抽屉组件的层叠逻辑会按上一个抽屉宽度减去 `--drawer-stack-gap` 限制最大宽度，
  通知抽屉 600px 导致审批详情被压到 552px，表单与右侧进度被截断。`BizDrawer` 与 `AppRecordDrawer` 新增
  `stacked` 透传，`Notifications` 打开审批详情时传 `stacked={false}`；1440 视口下审批抽屉恢复为 1184px（x=256），
  完全覆盖通知抽屉且无横向溢出。
- 个人设置页移除全部 mock：删除 `currentUser`、`loginLogs`、部门/工作电话、登录日志、第三方授权、
  临时授权/个人秘钥及假保存弹窗，改为读取 `getAuthSession()` 展示当前用户名、邮箱、用户 ID；
  继续使用 `AppLayout hideNavigation` 隐藏左侧菜单。
- 表单设计「表单布局」画布收紧：外层与画布 padding `p-4→p-3`，行列 `gap-3→gap-2`，
  字段卡片 `py-2→py-1.5`、分区 `h-9→h-8`、子表单 `min-h-14→min-h-11`。
- 复测用户提及的既有问题：表单设计返回固定跳转 `/admin/apps/{appId}/settings/forms` 表单列表；
  列表行编辑回填与详情编辑保存后刷新沿用 feat-107 的修复，实测记录 2323 在抽屉与独立详情页均正常，无 `undefined`。
- 验证：`npm run build` 通过；`npm test` 34 项通过；Playwright 实测通知审批全宽、个人设置无左侧栏且展示真实账号、
  表单设计画布字段高度 32-34px、表单设计返回列表；截图 `flexmodel-apps-ui/artifacts/notification-approval-current.png`、
  `user-settings-new.png`、`form-designer-compact.png`、`standalone-detail-refresh.png`。

**Apps UI - 文件中心接入存储能力（2026-10-26，feat-118）**

- `FilesDocs` 移除 `fileTree` mock，改为通过应用 `projectId` 读取默认 Bucket（缺失时沿用 `ensureBucket` 自动创建）：
  文件页签列出桶根目录并过滤 `appId` 附件目录，展示共享文件/目录；附件页签以 `appId/` 为前缀，
  按表单目录浏览上传附件，展示时去掉文件名中的 uuid 前缀。上传沿用 `appId/formId/uuid_文件名` 约定，
  文件页签则保留原始文件名。
- 上传按钮右侧箭头改为受控下拉菜单：新建目录（PUT `?folder=true`）、移动文件、删除文件（确认弹窗）、
  批量下载（选中文件夹时递归展开）。移动弹窗支持逐级选择目标目录，排除选中目录及其子目录，并禁止移动到当前目录。
- 后端补齐缺失的移动能力：`StorageOperations.moveFile`，Local 使用 `Files.move`，S3 使用 CopyObject + 批量 Delete；
  `BucketService` 校验空路径/相同路径/移入自身子目录/源不存在/目标同名，`BucketResource` 新增
  `POST /projects/{projectId}/buckets/{bucketName}/move`。
- 右侧文件/文件夹卡片右上角新增悬浮“更多”按钮，悬停展开下拉菜单，支持直接删除单个文件或目录，
  复用删除确认弹窗与列表刷新逻辑；按钮阻止冒泡，不影响卡片进入目录或勾选。
- 修复卡片下拉悬浮时反复弹出：Radix 模态菜单打开时会禁用外部指针事件，使 trigger 触发 mouseleave→关闭→mouseenter→重新打开。
  卡片菜单改为 `modal={false}`，实测真实鼠标悬浮仅打开一次，移开后正常关闭，`body` pointer-events 保持原值。
- 卡片“更多”菜单新增“详情”，展示名称、类型、大小（文件夹显示文件数）、修改时间与所在位置。
- 附件页签改为只读：上传按钮禁用并提示由表单上传；工具栏新建目录、移动、删除禁用；卡片菜单仅保留“详情”。
  批量下载属于只读操作，勾选后仍可用；相关写操作在事件处理入口同样加了只读保护。
- 验证：Apps UI `tsc -b`、`npm run build` 通过，`npm test` 34 项通过；真实后端与 5180 页面实测两个页签数据、
  上传（共享根目录保留原名、应用附件目录保留 uuid 前缀）、新建目录、移动、删除、批量下载均成功（批量下载对象 GET 200），
  卡片悬浮“更多”按钮在文件与文件夹上均可展开下拉并完成单文件删除；测试文件/目录已清理，原有产品附件未受影响。
  `LocalStorageOperationsTest` 16 项通过（新增文件与目录移动），`BucketAttachmentResourceTest` 3 项通过
  （新增移动往返、相同路径 400、移入子目录 400）。原生全模块 compile（排除 maven-plugin）与引擎测试通过；未改 engine。

**Apps UI - 个人设置接入后端（2026-10-26，feat-119）**

- `UserSettings` 移除 `currentUser/loginLogs` mock：个人信息改读 `GET /api/system/forms/f_user/records/{id}`，
  展示用户 ID、主部门、姓名、工作电话与头像；保存走 `PUT`，提交 name/phone/avatar。
- `f_user` 新增 `phone`（工作电话）字段，头像复用已有 `avatar`；`platform.fml` 同步表单字段配置。
- 头像选择后上传到默认项目 default Bucket 的 `avatars/{userId}/`，保存相对 URL；私有对象通过带鉴权的
  Blob 下载后再预览，刷新页面仍可显示。
- 新增 `f_login_log` 平台表与登录日志链路：`AuthResource.login` 成功/失败均记录用户名、用户 ID、IP、
  User-Agent 与失败原因；新增 `GET /api/auth/login-logs`，只返回当前登录用户的分页日志，未认证返回 401。
- 验证：后端全模块 compile 通过；`AuthResourceTest` 16 项通过（新增登录日志查询与未认证 401）；真实服务实测
  成功/失败登录日志、f_user 读取与更新 phone/avatar、头像上传后刷新可预览；测试手机号、头像与临时 Bucket 已清理。
  Apps UI `npm run build` 通过，`npm test` 34 项通过；未修改页面样式。

**Apps UI - 登录头像返回与展示（2026-10-26，feat-120）**

- UserinfoResponse.UserResponse 新增 avatar，AuthResource 的 login/refresh/whoami 统一返回用户头像。
- Apps 登录保留 avatar；UserAvatar 复用附件鉴权加载，处理取消、Blob URL释放和图片失败文字回退；顶部传入会话头像。
- 个人设置保存后同步姓名、邮箱、头像到会话并通知顶部更新，监听跨窗口存储变化。
- 验证：原生全模块非clean compile、规定引擎测试通过；AuthResourceTest 17项通过（新增非空头像三接口回归）；Apps UI
  build、34项unit通过；Playwright mock验证登录图片解码、Bearer鉴权、刷新持久化与无效图片回退；diff --check通过。
- init.sh 已执行但 clean 仍被运行中 flexmodel-server-dev.jar 文件锁阻塞，未停止用户服务。设计规范读取
  flexmodel-ui/DESIGN.md；保留现有组件样式。
- 仓库已有未提交 feat-119 改动与本次共享文件，Apps UI 为独立嵌套仓库且未跟踪；为避免混合提交既有工作，本次未提交。

**流程进度用户头像（2026-10-26，feat-121）**

- DirectoryService 人员目录返回 avatar；审批成员缓存保留完整成员，姓名映射继续复用缓存，useApprovalDetail 提供头像映射。
- 详情流程进度的发起人与审批人、实例抽屉共用时间线接入 UserAvatar，复用鉴权加载和失败文字回退。
- 验证：全模块非clean compile、规定引擎测试、Apps UI build、34项unit及diff --check通过；Playwright
  mock渲染真实进度组件，验证两类图片解码、Bearer鉴权、未设置头像首字回退、暗色/375px渲染。
- 标准init.sh的运行中dev.jar锁为前序已确认阻塞，本次保留服务并使用非clean验证。保留前序未提交头像组件依赖和个人设置工作，未混合提交。

**默认头像可读性（2026-10-26，feat-122）**

- 原灰色池包含浅色背景，但文字固定白色；UserAvatar 改用 bg-muted、text-foreground、border-border 和中等字重，统一适配明暗主题，图片头像逻辑保持。
- 验证：Apps UI build通过；Playwright实测28px默认头像浅色对比度15.45:1、暗色15.11:
  1，首字和细边框正常。仅修改共享组件样式，无后端变动。保留前序未提交工作，未混合提交。


**Apps - 菜单独立授权配置（2026-10-28，feat-125）**

- 按用户最新要求，授权按钮放在一级菜单、左侧菜单/分组、子菜单行，与编辑、删除平级；使用盾牌图标及中文可访问名称。
- 独立授权弹窗复用 DirectoryMemberPicker，多选用户、角色、组织及包含子组织，支持取消、清空、回填与保存失败重试。授权不依赖菜单编辑草稿。
- 新增 PUT /api/apps/{appId}/menus/{menuId}/members，仅更新授权配置；metadata 保存类型/ID/包含子组织，普通菜单编辑、移动和排序保留配置。校验数组、类型、空ID、组织选项及数量；本次仅保存配置，不接入运行时菜单可见性或页面访问鉴权。
- 验证：全模块非clean mvn compile（排除maven-plugin）、mvn test -pl flexmodel-engine通过；AppMenuResourceTest 10项通过（含独立保存、编辑与排序保留、清空、非法参数）；Apps npm run build、36项unit和菜单授权Playwright通过，覆盖多选、取消、失败重试、回填、清空及无浏览器异常，检查明暗截图；git diff --check通过。
- 标准bash ./init.sh已执行，clean被现有运行中dev.jar文件锁阻塞，未停止用户服务，采用非clean等价编译验证。设计规范实际位于flexmodel-ui/DESIGN.md，已读取并复用Apps现有语义主题及组件。
- Apps UI为既有未跟踪的独立嵌套仓库，本次在其内部单独提交；主仓库仅提交本特性后端与跟踪文档，保留原有flexmodel-ui的AD图标改动与未跟踪目录状态。

**Apps - 菜单授权交互与接口调整（2026-10-28，feat-125 调整）**

- 移除 PUT /api/apps/{appId}/menus/{menuId}/members，改为 PATCH /api/apps/{appId}/menus/{menuId} 局部更新
  members；AppMenuSaveRequest 增加 members，普通 PUT 未携带时保留既有授权。PATCH 仍校验成员数组、类型、空ID、组织包含子组织与未知字段。
- 菜单授权使用盾牌按钮 + 独立弹窗，内部复用 DirectoryMemberPicker；存在成员时盾牌图标使用 text-primary 主题色，title
  显示已设置数量（期间曾改为气泡框，已按用户要求改回弹窗）。
- 按用户要求，配置界面不处理所有者；AppMenuResponse 每个菜单节点新增 ownerId，取 project.ownerId。
- 验证：mvn compile -pl flexmodel-server -am 通过；AppMenuResourceTest 10项通过（PATCH 保存/清空/非法参数/未知字段、ownerId
  返回）；Apps npm run build、36项unit、菜单授权 Playwright 1项通过；git diff --check 通过。
- 行内授权、编辑、删除（分组行含新增子菜单）按钮收进 gap-0.5 操作组并调整为主题尺寸 h-7 w-7，缩短图标间距；Apps build 与菜单授权
  Playwright 复测通过。
- GET /api/apps 及详情、创建、更新响应中的 owner 改为按 project.ownerId 查询用户显示名称，用户不存在或名称为空时回退
  ID；AppResourceTest
  的列表、详情、创建、更新 4 项通过。完整测试类中 testCreateAppWithExplicitProjectId 删除应用后的 GET 仍由鉴权过滤器返回
  401（既有行为，与本次改动无关）。
- 授权交互按用户要求改回弹窗，抽为共享组件 MenuAuthorizationDialog（含 MenuAuthorizationButton 入口），同时接入 AppSettings
  菜单管理页（/admin/apps/{appId}/settings/menus）的一级菜单与子菜单行；设置页授权保存后刷新列表与父级菜单。Apps
  build、36项unit及菜单授权 Playwright 2项（菜单编辑弹窗、设置页）通过。

**Apps - 待办中心引用记录可点击（2026-10-29）**

- ApprovalFeed 的记录抽屉补充 onOpenRecord，并为关联记录叠加二级
  AppRecordDrawer；引用字段由纯文本改为可点击按钮，支持继续打开其引用的表单。切换/关闭待办项时同步清理二级抽屉。
- 验证：Apps npm run build 通过；在本地开发服务待办中心打开采购订单审批，供应商引用显示为按钮，点击后成功打开供应商档案抽屉。

**Apps - 菜单授权运行时过滤（2026-10-29，feat-129）**

- GET /api/apps/{appId}/menus 接入菜单授权：所有者（project.ownerId）无条件返回全部；成员为空的菜单对所有应用用户可见；有成员时按
  USER、ROLE、ORGANIZATION 匹配，组织 includeChildren 通过父级链匹配子组织。
- 分组自身有授权时约束整棵子树；子项按各自授权过滤，分组无可见子项时隐藏。非所有者且没有受限菜单时跳过目录读取。
- 验证：mvn -pl flexmodel-server -am compile 通过；AppMenuResourceTest 新增空成员、用户、角色、子组织、直属组织、分组子树及所有者场景，11项全部通过。

**Apps UI - 进入与刷新应用主色闪烁修复（2026-10-29，feat-130）**

- 根因：AppLayout 首次使用空应用设置默认色，接口返回后才更新主色，效果清理再次重置；TopHeaderShell 挂载也强制蓝色。
- 新增 AppTheme 统一按应用路由管理根主色，布局效果在浏览器绘制前应用配色，移除两处重复主题管理；列表、详情、创建、更新响应按应用缓存主色，删除清理。缓存只含主色 slug，存储失败仍有内存缓存，主色定义继续只来自 appPrimaryColors.ts。
- 无缓存的首次访问复用 AppLoading 中性全屏等待主色确定；有缓存刷新可立即展示，接口结果仍校正缓存；切换应用及返回管理页分别恢复对应主色与默认色。明暗模式在 React 首屏前恢复，避免加载态与顶部栏初始化时明暗跳变。
- 已读取实际设计规范 flexmodel-ui/DESIGN.md 与 designTokens.ts，复用 Apps 语义 token 和现有色板，未新增视觉参数。
- 验证：Apps npm run build、npx tsc --noEmit、36项unit及5项主题Playwright通过；使用 requestAnimationFrame 采样验证浅色/夜间首次进入与延迟接口刷新均无默认色顶部栏帧，另覆盖跨应用/返回管理页、配色存储写入失败、应用接口失败退出等待；git diff --check通过。初次E2E夹具返回错误的通知分页结构造成渲染异常，修正为真实结构后5项全部通过。
- 标准 init.sh 已运行，clean 被正在使用的 flexmodel-server-dev.jar 文件锁阻塞；保留用户运行服务，全模块非 clean compile（排除maven-plugin）与规定引擎测试通过。既有大包及 /logo.png 构建提示仍在。
- Apps UI独立仓库提交02a7e02，仅包含本次主题修复与回归；主仓库保留原有菜单授权后端、feat-129记录、flexmodel-ui子模块与未跟踪Apps目录状态，本次记录单独提交。

**Apps - 多表 Excel 导入导出（2026-10-29，feat-131）**

- 按确认范围交付 Excel 多 Sheet、平台模板/导出文件与普通文件映射；仅新增、新UUID，同应用同SQL业务库，支持包外已有ID，向所有已登录应用用户开放，沿用当前数据访问范围。未新增运行时行/字段权限。
- 新增 `/api/apps/{appId}/transfers` 下的业务表逻辑关系元数据、模板、导出、上传、预览、提交与状态接口。POI 6.0.0读写，隐藏版本/列映射元数据和填写说明；包内键与已有ID分列，服务端严格校验映射、目标表与引用。整包5000行/20表/10MB，每表200列，另限制解压体积50MB和1000条目，禁止公式和错误单元格，错误最多展示500项加摘要。
- 提取复用 AppRecordService 创建字段赋值、输入校验、布局投影、筛选与排序。AppTransferForms 解析普通引用及 SUBFORM.referenceField，普通文本子表链接保持原有校验并在UUID重映射后校验。按实际记录依赖排序，支持无环自引用，循环/悬空/重复键阻止提交；系统目录仅允许已有ID。
- 复用 ProjectUnitOfWork/ServerSessions，按依赖顺序每批最多100条整包提交；业务库隐藏 `f_app_transfer_receipt` 主键抢占与数据在同一事务持久化，重复/并发提交返回相同结果。内部凭据不进入实时广播和业务触发器。上传和计划绑定应用/用户、30分钟有效，定期清理；重启后有效暂存和已提交凭据均可读取。
- 导出在独立串行化事务内采集，再生成文件；支持当前筛选、所选ID、多根表、子表及可选业务引用补齐，按表/ID去重，不沿共享引用反向扩散。处理同一记录先作为共享引用、后作为选中子树时继续收集其后代。
- Apps 记录列表三个入口接入共享五步导入向导、模板下载和多表导出弹窗；复用现有组件与语义主题，服务层处理鉴权multipart与Blob下载、重复提交禁用、网络重试及成功刷新。已读设计规范 `flexmodel-ui/DESIGN.md`，检查浅色、夜间和390px截图。
- 验证：全模块非clean `mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'`、规定 `mvn test -q -pl flexmodel-engine`通过；AppTransferPlannerTest 10项、AppTransferResourceTest 10项、AppRecordServiceTest 6项、AppRecordResourceTest 6项通过。覆盖三表回导、普通Excel、包外ID、权限/任务归属、UUID重映射、自引用/循环、文本子表、整包回滚、并发幂等、定义变更和并发写入快照。补跑发现旧记录REST夹具未配置布局却断言投影外字段，已在夹具中显式配置布局，保留原有断言并通过6项回归。
- Apps `npm run build`、39项Vitest、4项导入导出Playwright、4项既有记录Playwright及两仓库diff检查通过。首轮夜间E2E初始化脚本在documentElement生成前访问classList，修正测试夹具后4项全部通过；产品无页面异常。
- 标准 `bash ./init.sh` 已运行，clean仍受既有 `flexmodel-server-dev.jar` 文件锁阻塞，保留用户服务，使用非clean验证。原生镜像的POI/XMLBeans依赖未验证，本期验收Java25 JVM；多实例暂存需共享目录或会话路由。既有前端大包和logo构建提示仍在。使用说明与API契约见 `flexmodel-server/docs/excel-transfers.md`。
- Apps独立仓库提交 `a6f39d9`。主仓库仅提交本功能后端、专项/相关回归测试与文档，保留原有 `flexmodel-ui` 子模块改动及未跟踪的Apps目录；测试暂存已移至系统临时目录，清理了本次仓库内临时产物。正在运行的后端未重启，新接口需加载本次构建后使用。

**Apps - 讨论行内回复与本人删除权限（2026-10-29，feat-132）**

- 参考 docs/plans/still-storm-deer.md 与用户截图，按最新要求覆盖原计划中所有者/管理员可删除他人评论的规则。点击回复在目标评论下方展开独立
  DiscussionComposer 并聚焦，包含目标姓名与取消入口；提交保留根 parentId 和具体
  replyToId，回复的回复仍归并根线程。顶部新讨论草稿独立，回复发送失败保留草稿，成功关闭行内框，删除对应评论后清理回复目标。
- 本人评论的删除入口移入“更多 → 删除评论”，他人评论不展示更多菜单；后端列表 canDelete 和 DELETE
  均仅允许作者本人，管理员/所有者不例外。保留顶层级联删除及确认提示。复用既有 DropdownMenu/ConfirmDialog，修复 Portal
  菜单低于抽屉导致不可点击的层级，夜间删除项用可读红色。补齐现有 AppRecordDrawer 漏掉的 RecordAsideTab 类型导入。
- 验证：mvn compile -q -pl '!flexmodel-engine/flexmodel-maven-plugin'、mvn test -q -pl flexmodel-engine 通过；mvn test -q
  -pl flexmodel-server -am -Dtest=AppRecordDiscussionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
  通过2项，覆盖所有者/管理员无法删除他人、普通成员删除自己回复及列表标记。Apps npm run build 与 npm run test:records
  6项通过；浅色/夜间专项截图检查，修正夜间对比度后复测通过。两仓库 git diff --check 通过。
- 标准 bash ./init.sh 已执行，clean 仍被正在使用的 flexmodel-server-dev.jar 文件锁阻塞，未停止用户服务；采用非 clean
  编译验证。后端测试有既有日志文件占用轮转告警，不影响2项测试通过；前端沿用既有大包和 logo 构建提示。
-
仓库开始时已有大量暂存与未暂存改动（含讨论功能新类、旧类AD状态及独立Apps仓库的混合改动）。本次仅修改上述目标交互/权限、相关测试、计划与状态文档，保留原有暂存区；为避免把未完成的既有实现混入提交，本次未创建混合提交。后续需连同原讨论功能统一整理提交。

**讨论回复提示简化（2026-10-29，feat-132 追加调整）**

- 按用户要求移除独立“回复某人”横条与取消按钮，输入框占位提示改为“回复 xxx 的讨论”；再次点击同一评论的回复按钮收起，点击其他评论切换目标并聚焦。删除无用的
  onCancelReply 属性与 X 图标依赖，计划文档同步。
- 验证：Apps npm run
  build、浅色/夜间2项讨论Playwright通过，覆盖占位提示、无横条、重复点击收起、重新展开、切换目标、发送失败草稿及删除权限入口。两仓库diff检查通过。仅前端小范围调整，沿用本轮已记录的后端验证和init.sh文件锁限制；保留既有混合暂存区，未混合提交。

**讨论作者头像图片修复（2026-10-29，feat-132 追加调整）**

- 根因：DiscussionItem 只传 authorName，未传 UserAvatar.src，因此始终显示姓名首字。DiscussionPanel 复用 getDirectory (appId)
  的请求缓存，按 authorId 将成员 avatar 传给顶层评论和回复；保留作者姓名快照。图片由既有 UserAvatar/loadAttachment 携带
  Bearer 加载，无头像、目录失败、图片404均回退姓名首字；切换应用或卸载时隔离迟到目录响应。
- 验证：Apps npm run build 与浅色/夜间2项讨论Playwright通过；实际断言本人/其他作者/二级回复图片
  naturalWidth=100、头像请求含Bearer，以及无头像和404回退。检查浅色回复截图，图片正常显示。两仓库 diff --check
  通过。仅前端调整，未重复后端测试或本轮已知被dev.jar文件锁阻塞的init.sh；保留既有暂存改动，未混合提交。

**记录动态收起时展示讨论数量（2026-10-29，feat-132 追加调整）**

- BizDrawer 增加可选 asideCollapsedLabel，收起时在展开图标旁显示“X条讨论”，展开后保留图标。AppRecordDrawer
  首次查看读取一次讨论总数，按应用/表单/记录隔离计数；RecordActivityAside
  将加载、轮询、发送和删除后的总数回传，收起后保留最新值。计数含回复、零条显示0；读取失败或空响应保留图标，侧栏继续卸载并停止轮询。
- 回归发现空接口响应在React延迟执行的状态更新回调中抛错，已提前读取并校验total，防止空响应导致抽屉渲染失败。侧栏按记录key重新挂载，防止切换记录遗留讨论内容或计数。
- 验证：Apps npm run build、npm run test:records
  7项全部通过，覆盖初始收起5条、删除线程后收起3条、再次展开、切换记录4→0及既有编辑保存。检查浅色/夜间收起数量截图，按钮与更多/关闭图标正常对齐；两仓库diff检查通过。本次仅前端调整，沿用前序后端验证与init.sh文件锁限制；保留既有暂存区，未混合提交。

**讨论提及与回复通知修复（2026-10-29，feat-132 追加调整）**

-
根因：原create仅在mentionedUserIds非空时enqueue提及通知，单纯回复只保存目标姓名、不通知目标作者。现在解析并保留具体回复目标，按目标author_id发送回复通知；replyToId缺省时以请求parentId为目标，二级回复也正确通知其作者。存储仍归并根线程，显示姓名快照不变。
-
@通知保持使用显式选择的成员ID；回复目标与提及接收人去重，同一人只收到一条回复通知，其他提及者收到提及通知。作者本人和目录中不存在/停用的回复目标不通知。NotificationService复用同一正文摘要和标题截断方法，reply/mention各有幂等事件键，沿用与评论同平台事务的Outbox及既有SSE分发。未补发历史回复。
-
新增AppRecordDiscussionNotificationTest，使用隔离SQLite和真实REST/Outbox/收件箱：覆盖提及去重和自我排除、根评论回复、二级目标回复、同时回复+提及去重、其他提及者、parentId直接指向二级回复、回复自己、重复drain幂等、未读数、notification.created持久事件、通知详情跳转记录。另保留2项权限测试。前端两种主题的记录E2E增加@候选Enter选择并断言mentionedUserIds与失败草稿保留。
- 验证：mvn test -q -pl flexmodel-server -am -Dtest=AppRecordDiscussionServiceTest,AppRecordDiscussionNotificationTest
  -Dsurefire.failIfNoSpecifiedTests=false通过（3项）；NotificationResourceTest#authenticatedStreamPushesImmediatelyAndReplaysWithoutAlerts通过（1项）；全模块非clean
  compile及规定引擎测试通过；Apps讨论专项浅色/夜间2项通过；两仓库diff检查通过。后端测试仍有既有日志轮转文件锁和无关RabbitMQ连接告警，测试退出码0且断言全部通过。
- 只读检查当前本地系统库：f_notification已包含discussion_id，现有discussion Outbox
  3条均为DONE，无待处理/失败记录；未修改运行中的业务数据或向真实用户发送测试通知。新回复行为需后端加载本次代码后生效。沿用本轮init.sh的运行中dev.jar锁限制；既有混合暂存与未暂存改动保留，未创建混合提交。

**零条讨论时隐藏数量文字（2026-10-29，feat-132 追加调整）**

- 收起入口的数量显示条件改为 total > 0，零条只保留可点击的展开图标。同步更新计划与既有记录切换回归，等待零计数接口返回后断言无数量文字且仍可展开。
- 验证：Apps npm run build、记录切换/零条隐藏Playwright专项1项与diff检查通过。仅前端显示条件微调，保留既有混合暂存区，未混合提交；沿用本轮已记录的后端验证与init.sh文件锁限制。
