# Session Progress Log

## Current State

**Last Updated:** 2026-06-09 23:30 **Session ID:** cloud-functions-implementation **Active Feature:** feat-011 -
Functions - 边缘函数 (Flexmodel Functions)

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
