# Flexmodel Functions — 云函数功能设计方案

## 一、背景与目标

对标 Supabase Edge Functions，实现 Flexmodel 的云函数能力。用户可编写 JavaScript/TypeScript 函数，部署后在服务端执行。函数可访问 Flexmodel 数据 API，实现自定义业务逻辑、Webhook、定时任务等场景。

## 二、架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                    flexmodel-server (Java/Quarkus)           │
│  ┌──────────────────┐  ┌──────────────────┐                  │
│  │ FunctionResource  │  │ FunctionInvoker   │                  │
│  │ (CRUD REST API)   │  │ (HTTP Client)     │                  │
│  └──────┬───────────┘  └───────┬──────────┘                  │
│         │                       │                              │
│  ┌──────▼───────────┐           │                              │
│  │ FunctionService   │ ◄─── DB (f_function / f_function_version / f_trigger)│
│  └──────────────────┘           │                              │
│                                 │  HTTP (localhost:9999)       │
└─────────────────────────────────┼──────────────────────────────┘
                                  │
┌─────────────────────────────────┼──────────────────────────────┐
│              flexmodel-sidecar (Deno + Hono.js)               │
│  ┌──────────────────────────────▼────────────────────────┐     │
│  │                  Hono.js HTTP Server                    │     │
│  │  POST /functions/deploy       (部署函数代码)           │     │
│  │  DELETE /functions/:id/:name  (删除函数)               │     │
│  │  POST /functions/:id/:name/invoke (调用函数)           │     │
│  │  GET /health                  (健康检查)               │     │
│  └──────────────┬────────────────────────────────────────┘     │
│                 │                                                │
│  ┌──────────────▼────────────────────────────────────────┐     │
│  │             Function Registry (内存缓存)               │     │
│  │  Map<projectId:functionName, FunctionMeta>             │     │
│  └──────────────┬────────────────────────────────────────┘     │
│                 │                                                │
│  ┌──────────────▼────────────────────────────────────────┐     │
│  │             Worker (隔离执行)                           │     │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐                  │     │
│  │  │Worker A │ │Worker B │ │Worker C │  ...              │     │
│  │  └─────────┘ └─────────┘ └─────────┘                  │     │
│  │  - V1: 每次调用创建独立 Worker，执行完销毁              │     │
│  │  - V2: 引入 Worker Pool (Borrow/Return)，减少创建开销  │     │
│  │  - Worker 权限最小化 (仅 net:localhost)                 │     │
│  │  - worker.terminate() 实现真正超时终止                  │     │
│  │  - Worker 崩溃不影响主进程和其他函数                    │     │
│  └───────────────────────────────────────────────────────┘     │
│                                                                  │
│  ┌───────────────────────────────────────────────────────┐     │
│  │             Flexmodel SDK (Worker 内注入)              │     │
│  │  - 通过 postMessage 代理数据请求                       │     │
│  │  - 支持单条 + 批量操作                                  │     │
│  │  - ctx.log 结构化日志                                   │     │
│  └───────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────┘
```

**核心原则：Deno 主进程负责路由和元数据缓存，用户函数在独立 Worker 中执行，Worker 崩溃不影响主进程。Registry 只存元数据，源码首次调用时 lazy load。**

## 三、模块结构

```
flexmodel-sidecar/                  # 新模块（Deno 项目）
├── deno.json                       # Deno 配置
├── src/
│   ├── main.ts                     # Hono.js 入口, 启动 HTTP Server
│   ├── server.ts                   # 服务初始化, 路由注册
│   ├── router/
│   │   ├── functions.ts            # /functions 路由 (deploy, delete, invoke)
│   │   └── health.ts               # /health 路由
│   ├── runner/
│   │   ├── worker.ts               # Worker 执行器 (创建 Worker + 超时 + terminate)
│   │   ├── worker-entry.ts         # Worker 内部入口 (addEventListener 统一消息处理)
│   │   └── registry.ts             # 函数注册表 (元数据缓存 + lazy load 源码)
│   ├── sdk/
│   │   └── flexmodel.ts            # RPC Dispatcher (统一 operation 分发，供 Worker 内调用)
│   └── types.ts                    # TypeScript 类型定义
│
flexmodel-server/                     # Java 侧改动
└── src/main/java/dev/flexmodel/functions/
    ├── FunctionResource.java         # REST 端点
    ├── FunctionService.java          # 函数 CRUD + 状态机 + Deno 通信
    ├── FunctionInvoker.java          # HTTP Client → Deno
    ├── dto/
    │   ├── FunctionCreateRequest.java
    │   ├── FunctionUpdateRequest.java
    │   ├── FunctionInvokeRequest.java
    │   ├── FunctionInvokeResponse.java
    │   └── FunctionDeployRequest.java
    │
flexmodel-server/src/main/resources/
    └── platform.fml                  # 新增 f_function + f_function_version，扩展 f_trigger (TriggerType+HTTP, job_type+FUNCTION)
```

## 四、数据模型

### 4.1 f_function (函数主表)

```
model f_function {
  id : String @id @default(uuid()),
  project_id : String @comment("所属项目ID"),
  name : String @comment("函数名称"),
  slug : String @comment("函数标识符(URL安全)"),
  description? : String @comment("函数描述"),
  entry_point : String @default("default") @comment("入口函数名"),
  status : String @default("CREATING") @comment("部署状态: CREATING, ACTIVE, DEPLOY_FAILED, UPDATING, DELETING, INACTIVE"),
  current_version : Int @default("1") @comment("当前生效版本号"),
  timeout : Int @default("30") @comment("超时时间(秒)"),
  memory_limit : Int @default("128") @comment("内存限制(MB)预留字段，V1 仅配置不强制，V2 监控+超限 terminate"),
  created_by? : String @comment("创建人"),
  updated_by? : String @comment("更新人"),
  created_at? : DateTime @default(now()) @comment("创建时间"),
  updated_at? : DateTime @default(now()) @comment("更新时间"),
  @index(name: "IDX_FUNCTION_PROJECT_SLUG", unique: true, fields: [project_id, slug]),
  @comment("云函数")
}
```

**注意：** HTTP 方法由 trigger 配置决定；触发器复用现有 `f_trigger` 表，不单独建表。

### 4.2 f_function_version (函数版本表)

```
model f_function_version {
  id : String @id @default(uuid()),
  function_id : String @comment("所属函数ID"),
  version : Int @comment("版本号"),
  source_code : String @comment("函数源代码(TypeScript/JavaScript)"),
  created_by? : String @comment("创建人"),
  created_at? : DateTime @default(now()) @comment("创建时间"),
  @index(name: "IDX_FUNCTION_VERSION", unique: true, fields: [function_id, version]),
  @comment("云函数版本")
}
```

**设计说明：**
- `f_function` 只存元数据和当前版本号，不存源代码
- `f_function_version` 存每个版本的源代码，支持版本回滚
- `status` 为部署状态机，完整覆盖部署生命周期

### 4.3 复用现有 f_trigger 表（触发器统一模型）

项目已存在 `f_trigger` 表（用于 EVENT/SCHEDULED 触发 Flow），云函数触发器复用该表，通过扩展枚举和 config JSON 实现统一触发器模型。

**现有表结构（无需修改）：**
```
model f_trigger {
  id : String @id @default(uuid()),
  name : String @comment("描述"),
  description? : String @comment("描述"),
  type : TriggerType @comment("触发器类型"),
  config : JSON @comment("触发器配置"),
  job_type : String @default("FLOW") @comment("任务类型"),
  job_group : String @comment("任务分组"),
  job_id : String @comment("任务ID"),
  state : Boolean @default(true) @comment("状态"),
  project_id? : String @comment("项目ID"),
  ...
}
```

**扩展枚举：**
```
enum TriggerType {
  EVENT,       // 已有: 数据变更事件触发
  SCHEDULED,   // 已有: 定时触发 (Cron/Interval)
  HTTP         // 新增: HTTP 请求触发 (云函数专用)
}
```

**字段复用规则：**

| 字段 | 云函数场景用法 | 示例值 |
|------|----------------|--------|
| `type` | 固定为 `HTTP` | `"HTTP"` |
| `job_type` | 固定为 `FUNCTION`（现有值为 `FLOW`） | `"FUNCTION"` |
| `job_id` | 引用 `f_function.id` | `"uuid-xxx"` |
| `job_group` | 函数 slug（用于日志分组） | `"hello-world"` |
| `config` | JSON，包含 auth_mode、method、path 等 | 见下方 |
| `state` | 触发器是否启用 | `true` |

**config JSON 示例（HTTP 触发器）：**
```json
{
  "auth_mode": "PUBLIC",
  "method": "POST",
  "path": "/functions/hello"
}
```

**auth_mode 说明：**

| auth_mode | 说明 | 典型场景 |
|---|---|---|
| `PUBLIC` | 无鉴权，任何人可调用 | 公开 Webhook |
| `JWT` | 需 JWT Token（前端用户） | 前端 API 调用 |
| `API_KEY` | 需 API Key（服务间调用） | 第三方集成 |
| `INTERNAL` | 仅允许 Java 侧内部调用 | 系统编排 |

**云函数 Cron 触发（V2）：** 复用现有 `type=SCHEDULED`，`job_type=FUNCTION`，config 中配置 cron 表达式：
```json
{ "type": "cron", "cronExpression": "0 0 * * *" }
```

### 4.4 部署状态机

```
创建:  CREATING → ACTIVE (成功) / DEPLOY_FAILED (失败)
更新:  ACTIVE → UPDATING → ACTIVE (成功) / DEPLOY_FAILED (失败)
删除:  ACTIVE → DELETING → (记录删除)
手动:  DEPLOY_FAILED → ACTIVE (重试部署成功)
禁用:  ACTIVE → INACTIVE
启用:  INACTIVE → ACTIVE
```

## 五、Deno 端 API 设计

Deno 服务监听 `http://localhost:9999`（可通过环境变量 `FLEXMODEL_PORT` 配置）。

### 5.1 部署函数（仅元数据，源码 lazy load）
```
POST /functions/deploy
Content-Type: application/json

{
  "projectId": "dev_test",
  "functionId": "uuid-xxx",
  "name": "hello-world",
  "version": 1,
  "entryPoint": "default",
  "timeout": 30,
  "memoryLimit": 128
}

// 注意: sourceCode 不随 deploy 发送，首次调用时由 Sidecar 从 Java 侧 lazy load

Response 200:
{
  "success": true,
  "name": "hello-world"
}
```

### 5.2 调用函数
```
POST /functions/:projectId/:name/invoke
Content-Type: application/json

{
  "method": "POST",
  "headers": { "content-type": "application/json" },
  "body": { "key": "value" },
  "query": { "page": "1" }
}

Response 200:
{
  "status": 200,
  "headers": { "content-type": "application/json" },
  "body": { "hello": "world" },
  "_meta": {
    "executionTimeMs": 42,
    "logs": [
      { "level": "info", "message": "Query completed", "data": { "total": 10 } }
    ]
  }
}

// _meta 字段供 Java 侧提取执行指标和日志，V1 写入 stdout，V2 写入 f_function_log / f_function_metric
```

### 5.3 删除函数
```
DELETE /functions/:projectId/:name

Response 200:
{ "success": true }
```

### 5.4 健康检查
```
GET /health
Response 200:
{ "status": "ok", "uptime": 12345, "workers": 3 }
```

## 六、函数 SDK 设计

函数代码中通过 `ctx` 对象注入 SDK，提供数据操作和日志能力：

```typescript
// 函数示例 — 查询数据
export default async function(req: Request, ctx: Context) {
  // 查询记录
  const records = await ctx.flexmodel.data.find("Student", {
    filter: { age: { _gte: 18 } },
    sort: [{ field: "id", order: "DESC" }],
    page: 1,
    size: 10
  });

  // 批量操作 (减少 HTTP 往返次数)
  const results = await ctx.flexmodel.data.batch([
    { op: "find", model: "Student", params: { filter: { age: { _gte: 18 } } } },
    { op: "create", model: "Log", params: { data: { action: "query", count: 10 } } },
  ]);

  // 创建记录
  await ctx.flexmodel.data.create("Student", { name: "Alice", age: 20 });

  // 单条查询
  const student = await ctx.flexmodel.data.findOne("Student", "record-id");

  // 更新记录
  await ctx.flexmodel.data.update("Student", "record-id", { age: 21 });

  // 删除记录
  await ctx.flexmodel.data.delete("Student", "record-id");

  // 结构化日志
  ctx.log.info("Query completed", { total: records.total });
  ctx.log.error("Something went wrong", { detail: "..." });

  return ctx.json({ total: records.total, list: records.list });
}
```

**SDK 注入机制：** Worker 启动时，在 `worker-entry.ts` 中构造 `ctx` 对象并通过 `postMessage` 传递给 Worker。SDK 内部通过 HTTP 回调 Java 服务的 REST API。`ctx` 同时包含 `log` 方法，日志通过 `postMessage` 回传主进程统一输出。

**Context 类型定义：**

```typescript
interface Context {
  flexmodel: {
    data: {
      find(model: string, params?: any): Promise<{ list: any[], total: number }>;
      findOne(model: string, id: string): Promise<any>;
      create(model: string, data: any): Promise<any>;
      update(model: string, id: string, data: any): Promise<any>;
      delete(model: string, id: string): Promise<void>;
      batch(operations: BatchOp[]): Promise<any[]>;
      // V2: exists(model, id), count(model, filter), upsert(model, id, data)
    };
    // V2: invoke(slug, payload) — 函数间调用
  };
  log: {
    info(message: string, data?: any): void;
    warn(message: string, data?: any): void;
    error(message: string, data?: any): void;
  };
  json(data: any, status?: number): Response;
  text(data: string, status?: number): Response;
  env: Record<string, string>;  // 环境变量（由 Java 侧配置，非系统环境变量）
  // V2: secrets.get(key) — 加密密钥访问
  // V2: user — AUTHENTICATED 触发时自动注入的用户身份
}
```

**SDK 错误格式（V1 基础版）：**

```typescript
// V1: SDK 请求失败时抛出错误，message 包含基本描述
// 函数内可通过 try-catch 捕获
try {
  await ctx.flexmodel.data.findOne("Student", "non-existent");
} catch (err) {
  // err.message = "Record not found"
  ctx.log.warn("Student lookup failed", { error: err.message });
  return ctx.json({ error: "not_found" }, 404);
}
```

**SDK 错误格式（V2 增强）：** 扩展为 `{ code: "NOT_FOUND", model: "Student", detail: "..." }`，支持按 error code 精确处理。

## 七、Worker 隔离执行（核心安全机制）

### 7.1 为什么不用 AbortController

`AbortController.abort()` 无法中断 JavaScript 执行。例如 `while(true){}` 会直接卡死主进程。JS 没有 `Thread.interrupt()` 机制。

### 7.2 Worker 方案

每次函数调用创建一个独立 Worker，执行完毕后销毁。Worker 崩溃或被 terminate 不影响主进程和其他函数。

**Registry 设计：**
- Registry 只缓存元数据 `{ id, version, entryPoint, timeout }` ，不缓存 sourceCode
- deploy 时只存元数据，内存占用 O(N) 与函数数量线性，与源码大小无关
- 首次调用时 lazy load sourceCode：DB 查 f_function_version → 缓存到 LRU Cache（上限 50MB）
- 重启恢复：只加载元数据，启动时间 O(1)，而不是 O(N) 全量 deploy

```typescript
// registry.ts — 函数注册表 (元数据缓存 + lazy load)
interface FunctionMeta {
  id: string;
  projectId: string;
  name: string;
  version: number;
  entryPoint: string;
  timeout: number;
  memoryLimit: number;
  sourceCode?: string;  // lazy loaded, 不在 deploy 时填充
}

class Registry {
  private meta = new Map<string, FunctionMeta>();         // key: projectId:name
  private sourceCache = new LRUCache<string, string>(50 * 1024 * 1024);  // 50MB LRU

  deploy(meta: Omit<FunctionMeta, "sourceCode">) {
    this.meta.set(`${meta.projectId}:${meta.name}`, meta);
  }

  async getSourceCode(meta: FunctionMeta): Promise<string> {
    if (meta.sourceCode) return meta.sourceCode;
    const cacheKey = `${meta.id}:v${meta.version}`;
    let code = this.sourceCache.get(cacheKey);
    if (!code) {
      // lazy load: 从 Java 侧获取源码
      code = await fetch(`http://localhost:${JAVA_PORT}/internal/functions/${meta.id}/versions/${meta.version}/source`).then(r => r.text());
      this.sourceCache.set(cacheKey, code);
    }
    meta.sourceCode = code;
    return code;
  }
}
```

```typescript
// worker.ts — Worker 执行器
import { Registry } from "./registry.ts";

interface InvokeResult {
  status: number;
  headers: Record<string, string>;
  body: any;
  _meta: {
    executionTimeMs: number;
    logs: Array<{ level: string; message: string; data?: any }>;
  };
}

async function invokeFunction(
  functionMeta: FunctionMeta,
  req: InvokeRequest
): Promise<InvokeResult> {
  return new Promise((resolve, reject) => {
    const startTime = performance.now();
    const collectedLogs: Array<{ level: string; message: string; data?: any }> = [];
    // 1. 创建 Worker，权限最小化
    const worker = new Worker(new URL("./worker-entry.ts", import.meta.url).href, {
      type: "module",
      deno: {
        permissions: {
          net: ["localhost"],     // 仅允许回调 Java API
          read: false,
          write: false,
          env: false,
          run: false,
          ffi: false,
          hrtime: false,
        },
      },
    });

    // 2. 超时强制终止
    const timer = setTimeout(() => {
      worker.terminate();
      reject(new Error(`Function execution timed out after ${functionMeta.timeout}s`));
    }, functionMeta.timeout * 1000);

    // 3. 监听 Worker 消息
    worker.onmessage = (e: MessageEvent) => {
      clearTimeout(timer);
      const { type, data } = e.data;

      if (type === "sdk-request") {
        // 代理 SDK RPC 请求：Worker → 主进程 → Java API → 主进程 → Worker
        // operation 格式: "data.find", "data.create", "auth.getUser" 等
        handleRpcRequest(data.operation, data.params).then((result) => {
          worker.postMessage({ type: "sdk-response", requestId: data.requestId, result });
        }).catch((err) => {
          worker.postMessage({ type: "sdk-error", requestId: data.requestId, error: err.message });
        });
        return;
      }

      if (type === "log") {
        // 代理日志输出 + 收集到 _meta
        const entry = { level: data.level, message: data.message, data: data.data };
        collectedLogs.push(entry);
        console.log(`[fn:${functionMeta.name}][${data.level}] ${data.message}`, data.data ?? "");
        return;
      }

      if (type === "result") {
        const executionTimeMs = Math.round(performance.now() - startTime);
        worker.terminate();
        resolve({
          ...data,
          _meta: { executionTimeMs, logs: collectedLogs },
        });
        return;
      }

      if (type === "error") {
        worker.terminate();
        reject(new Error(data.message));
      }
    };

    worker.onerror = (e: ErrorEvent) => {
      clearTimeout(timer);
      worker.terminate();
      reject(new Error(`Worker error: ${e.message}`));
    };

    // 4. 启动执行
    worker.postMessage({
      type: "invoke",
      sourceCode: functionMeta.sourceCode,
      entryPoint: functionMeta.entryPoint,
      request: req,
      callbackUrl: `http://localhost:${JAVA_PORT}`,  // Java 回调地址
    });
  });
}
```

```typescript
// worker-entry.ts — Worker 内部入口
/// <reference no-default-lib="true" />
/// <reference lib="deno.worker" />

// SDK RPC 代理：统一通过 sendRpcRequest 分发，主进程根据 operation 路由到对应服务
let requestIdCounter = 0;
const pendingRequests = new Map<string, { resolve: Function; reject: Function }>();

function sendRpcRequest(operation: string, params: any): Promise<any> {
  return new Promise((resolve, reject) => {
    const requestId = `req_${++requestIdCounter}`;
    pendingRequests.set(requestId, { resolve, reject });
    self.postMessage({ type: "sdk-request", data: { requestId, operation, params } });
  });
}

// 日志限制：单次执行最多 100 条，超出截断
const MAX_LOG_ENTRIES = 100;
let logCount = 0;

function sendLog(level: string, message: string, data?: any) {
  if (logCount >= MAX_LOG_ENTRIES) return; // 超出截断
  logCount++;
  self.postMessage({ type: "log", data: { level, message, data } });
}

// 统一消息处理：避免 self.onmessage 被覆盖导致 invoke / sdk-response 漏接
self.addEventListener("message", async (e: MessageEvent) => {
  const { type } = e.data;

  // 处理 SDK 响应：解锁 pendingRequests
  if (type === "sdk-response") {
    const pending = pendingRequests.get(e.data.requestId);
    if (pending) { pendingRequests.delete(e.data.requestId); pending.resolve(e.data.result); }
    return;
  }
  if (type === "sdk-error") {
    const pending = pendingRequests.get(e.data.requestId);
    if (pending) { pendingRequests.delete(e.data.requestId); pending.reject(new Error(e.data.error)); }
    return;
  }

  // 处理 invoke 消息：加载并执行函数
  if (type === "invoke") {
    const { sourceCode, entryPoint, request, callbackUrl } = e.data;
    try {
      const moduleUrl = `data:application/typescript;base64,${btoa(sourceCode)}`;
      const mod = await import(moduleUrl);

      const handler = mod[entryPoint];
      if (typeof handler !== "function") {
        self.postMessage({ type: "error", data: { message: `Entry point "${entryPoint}" is not a function` } });
        return;
      }

      const ctx = buildContext(callbackUrl);
      const req = new Request(request.url || "http://localhost/function", {
        method: request.method || "POST",
        headers: request.headers,
        body: request.body ? JSON.stringify(request.body) : undefined,
      });

      const response = await handler(req, ctx);
      const result = {
        status: response instanceof Response ? response.status : 200,
        headers: response instanceof Response ? Object.fromEntries(response.headers) : {},
        body: response instanceof Response ? await response.json().catch(() => null) : response,
      };
      self.postMessage({ type: "result", data: result });
    } catch (err) {
      self.postMessage({ type: "error", data: { message: err instanceof Error ? err.message : String(err) } });
    }
  }
});

function buildContext(callbackUrl: string) {
  return {
    flexmodel: {
      // RPC Dispatcher：ctx.flexmodel.call(operation, params)
      // 便捷方法封装在 call 之上，主进程按 operation 路由到 Java 对应 Service
      call: (operation: string, params?: any) => sendRpcRequest(operation, params),
      data: {
        find:    (model: string, params?: any)          => sendRpcRequest("data.find",    { model, params }),
        findOne: (model: string, id: string)             => sendRpcRequest("data.findOne", { model, id }),
        create:  (model: string, data: any)              => sendRpcRequest("data.create",  { model, data }),
        update:  (model: string, id: string, data: any)  => sendRpcRequest("data.update",  { model, id, data }),
        delete:  (model: string, id: string)             => sendRpcRequest("data.delete",  { model, id }),
        batch:   (ops: any[])                            => sendRpcRequest("data.batch",   { operations: ops }),
        // V2: exists, count, upsert 仅需新增 operation 名，无需改架构
      },
      // V2: auth / storage / queue / invoke 等新服务均通过 call(operation, params) 扩展
    },
    log: {
      info:  (message: string, data?: any) => sendLog("info",  message, data),
      warn:  (message: string, data?: any) => sendLog("warn",  message, data),
      error: (message: string, data?: any) => sendLog("error", message, data),
    },
    json: (data: any, status = 200)    => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } }),
    text: (data: string, status = 200) => new Response(data, { status, headers: { "content-type": "text/plain" } }),
    env: {},
  };
}
```

### 7.3 安全边界

| 层面 | 措施 |
|------|------|
| 进程隔离 | 每个函数调用在独立 Worker 中执行 |
| 权限最小化 | Worker 仅允许 `net: [localhost]`，禁用文件/环境/运行权限 |
| 超时终止 | `worker.terminate()` 强制终止，可中断死循环 |
| 内存保护 | Worker 崩溃不影响主进程；V1 `memory_limit` 仅配置字段，不强制；V2 监控 Worker 内存占用，超限 terminate |
| 日志保护 | 单次执行最多 100 条日志，超出截断；`_meta.logs` 总体积上限 1MB |
| 错误隔离 | Worker 错误不泄露堆栈信息，仅返回可读错误消息 |
| SDK 代理 | Worker 内不直接持有网络能力，所有 SDK 请求通过 postMessage 代理，主进程按 operation 路由 |
| Registry | 仅缓存元数据，源码通过 LRU Cache 管理，避免内存溢出 |

### 7.4 资源限制

| 限制项 | 默认值 | V1 实现 | V2 演进 |
|--------|--------|------|------|
| 执行超时 | 30s | `worker.terminate()` 强制终止 | - |
| 内存限制 | 128MB | **仅配置字段，不强制**，Deno Worker 无原生内存限制 API | 监控 Worker `performance.memory`，超限 terminate |
| 请求体大小 | 1MB | Java 侧校验 | - |
| 响应体大小 | 5MB | Deno 侧校验 | - |
| 单次日志数 | 100 条 | Worker 内截断，超出丢弃 | 支持配置 |
| `_meta.logs` 大小 | 1MB | 主进程侧校验，超出截断 | - |

## 八、Java 端实现

### 8.1 FunctionService.java

```java
@ApplicationScoped
public class FunctionService {

  @Inject FunctionRepository functionRepository;
  @Inject FunctionVersionRepository versionRepository;
  @Inject TriggerRepository triggerRepository;  // 复用现有 f_trigger 表
  @Inject FunctionInvoker functionInvoker;

  /** 创建函数: 保存DB(CREATING) → 保存版本 → 部署元数据到 Deno → ACTIVE / DEPLOY_FAILED */
  public FunctionResponse create(FunctionCreateRequest req) {
    FunctionEntity entity = functionRepository.save(req.toEntity("CREATING"));
    versionRepository.save(entity.getId(), 1, req.sourceCode());

    try {
      // 仅发送元数据到 Deno，源码首次调用时 lazy load
      functionInvoker.deploy(entity);
      entity.setStatus("ACTIVE");
    } catch (Exception e) {
      entity.setStatus("DEPLOY_FAILED");
      log.error("Deploy failed for function: " + entity.getSlug(), e);
    }
    functionRepository.update(entity);
    return FunctionResponse.from(entity);
  }

  /** 更新函数: 版本号+1，重新部署元数据 (Sidecar LRU 缓存会失效旧版本) */
  public FunctionResponse update(String slug, FunctionUpdateRequest req) {
    FunctionEntity entity = functionRepository.findBySlug(slug);
    entity.setStatus("UPDATING");
    functionRepository.update(entity);

    int newVersion = entity.getCurrentVersion() + 1;
    versionRepository.save(entity.getId(), newVersion, req.sourceCode());

    try {
      functionInvoker.deploy(entity, newVersion);
      entity.setCurrentVersion(newVersion);
      entity.setStatus("ACTIVE");
    } catch (Exception e) {
      entity.setStatus("DEPLOY_FAILED");
      log.error("Deploy failed for function: " + slug, e);
    }
    functionRepository.update(entity);
    return FunctionResponse.from(entity);
  }

  /** 回滚到指定版本 */
  public FunctionResponse rollback(String slug, int targetVersion) {
    FunctionEntity entity = functionRepository.findBySlug(slug);

    entity.setStatus("UPDATING");
    functionRepository.update(entity);

    try {
      functionInvoker.deploy(entity, targetVersion);
      entity.setCurrentVersion(targetVersion);
      entity.setStatus("ACTIVE");
    } catch (Exception e) {
      entity.setStatus("DEPLOY_FAILED");
    }
    functionRepository.update(entity);
    return FunctionResponse.from(entity);
  }

  /** 调用函数: 查找匹配的 trigger → 校验 auth_mode → 转发到 Deno */
  public FunctionInvokeResponse invoke(String projectId, String slug, FunctionInvokeRequest req, String authHeader) {
    FunctionEntity entity = functionRepository.findByProjectAndSlug(projectId, slug);

    // 查找匹配当前请求的 trigger (按 path + method 匹配 HTTP 类型)
    TriggerEntity trigger = triggerRepository
      .findByJobIdAndJobType(entity.getId(), "FUNCTION")  // job_type=FUNCTION 过滤云函数触发器
      .orElseThrow(() -> new NotFoundException("No HTTP trigger configured"));

    // 权限校验
    validateAuthMode(trigger, authHeader);

    FunctionInvokeResponse response = functionInvoker.invoke(projectId, slug, req);

    // V1: 将 _meta 中的日志和耗时输出到 stdout (供日志采集)
    if (response.getMeta() != null) {
      log.info("Function {} executed in {}ms", slug, response.getMeta().getExecutionTimeMs());
      for (LogEntry entry : response.getMeta().getLogs()) {
        log.info("[fn:{}][{}] {}", slug, entry.getLevel(), entry.getMessage());
      }
    }

    // V2: 写入 f_function_log + f_function_metric

    return response;
  }

  /** 内部接口: Sidecar lazy load 源码时调用 */
  @GET @Path("/internal/functions/{functionId}/versions/{version}/source")
  public String getSourceCode(@PathParam String functionId, @PathParam int version) {
    FunctionVersionEntity v = versionRepository.findByFunctionAndVersion(functionId, version);
    return v.getSourceCode();
  }

  /** 启动时同步: 只加载元数据到 Deno Registry，不传源码，启动时间 O(1) */
  void onStart(@Observes StartupEvent event) {
    List<FunctionEntity> functions = functionRepository.findByStatus("ACTIVE");
    for (FunctionEntity fn : functions) {
      try {
        functionInvoker.deploy(fn);  // 仅元数据
      } catch (Exception e) {
        fn.setStatus("DEPLOY_FAILED");
        functionRepository.update(fn);
        log.error("Startup deploy failed: " + fn.getSlug(), e);
      }
    }
    log.info("Startup: deployed {} functions (metadata only, lazy sourceCode)", functions.size());
  }

  private void validateAuthMode(TriggerEntity trigger, String authHeader) {
    // auth_mode 存在 trigger.config JSON 中
    String authMode = trigger.getConfig().getString("auth_mode");
    switch (authMode) {
      case "PUBLIC" -> { /* 无需鉴权 */ }
      case "JWT" -> {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
          throw new ForbiddenException("JWT token required");
        }
        // 校验 JWT
      }
      case "API_KEY" -> {
        if (authHeader == null || authHeader.isBlank()) {
          throw new ForbiddenException("API Key required");
        }
        // 校验 API Key
      }
      case "INTERNAL" -> {
        // 仅允许内部调用
      }
    }
  }
}
```

### 8.2 FunctionInvoker.java

```java
@ApplicationScoped
public class FunctionInvoker {
  private final WebClient client;

  /** 仅发送元数据，源码由 Sidecar 首次调用时 lazy load */
  void deploy(FunctionEntity fn) {
    deploy(fn, fn.getCurrentVersion());
  }

  void deploy(FunctionEntity fn, int version) {
    DeployRequest req = new DeployRequest(
      fn.getProjectId(), fn.getId(), fn.getSlug(),
      version, fn.getEntryPoint(),
      fn.getTimeout(), fn.getMemoryLimit()
    );
    client.post("/functions/deploy").body(req).send();
  }

  FunctionInvokeResponse invoke(String projectId, String name, FunctionInvokeRequest req) {
    return client.post("/functions/" + projectId + "/" + name + "/invoke")
      .body(req).send().bodyAsJson(FunctionInvokeResponse.class);
  }
}
```

### 8.3 FunctionResource.java (REST API)

```
# 函数管理
POST   /projects/{projectId}/functions                              → 创建函数
GET    /projects/{projectId}/functions                              → 函数列表
GET    /projects/{projectId}/functions/{slug}                       → 函数详情
PUT    /projects/{projectId}/functions/{slug}                       → 更新函数
DELETE /projects/{projectId}/functions/{slug}                       → 删除函数
POST   /projects/{projectId}/functions/{slug}/invoke                → 调用函数 (内部)
POST   /projects/{projectId}/functions/{slug}/rollback              → 回滚到指定版本
GET    /projects/{projectId}/functions/{slug}/versions              → 版本列表

# Trigger 管理 (内部操作 f_trigger 表，job_type=FUNCTION, type=HTTP)
GET    /projects/{projectId}/functions/{slug}/triggers              → 触发器列表
POST   /projects/{projectId}/functions/{slug}/triggers              → 添加触发器
PUT    /projects/{projectId}/functions/{slug}/triggers/{triggerId}  → 更新触发器
DELETE /projects/{projectId}/functions/{slug}/triggers/{triggerId}  → 删除触发器

# 公开触发入口 (按 trigger type + auth_mode 鉴权)
POST   /functions/{slug}                                            → HTTP Trigger 入口

# 内部接口 (Sidecar lazy load 源码)
GET    /internal/functions/{functionId}/versions/{version}/source   → 获取源码
```

### 8.4 公开 HTTP Trigger 鉴权模型

| auth_mode | 鉴权方式 | 适用场景 |
|---|---|---|
| `PUBLIC` | 无鉴权，任何人可调用 | 公开 Webhook |
| `JWT` | 需携带 `Authorization: Bearer <token>` | 前端用户调用 |
| `API_KEY` | 需携带 `Authorization: <api-key>` | 服务间集成 |
| `INTERNAL` | 仅允许 Java 侧 `/invoke` API 调用 | 内部编排 |

## 九、生命周期流程

### 函数创建流程
```
User → POST /functions
  → Java: save(CREATING) → save version(v1) → save trigger(HTTP)
  → Java: POST Deno /deploy (仅元数据，不含 sourceCode)
    → Deno: Registry 缓存元数据 {id, version, entryPoint, timeout} → 返回成功
  → Java: update(ACTIVE)
  → 返回成功

失败路径:
  → Deno deploy 失败 → Java: update(DEPLOY_FAILED) → 返回错误详情
```

### 函数调用流程（含 lazy load）
```
Client → POST /functions/{slug}
  → Java: 查找 HTTP trigger → 校验 auth_mode
  → Java: POST Deno /invoke
    → Deno: Registry 查找函数元数据
    → Deno: LRU Cache 查找 sourceCode
      → Cache miss: HTTP GET /internal/functions/{id}/versions/{v}/source → 缓存到 LRU
    → Deno: 创建 Worker (权限最小化)
    → Deno: Worker 内 addEventListener 统一消息处理 + RPC Dispatcher SDK
    → Deno: Worker 执行函数
      → SDK RPC → postMessage → 主进程 → HTTP → Java → DB → 返回
    → Deno: Worker 返回结果 + _meta → terminate Worker
  → Java: 提取 _meta.logs + executionTimeMs → SLF4J 输出
  → 返回响应
```

### 函数删除流程
```
User → DELETE /functions/{slug}
  → Java: update(DELETING)
  → Java: DELETE Deno /:projectId/:name → Deno: 清除 Registry 缓存
  → Java: 删除 f_function + f_function_version 记录
```

### 重启恢复流程（lazy load，启动 O(1)）
```
Java @Observes StartupEvent
  → 查询所有 status=ACTIVE 的函数
  → 逐一 POST Deno /deploy (仅元数据: id, version, entryPoint, timeout)
  → Deno Registry 缓存元数据，不加载源码
  → 部署失败的标记为 DEPLOY_FAILED
  → 源码在首次调用时由 Sidecar lazy load (LRU Cache)
```

## 十、日志方案

### 10.1 运行时日志

日志流转路径：
```
函数内 ctx.log.info(...)
  → Worker postMessage(type: "log")
  → Deno 主进程: console.log + collectedLogs.push()
  → Worker 执行完毕后随 _meta.logs 返回
  → Java 侧 FunctionService.invoke(): 提取 _meta.logs 输出到 SLF4J
  → V1: stdout/stderr（供日志采集）
  → V2: 写入 f_function_log 表
```

V1 策略：
- Deno 侧：每条日志实时 console.log（方便本地调试） + 收集到 collectedLogs
- invoke 响应中附带 `_meta.logs` + `_meta.executionTimeMs`
- Java 侧接收后输出到 SLF4J，生产环境通过 Loki/ELK 采集

### 10.2 执行日志表 (可选，V2 实现)

```
model f_function_log {
  id : String @id @default(uuid()),
  function_id : String @comment("函数ID"),
  request_id : String @comment("请求ID"),
  level : String @comment("日志级别: info, warn, error"),
  message : String @comment("日志消息"),
  data? : String @comment("附加数据(JSON)"),
  execution_time : Int @comment("执行耗时(ms)"),
  status : String @comment("执行状态: SUCCESS, ERROR, TIMEOUT"),
  error? : String @comment("错误信息"),
  created_at : DateTime @default(now()) @comment("创建时间"),
  @index(name: "IDX_FUNCTION_LOG_FUNCTION", fields: [function_id]),
  @comment("函数执行日志")
}
```

**V1 策略：** 日志输出到 stdout/stderr（Deno 侧 + Java 侧），不强制写表。生产环境通过日志采集（如 Loki/ELK）收集。`f_function_log` 表作为 V2 增强预留。

## 十一、实施步骤

### Phase 1: Deno 函数执行引擎
1. 初始化 `flexmodel-sidecar/` 项目 (`deno.json`, `main.ts`)
2. 实现 Hono.js HTTP 服务器 + 路由
3. 实现 Function Registry (内存缓存)
4. 实现 Worker 执行器 (`worker.ts` + `worker-entry.ts`)
5. 实现 Worker 内 SDK 代理 (postMessage 通信)
6. 实现 Flexmodel SDK (fetch 封装，主进程侧代理)
7. 实现健康检查端点
8. 编写 Deno 端测试

### Phase 2: Java 端集成
9. `platform.fml` 新增 `f_function` + `f_function_version` 模型，扩展 `TriggerType` 枚举 (+HTTP) 和 `f_trigger.job_type` (+FUNCTION)
10. 代码生成 → Entity 类
11. 实现 `FunctionInvoker.java` (元数据 deploy，Quarkus REST Client 或 Vert.x WebClient)
12. 实现 `FunctionService.java` (CRUD + 状态机 + 版本管理 + lazy load 源码接口)
13. 实现 `FunctionResource.java` (REST 端点 + auth_mode 权限校验 + Trigger CRUD)
14. 实现 `@Observes StartupEvent` 恢复逻辑 (仅元数据)

### Phase 3: 集成测试与验证
15. 验证端到端: 创建函数 → 调用函数 → 更新函数 → 删除函数
16. 验证函数内 SDK 调用 Flexmodel 数据 API (含批量操作)
17. 验证 Worker 隔离: 死循环函数不影响主进程
18. 验证超时终止: `worker.terminate()` 生效
19. 验证部署状态机: DEPLOY_FAILED → 重试 → ACTIVE
20. 验证重启恢复
21. 验证公开 HTTP Trigger 鉴权

## 十二、验证方案

```bash
# 1. 启动 Deno 服务
cd flexmodel-sidecar
deno run --allow-net=localhost --allow-env src/main.ts

# 2. 验证健康检查
curl http://localhost:9999/health

# 3. 部署测试函数 (仅元数据，源码由 Sidecar lazy load)
curl -X POST http://localhost:9999/functions/deploy ^
  -H "Content-Type: application/json" ^
  -d "{\"projectId\":\"dev_test\",\"functionId\":\"test-1\",\"name\":\"hello\",\"version\":1,\"entryPoint\":\"default\",\"timeout\":30,\"memoryLimit\":128}"

# 4. 调用函数
curl -X POST http://localhost:9999/functions/dev_test/hello/invoke ^
  -H "Content-Type: application/json" ^
  -d "{}"

# 5. 验证死循环函数被 terminate (不影响主进程)
curl -X POST http://localhost:9999/functions/deploy ^
  -H "Content-Type: application/json" ^
  -d "{\"projectId\":\"dev_test\",\"functionId\":\"test-2\",\"name\":\"bad-loop\",\"version\":1,\"entryPoint\":\"default\",\"timeout\":5,\"memoryLimit\":128}"

curl -X POST http://localhost:9999/functions/dev_test/bad-loop/invoke -d "{}"
# 预期: 5秒后返回超时错误，主进程仍可用

# 6. 编译 Java 侧
mvn compile -pl flexmodel-server -am

# 7. 启动完整服务验证端到端
mvn quarkus:dev -pl flexmodel-server
```

## 十三、V2 演进方向（当前不实现）

以下能力作为后续迭代方向，V1 不实现但架构预留扩展空间。按优先级分层排列。

### 13.1 高优先级（生产上线前建议补齐）

| 能力 | 说明 | V1 预留点 |
|------|------|----------|
| **Worker Pool** | V1 每次调用创建/销毁 Worker，性能一般（~100 QPS 时开销明显）。V2 引入 Worker Pool（Borrow/Return），复用 Worker 减少创建开销 | V1 架构已按 Worker 粒度设计，Pool 只需改造 `invokeFunction` 的创建逻辑，不影响其他组件 |
| **函数权限管理** | 函数可声明所需权限（读/写哪些 model、访问哪些外部服务），Java 侧在 deploy 时校验，运行时 SDK 层强制鉴权。类似 Supabase RLS Policy 绑定到函数 | `f_function` 表可增加 `permissions` JSON 字段，RPC Dispatcher 层增加鉴权拦截 |
| **执行指标 (Metrics)** | 记录每次调用的 execution_time、status、error_type；聚合计算调用次数、成功率、P95/P99 耗时；接入 Prometheus / Grafana | `f_function_log` 表已有 execution_time/status 字段，`_meta` 已包含 timing 数据 |
| **函数日志写表** | 每次调用产生的 ctx.log 条目持久化到 `f_function_log`，支持 UI 查看和搜索 | `f_function_log` 表结构已定义，`_meta.logs` 已统一回传 |
| **资源使用监控** | 监控每个 Worker 的实际内存占用、CPU 时间；超限告警或自动 terminate；UI 展示函数资源消耗排行 | Worker 可通过 `performance.memory` 上报内存；主进程维护资源计数器 |

### 13.2 中优先级（提升开发者体验）

| 能力 | 说明 | V1 预留点 |
|------|------|----------|
| **更好的 SDK 错误处理** | SDK RPC 请求失败时返回结构化错误（含 error code、model name、detail）；支持函数内 try-catch 降级；增加 `exists/count/upsert` 便捷方法 | RPC Dispatcher 已抽象，仅需扩展错误格式 `{ code, model, detail }` |
| **定时触发 (Cron)** | 支持 Cron 表达式触发函数执行（每天凌晨同步、定时清理等）；Java 侧增加 Scheduler 组件，定时 POST Deno /invoke | `f_trigger` 已支持 `type=SCHEDULED` + `job_type=FUNCTION`，config 中已预留 `cronExpression` 字段，仅需实现 Scheduler |
| **函数密钥 (Secrets)** | 函数可绑定加密环境变量（第三方 API Key），运行时通过 `ctx.secrets.get("KEY")` 获取 | `ctx.env` 已预留，V2 升级为从加密存储加载 |
| **函数间调用** | 函数 A 可调用函数 B（内部 RPC），支持链式调用和编排模式 | SDK RPC Dispatcher 增加 `functions.invoke` operation 即可 |
| **本地开发模式** | 提供 CLI 工具在本地启动 Sidecar + mock Java API，支持热重载 | Sidecar 独立运行架构已支持，V2 增加 `deno task dev` + file watcher |

### 13.3 低优先级（规模化演进）

| 能力 | 说明 |
|------|------|
| **函数市场 (Templates)** | 预置常用函数模板（Webhook 处理、数据清洗、通知推送等），UI 一键创建 |
| **多实例部署** | Sidecar 水平扩展，Registry 从内存 Map 迁移到 Redis 共享缓存，支持负载均衡 |
| **流式响应 (SSE/Streaming)** | 支持函数返回 `ReadableStream`，用于 AI 对话流式输出、大数据集分页流等场景 |
| **函数依赖管理** | 支持 `import` 外部 npm 包，deploy 时自动解析依赖并缓存（类似 Deno Deploy 或 esm.sh） |
| **多租户隔离** | 不同项目的函数在资源配额、网络策略上完全隔离，防止跨项目影响 |
| **A/B 部署 (Canary)** | 新版本函数先承接部分流量（如 10%），观察指标后逐步切换，配合版本回滚使用 |

### 13.4 V2 补充数据模型（预留参考）

```
f_function 表扩展字段:
  permissions? : String @comment("权限声明(JSON): {models: [{name, ops}]}")
  secrets? : String @comment("加密环境变量(JSON)")

// 注: cron 触发已在 f_trigger.config 中通过 type=SCHEDULED 支持，无需单独加字段

f_function_metric 聚合指标表:
  id : String @id @default(uuid()),
  function_id : String,
  window_start : DateTime,
  window_end : DateTime,
  invoke_count : Int,
  success_count : Int,
  error_count : Int,
  timeout_count : Int,
  avg_duration_ms : Int,
  p95_duration_ms : Int,
  p99_duration_ms : Int,
  @index(name: "IDX_METRIC_FUNCTION_WINDOW", fields: [function_id, window_start])
```

