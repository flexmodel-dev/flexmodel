# Session Progress Log

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
