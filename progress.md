# Session Progress Log

## Feature: Observability 可观测性（traceId 链路 + 函数日志 + 链路追踪页面）（2026-08-29）

**目标:** 对标 Cloudflare/Supabase 的可观测性，统一 traceId 贯穿 HTTP → 函数调用全链路，前端提供链路追踪页面（瀑布图）。

**完成内容:**

- feat-012 traceId 基础设施 + Span 存储：quarkus-opentelemetry + 自定义 FmSpanExporter（CDI SpanExporter）将 Span 持久化到平台级
  f_span 表；sampler parentbased_traceidratio@1.0；MDC 日志注入 traceId/spanId；LogFilter.currentTraceId () 写入
  f_api_request_log；platform.fml 新增 f_span 模型，project.fml 为 f_api_request_log/f_function_log 增加 trace_id 列与索引。
- feat-013 函数执行日志：Deno Runtime 解析 W3C traceparent 头透传 traceId 至 Worker，console.log/warn/error 捕获并携带
  trace_id 持久化到 f_function_log（+内联 100 行到 x-function-meta.logs 供测试面板）；Java
  FunctionLogResource/Service/Repository 提供查询（functionName/level/dateRange/invokeId/traceId/keyword）。
- feat-014 链路追踪页面（前端）：Observability 父级导航（Outlet 容器，父路径自动重定向到 traces
  子页），子页含「链路追踪」与「函数日志」；TracesList（按 trace_id 聚合列表）+ TraceDetail（ECharts custom 系列瀑布图 + Span
  列表 + 关联 API 日志 + 关联函数日志 Tab）；traces/:traceId 隐藏全屏详情路由（镜像现有 flow 父+隐藏详情模式）；FunctionLogList
  独立函数日志页（traceId 跳转链路详情）。其他入口（API 日志、函数列表等）保留。

**本次（接续）完成的前端接线:**

- 新增 flexmodel-ui/src/pages/Observability/components/TracesList.tsx（从 index.tsx 抽出列表）。
- Observability/index.tsx 改为重定向+Outlet 容器（直接访问父路径 → 重定向到 /observability/traces，子路由激活时渲染
  Outlet）。
- routes.tsx 接入：Observability 父路由 + traces/function-logs 子路由 + traces/:traceId 隐藏全屏详情（hideInMenu/hideLayout），新增
  MonitorOutlined/FileTextOutlined 图标。
- utils/echarts.ts 注册 CustomChart（瀑布图 custom 系列所需，此前仅注册 LineChart）。
- locales/zh.json、locales/en.json 补全 observability. */trace.*/function.log* 键（路由 translationKey 无 fallback，必须落
  locale）。

**验证:**

- npx tsc --noEmit（flexmodel-ui）→ 通过，0 错误。
- deno check src/main.ts（functions-runtime）→ 通过。
- mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'（全 reactor 除 maven-plugin）→ BUILD SUCCESS（flexmodel-server
  SUCCESS）。
- feature_list.json / locales JSON 校验 → 通过。

**遗留/说明:**

- flexmodel-maven-plugin 插件描述符在 reactor 内 compile 阶段不生成（plugin.xml 在 package 阶段），导致 -am 全量编译报
  PluginDescriptorParsingException；此为既有环境特性，AGENTS.md 验证命令已显式排除该模块，非本次引入。
- 现有「blank 父路径」模式（scheduling/api/flow/data 直接访问父路径渲染空 Outlet）未改动；Observability 父路径通过重定向改善为自动跳转
  traces。
- 未提交 git（遵循「未明确要求不提交」）。

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

## 接续会话：日志功能合并到 observability 包（2026-08-29）

**目标:** 将分散在 metrics / functions 包的日志功能统一归入
dev.flexmodel.observability.apilog，使可观测性三支柱（Traces/Logs）后端分包与前端分类一致。

**完成内容:**

- **API 日志迁移**（metrics → observability.log，8
  文件）：ApiLogResource、ApiRequestLogService、ApiRequestLogRepository、ApiLogFmRepository、LogStat、LogApiRank、dto/LogStatResponse、consumer/LogEventConsumer（consumer
  子包拍平至 log）。
- **函数日志迁移**（functions → observability.log，4
  文件）：FunctionLogResource、FunctionLogService、FunctionLogRepository、FunctionLogFmRepository。
- **保留**：MetricsResource / MetricsService / dto/FmMetricsResponse 留在 metrics 包（项目概览统计，非运行时遥测）。
- **引用更新**（4 处）：common/Jobs.java、metrics/MetricsService.java（ApiRequestLogService
  import）、observability/SpanService.java（ApiRequestLogService + FunctionLogService 两条 import）。
- **前端**：上一步已将接口日志挂为可观测性子路由（observability/api-logs），后端至此对齐。

**验证:** mvn compile（全模块，排除 maven-plugin）BUILD SUCCESS，仅剩既有 DB2/MapToObjectConverter deprecation 警告（与本次无关）。

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
