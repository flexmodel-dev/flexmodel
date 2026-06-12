# Flexmodel Functions — 云函数 MVP 设计方案

## 一、背景与目标

对标 Supabase Edge Functions，实现 Flexmodel 的云函数能力。用户可编写 TypeScript 函数，部署后在服务端执行。函数可访问 Flexmodel 数据 API，实现自定义业务逻辑、Webhook 等场景。

**MVP 约定：**
- 入口文件固定为 `index.ts`，入口函数固定为 `export default`
- 支持多文件 + 扁平结构（函数内只能建文件，不支持子目录，参考 Supabase）
- 每个函数自动暴露 HTTP 端点 `POST /functions/{slug}`，跟随项目鉴权
- 不管理版本，源码直接存在函数表中
- 不管理触发器，函数即路由

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
│  │ FunctionService   │ ◄─── DB (f_function)                   │
│  └──────────────────┘           │                              │
│                                 │  HTTP (localhost:9999)       │
└─────────────────────────────────┼──────────────────────────────┘
                                  │
┌─────────────────────────────────┼──────────────────────────────┐
│              flexmodel-sidecar (Deno + Hono.js)               │
│  ┌──────────────────────────────▼────────────────────────┐     │
│  │                  Hono.js HTTP Server                    │     │
│  │  POST /functions/deploy       (部署函数)               │     │
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
│  │         Function Files (磁盘目录，支持多文件 import)    │     │
│  │  {FUNCTIONS_DIR}/{functionId}/                         │     │
│  │    ├── index.ts          (用户入口)                     │     │
│  │    ├── utils.ts          (用户辅助文件)                 │     │
│  │    └── _worker_wrapper.ts (自动生成，Worker 消息循环)   │     │
│  │    注: 仅支持扁平文件，不支持子目录                     │     │
│  └──────────────┬────────────────────────────────────────┘     │
│                 │                                                │
│  ┌──────────────▼────────────────────────────────────────┐     │
│  │             Worker (隔离执行)                           │     │
│  │  - 每次调用创建独立 Worker，加载 file:// URL            │     │
│  │  - Deno 原生解析相对 import (./utils, ./db)            │     │
│  │  - Worker 权限最小化 (net:localhost, read:函数目录)     │     │
│  │  - worker.terminate() 实现超时终止                     │     │
│  │  - Worker 崩溃不影响主进程和其他函数                    │     │
│  └───────────────────────────────────────────────────────┘     │
│                                                                  │
│  ┌───────────────────────────────────────────────────────┐     │
│  │             Flexmodel SDK (Worker 内注入)              │     │
│  │  - 通过 postMessage 代理数据请求                       │     │
│  │  - 基础 CRUD 操作                                      │     │
│  │  - ctx.log 日志                                        │     │
│  └───────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────┘
```

**核心原则：Deno 主进程负责路由和元数据缓存，用户函数在独立 Worker 中执行，Worker 崩溃不影响主进程。函数文件写入磁盘目录，通过 `file://` URL 加载，Deno 原生支持同目录文件的相对 import。**

## 三、模块结构

```
flexmodel-sidecar/                  # 新模块（Deno 项目）
├── deno.json                       # Deno 配置
├── src/
│   ├── main.ts                     # Hono.js 入口, 启动 HTTP Server
│   ├── router/
│   │   ├── functions.ts            # /functions 路由 (deploy, delete, invoke)
│   │   └── health.ts               # /health 路由
│   ├── runner/
│   │   ├── worker.ts               # Worker 执行器 (创建 Worker + 超时 + terminate)
│   │   ├── wrapper.ts              # 生成 _worker_wrapper.ts 内容
│   │   └── registry.ts             # 函数注册表 (元数据 + entryUrl)
│   ├── sdk/
│   │   └── flexmodel.ts            # SDK RPC Dispatcher (主进程侧代理)
│   ├── config.ts                   # 常量配置 (FUNCTIONS_DIR, JAVA_PORT 等)
│   └── types.ts                    # TypeScript 类型定义
│
flexmodel-server/                     # Java 侧改动
└── src/main/java/dev/flexmodel/functions/
    ├── FunctionResource.java         # REST 端点
    ├── FunctionService.java          # 函数 CRUD + Deno 通信
    ├── FunctionInvoker.java          # HTTP Client → Deno
    ├── dto/
    │   ├── FunctionCreateRequest.java
    │   ├── FunctionUpdateRequest.java
    │   ├── FunctionInvokeRequest.java
    │   └── FunctionInvokeResponse.java
    │
flexmodel-server/src/main/resources/
    └── platform.fml                  # 新增 f_function
```

## 四、数据模型

### 4.1 f_function (函数表)

```
model f_function {
  id : String @id @default(uuid()),
  project_id : String @comment("所属项目ID"),
  name : String @comment("函数名称"),
  slug : String @comment("函数标识符(URL安全)"),
  description? : String @comment("函数描述"),
  source_files : String @comment("函数源代码(JSON: 文件名→内容)"),
  status : String @default("ACTIVE") @comment("状态: ACTIVE, FAILED, DELETED"),
  timeout : Int @default("30") @comment("超时时间(秒)"),
  created_by? : String @comment("创建人"),
  updated_by? : String @comment("更新人"),
  created_at? : DateTime @default(now()) @comment("创建时间"),
  updated_at? : DateTime @default(now()) @comment("更新时间"),
  @index(name: "IDX_FUNCTION_PROJECT_SLUG", unique: true, fields: [project_id, slug]),
  @comment("云函数")
}
```

**设计说明：**
- 入口文件固定为 `index.ts`，入口函数固定为 `export default`
- `source_files` 为 JSON 字段，存储所有文件内容，格式：`{"index.ts": "...", "utils.ts": "..."}`
- 单文件函数只需包含 `{"index.ts": "..."}`
- 状态只有 3 种：`ACTIVE`（可用）、`FAILED`（部署失败）、`DELETED`（已删除）

**source_files JSON 示例（单文件）：**
```json
{
  "index.ts": "export default async function(req, ctx) { return new Response('hello'); }"
}
```

**source_files JSON 示例（多文件）：**
```json
{
  "index.ts": "import { greet } from './utils.ts';\nexport default async function(req, ctx) { return new Response(greet('world')); }",
  "utils.ts": "export function greet(name: string) { return `Hello, ${name}!`; }",
  "db.ts": "export async function queryStudents(ctx) { return ctx.flexmodel.data.find('Student', {}); }"
}
```

**文件命名规则：**
- 文件名只能包含字母、数字、`-`、`_`、`.`，必须以 `.ts` 或 `.js` 结尾
- 不支持子目录（文件名中不能包含 `/`）
- 入口文件固定为 `index.ts`

### 4.2 状态流转

```
创建:  → ACTIVE (成功) / FAILED (失败)
更新:  ACTIVE → ACTIVE (成功) / FAILED (失败)
删除:  ACTIVE → DELETED
重试:  FAILED → ACTIVE (重新部署成功)
```

## 五、Deno 端 API 设计

Deno 服务监听 `http://localhost:9999`（可通过环境变量 `FLEXMODEL_PORT` 配置）。

### 5.1 部署函数
```
POST /functions/deploy
Content-Type: application/json

{
  "projectId": "dev_test",
  "functionId": "uuid-xxx",
  "name": "hello-world",
  "sourceFiles": {
    "index.ts": "import { greet } from './utils.ts';\nexport default async function(req, ctx) { return new Response(greet('world')); }",
    "utils.ts": "export function greet(name: string) { return `Hello, ${name}!`; }"
  },
  "timeout": 30
}

// Sidecar 处理:
// 1. 将 sourceFiles 写入磁盘 {FUNCTIONS_DIR}/{functionId}/
// 2. 自动生成 _worker_wrapper.ts
// 3. Registry 缓存元数据 + entryUrl

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
  "body": { "hello": "world" }
}
```

### 5.3 删除函数
```
DELETE /functions/:projectId/:name

// Sidecar 处理:
// 1. 清除 Registry 缓存
// 2. 删除磁盘目录 {FUNCTIONS_DIR}/{functionId}/

Response 200:
{ "success": true }
```

### 5.4 健康检查
```
GET /health
Response 200:
{ "status": "ok", "uptime": 12345 }
```

## 六、函数 SDK 设计

函数入口文件固定为 `index.ts`，使用 `export default` 导出处理函数。支持多文件 + 同目录 import：

```typescript
// index.ts — 入口文件
import { greet } from "./utils.ts";
import { queryStudents } from "./db.ts";

export default async function(req: Request, ctx: Context) {
  const students = await queryStudents(ctx);

  ctx.log.info("Query completed", { total: students.length });

  return new Response(JSON.stringify({ message: greet("world"), students }), {
    headers: { "content-type": "application/json" }
  });
}
```

```typescript
// utils.ts — 辅助模块
export function greet(name: string): string {
  return `Hello, ${name}!`;
}
```

```typescript
// db.ts — 数据访问模块
export async function queryStudents(ctx: Context) {
  return await ctx.flexmodel.data.find("Student", {
    filter: { age: { _gte: 18 } },
    page: 1,
    size: 10
  });
}
```

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
    };
  };
  log: {
    info(message: string, data?: any): void;
    warn(message: string, data?: any): void;
    error(message: string, data?: any): void;
  };
}
```

**SDK 注入机制：** deploy 时自动生成 `_worker_wrapper.ts`（包含 Worker 消息循环 + SDK 构建），Worker 通过 `file://` URL 加载该 wrapper，wrapper 内部 `import("./index.ts")` 加载用户模块。Deno 原生解析同目录文件的相对路径 import，用户只需在同一层级建文件即可互相引用。

## 七、Worker 隔离执行（核心安全机制）

### 7.1 为什么不用 AbortController

`AbortController.abort()` 无法中断 JavaScript 执行。例如 `while(true){}` 会直接卡死主进程。JS 没有 `Thread.interrupt()` 机制。

### 7.2 Worker 方案

每次函数调用创建一个独立 Worker，执行完毕后销毁。Worker 崩溃或被 terminate 不影响主进程和其他函数。

**核心改变（相比单文件方案）：**
- 之前：Worker 通过 `postMessage` 接收 `sourceCode`，用 `data:` URL 加载（不支持相对 import）
- 现在：deploy 时写入磁盘，Worker 通过 `file://` URL 加载（Deno 原生支持相对 import）

```typescript
// config.ts — 常量配置
export const FUNCTIONS_DIR = Deno.env.get("FUNCTIONS_DIR") || "/tmp/flexmodel-functions";
export const JAVA_PORT = Deno.env.get("JAVA_PORT") || "8080";
```

```typescript
// registry.ts — 函数注册表
interface FunctionMeta {
  id: string;
  projectId: string;
  name: string;
  timeout: number;
  entryUrl: string;       // file:// URL 指向 _worker_wrapper.ts
  functionDir: string;    // 磁盘目录路径
}

class Registry {
  private functions = new Map<string, FunctionMeta>();  // key: projectId:name

  deploy(meta: FunctionMeta) {
    this.functions.set(`${meta.projectId}:${meta.name}`, meta);
  }

  get(projectId: string, name: string): FunctionMeta | undefined {
    return this.functions.get(`${projectId}:${name}`);
  }

  remove(projectId: string, name: string) {
    this.functions.delete(`${projectId}:${name}`);
  }
}
```

```typescript
// wrapper.ts — 生成 _worker_wrapper.ts 内容
export function generateWrapperCode(): string {
  return `
// 自动生成，请勿手动修改
// Worker 消息循环 + SDK 构建 + 加载用户模块

let requestIdCounter = 0;
const pendingRequests = new Map();

function sendRpcRequest(operation, params) {
  return new Promise((resolve, reject) => {
    const requestId = "req_" + (++requestIdCounter);
    pendingRequests.set(requestId, { resolve, reject });
    self.postMessage({ type: "sdk-request", data: { requestId, operation, params } });
  });
}

self.addEventListener("message", async (e) => {
  const { type } = e.data;

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

  if (type === "invoke") {
    const { request, callbackUrl } = e.data;
    try {
      const mod = await import("./index.ts");
      const handler = mod.default;
      if (typeof handler !== "function") {
        self.postMessage({ type: "error", data: { message: "export default is not a function in index.ts" } });
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

function buildContext(callbackUrl) {
  return {
    flexmodel: {
      data: {
        find:    (model, params)         => sendRpcRequest("data.find",    { model, params }),
        findOne: (model, id)             => sendRpcRequest("data.findOne", { model, id }),
        create:  (model, data)           => sendRpcRequest("data.create",  { model, data }),
        update:  (model, id, data)       => sendRpcRequest("data.update",  { model, id, data }),
        delete:  (model, id)             => sendRpcRequest("data.delete",  { model, id }),
      },
    },
    log: {
      info:  (message, data) => self.postMessage({ type: "log", data: { level: "info",  message, data } }),
      warn:  (message, data) => self.postMessage({ type: "log", data: { level: "warn",  message, data } }),
      error: (message, data) => self.postMessage({ type: "log", data: { level: "error", message, data } }),
    },
  };
}
`.trim();
}
```

```typescript
// worker.ts — Worker 执行器
import { Registry, FunctionMeta } from "./registry.ts";
import { FUNCTIONS_DIR, JAVA_PORT } from "../config.ts";
import { generateWrapperCode } from "./wrapper.ts";

/** Deploy: 将文件写入磁盘 + 生成 wrapper + 注册到 Registry */
async function deployFunction(deployReq: DeployRequest): Promise<void> {
  const functionDir = `${FUNCTIONS_DIR}/${deployReq.functionId}`;

  // 清理旧目录（重新部署时）
  try { await Deno.remove(functionDir, { recursive: true }); } catch { /* 不存在则忽略 */ }

  // 写入所有用户文件（扁平结构，不支持子目录）
  for (const [filename, content] of Object.entries(deployReq.sourceFiles)) {
    if (filename.includes("/")) {
      throw new Error(`Subdirectories are not supported: ${filename}`);
    }
    await Deno.writeTextFile(`${functionDir}/${filename}`, content);
  }

  // 生成 _worker_wrapper.ts
  await Deno.writeTextFile(`${functionDir}/_worker_wrapper.ts`, generateWrapperCode());

  // 注册到 Registry
  const entryUrl = `file://${functionDir}/_worker_wrapper.ts`;
  registry.deploy({
    id: deployReq.functionId,
    projectId: deployReq.projectId,
    name: deployReq.name,
    timeout: deployReq.timeout,
    entryUrl,
    functionDir,
  });
}

/** Delete: 清除 Registry + 删除磁盘目录 */
async function deleteFunction(projectId: string, name: string): Promise<void> {
  const meta = registry.get(projectId, name);
  if (meta) {
    try { await Deno.remove(meta.functionDir, { recursive: true }); } catch { /* 忽略 */ }
    registry.remove(projectId, name);
  }
}

/** Invoke: 创建 Worker 执行函数 */
async function invokeFunction(
  fn: FunctionMeta,
  req: InvokeRequest
): Promise<InvokeResult> {
  return new Promise((resolve, reject) => {
    // 1. 创建 Worker，加载 file:// URL
    const worker = new Worker(fn.entryUrl, {
      type: "module",
      deno: {
        permissions: {
          net: ["localhost"],             // 仅允许回调 Java API
          read: [fn.functionDir],         // 仅允许读函数自身目录
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
      reject(new Error(`Function execution timed out after ${fn.timeout}s`));
    }, fn.timeout * 1000);

    // 3. 监听 Worker 消息
    worker.onmessage = (e: MessageEvent) => {
      const { type, data } = e.data;

      if (type === "sdk-request") {
        handleRpcRequest(data.operation, data.params).then((result) => {
          worker.postMessage({ type: "sdk-response", requestId: data.requestId, result });
        }).catch((err) => {
          worker.postMessage({ type: "sdk-error", requestId: data.requestId, error: err.message });
        });
        return;
      }

      if (type === "log") {
        console.log(`[fn:${fn.name}][${data.level}] ${data.message}`, data.data ?? "");
        return;
      }

      if (type === "result") {
        clearTimeout(timer);
        worker.terminate();
        resolve(data);
        return;
      }

      if (type === "error") {
        clearTimeout(timer);
        worker.terminate();
        reject(new Error(data.message));
      }
    };

    worker.onerror = (e: ErrorEvent) => {
      clearTimeout(timer);
      worker.terminate();
      reject(new Error(`Worker error: ${e.message}`));
    };

    // 4. 触发执行（不再传 sourceCode，Worker 已通过 file:// 加载）
    worker.postMessage({
      type: "invoke",
      request: req,
      callbackUrl: `http://localhost:${JAVA_PORT}`,
    });
  });
}
```

### 7.3 安全边界

| 层面 | 措施 |
|------|------|
| 进程隔离 | 每个函数调用在独立 Worker 中执行 |
| 文件隔离 | Worker 仅允许读自身函数目录 `read: [functionDir]`，不可读其他函数 |
| 网络隔离 | Worker 仅允许 `net: [localhost]`，用于 SDK 回调 Java API |
| 超时终止 | `worker.terminate()` 强制终止，可中断死循环 |
| 错误隔离 | Worker 错误不泄露堆栈信息，仅返回可读错误消息 |
| SDK 代理 | Worker 内不直接持有网络能力，所有 SDK 请求通过 postMessage 代理 |
| 写入保护 | Worker 无 write 权限，`_worker_wrapper.ts` 由主进程写入 |

### 7.4 多文件 import 解析流程

```
Worker 加载 file://{FUNCTIONS_DIR}/{id}/_worker_wrapper.ts
  → wrapper 内 import("./index.ts")
    → Deno 解析为 file://{FUNCTIONS_DIR}/{id}/index.ts
  → index.ts 内 import("./utils.ts")
    → Deno 解析为 file://{FUNCTIONS_DIR}/{id}/utils.ts  ✓
  → index.ts 内 import("./db.ts")
    → Deno 解析为 file://{FUNCTIONS_DIR}/{id}/db.ts  ✓

注意: 仅支持扁平文件（同目录），不支持子目录 import。
```

## 八、Java 端实现

### 8.1 FunctionService.java

```java
@ApplicationScoped
public class FunctionService {

  @Inject FunctionRepository functionRepository;
  @Inject FunctionInvoker functionInvoker;

  /** 创建函数: 保存DB → 部署到 Deno → 返回 */
  public FunctionResponse create(FunctionCreateRequest req) {
    FunctionEntity entity = functionRepository.save(req.toEntity());

    try {
      functionInvoker.deploy(entity);
      entity.setStatus("ACTIVE");
    } catch (Exception e) {
      entity.setStatus("FAILED");
      log.error("Deploy failed for function: " + entity.getSlug(), e);
    }
    functionRepository.update(entity);
    return FunctionResponse.from(entity);
  }

  /** 更新函数: 更新文件，重新部署 */
  public FunctionResponse update(String slug, FunctionUpdateRequest req) {
    FunctionEntity entity = functionRepository.findBySlug(slug);

    if (req.sourceFiles() != null) {
      entity.setSourceFiles(req.sourceFiles());
    }

    try {
      functionInvoker.deploy(entity);
      entity.setStatus("ACTIVE");
    } catch (Exception e) {
      entity.setStatus("FAILED");
      log.error("Deploy failed for function: " + slug, e);
    }
    functionRepository.update(entity);
    return FunctionResponse.from(entity);
  }

  /** 调用函数: 转发到 Deno */
  public FunctionInvokeResponse invoke(String projectId, String slug, FunctionInvokeRequest req) {
    FunctionEntity entity = functionRepository.findByProjectAndSlug(projectId, slug);
    return functionInvoker.invoke(projectId, slug, req);
  }

  /** 删除函数 */
  public void delete(String projectId, String slug) {
    FunctionEntity entity = functionRepository.findByProjectAndSlug(projectId, slug);
    functionInvoker.delete(projectId, slug);
    entity.setStatus("DELETED");
    functionRepository.update(entity);
  }

  /** 启动时同步: 将所有 ACTIVE 函数重新部署到 Deno */
  void onStart(@Observes StartupEvent event) {
    List<FunctionEntity> functions = functionRepository.findByStatus("ACTIVE");
    for (FunctionEntity fn : functions) {
      try {
        functionInvoker.deploy(fn);
      } catch (Exception e) {
        fn.setStatus("FAILED");
        functionRepository.update(fn);
        log.error("Startup deploy failed: " + fn.getSlug(), e);
      }
    }
    log.info("Startup: deployed {} functions", functions.size());
  }
}
```

### 8.2 FunctionInvoker.java

```java
@ApplicationScoped
public class FunctionInvoker {
  private final WebClient client;

  void deploy(FunctionEntity fn) {
    DeployRequest req = new DeployRequest(
      fn.getProjectId(), fn.getId(), fn.getSlug(),
      fn.getSourceFiles(), fn.getTimeout()
    );
    client.post("/functions/deploy").body(req).send();
  }

  FunctionInvokeResponse invoke(String projectId, String name, FunctionInvokeRequest req) {
    return client.post("/functions/" + projectId + "/" + name + "/invoke")
      .body(req).send().bodyAsJson(FunctionInvokeResponse.class);
  }

  void delete(String projectId, String name) {
    client.delete("/functions/" + projectId + "/" + name).send();
  }
}
```

### 8.3 FunctionResource.java (REST API)

```
# 函数管理
POST   /projects/{projectId}/functions                    → 创建函数
GET    /projects/{projectId}/functions                    → 函数列表
GET    /projects/{projectId}/functions/{slug}             → 函数详情
PUT    /projects/{projectId}/functions/{slug}             → 更新函数
DELETE /projects/{projectId}/functions/{slug}             → 删除函数

# 函数调用入口 (跟随项目鉴权)
POST   /projects/{projectId}/functions/{slug}/invoke      → 调用函数
```

## 九、生命周期流程

### 函数创建流程
```
User → POST /projects/{projectId}/functions
  → Java: save DB (source_files JSON)
  → Java: POST Deno /deploy (含 sourceFiles)
    → Deno: 写入磁盘 {FUNCTIONS_DIR}/{id}/
    → Deno: 生成 _worker_wrapper.ts
    → Deno: Registry 缓存 {id, name, entryUrl, timeout} → 返回成功
  → Java: update status = ACTIVE
  → 返回成功

失败路径:
  → Deno deploy 失败 → Java: update status = FAILED → 返回错误详情
```

### 函数调用流程
```
Client → POST /projects/{projectId}/functions/{slug}/invoke
  → Java: 查找函数 → POST Deno /invoke
    → Deno: Registry 查找函数
    → Deno: 创建 Worker，加载 file://{FUNCTIONS_DIR}/{id}/_worker_wrapper.ts
    → Deno: wrapper 内 import("./index.ts") → 用户代码执行
      → 用户 import("./utils.ts") → Deno 原生解析  ✓
      → SDK RPC → postMessage → 主进程 → HTTP → Java → DB → 返回
    → Deno: Worker 返回结果 → terminate Worker
  → Java: 返回响应
```

### 函数删除流程
```
User → DELETE /projects/{projectId}/functions/{slug}
  → Java: DELETE Deno /:projectId/:name
    → Deno: 清除 Registry + 删除磁盘目录
  → Java: update status = DELETED
```

### 重启恢复流程
```
Java @Observes StartupEvent
  → 查询所有 status=ACTIVE 的函数
  → 逐一 POST Deno /deploy (含 sourceFiles)
    → Deno: 写入磁盘 + 生成 wrapper + 注册
  → 部署失败的标记为 FAILED
```

## 十、实施步骤

### Phase 1: Deno 函数执行引擎
1. 初始化 `flexmodel-sidecar/` 项目 (`deno.json`, `main.ts`, `config.ts`)
2. 实现 Hono.js HTTP 服务器 + 路由
3. 实现 Function Registry (内存缓存 entryUrl)
4. 实现文件写入 + wrapper 生成 (`wrapper.ts`)
5. 实现 Worker 执行器 (`worker.ts`，file:// URL 加载)
6. 实现 SDK RPC 代理 (主进程侧 fetch 回调 Java)
7. 实现健康检查端点
8. 编写 Deno 端测试（含多文件 import 场景）

### Phase 2: Java 端集成
9. `platform.fml` 新增 `f_function` 模型（`source_files` JSON 字段）
10. 代码生成 → Entity 类
11. 实现 `FunctionInvoker.java` (Quarkus REST Client 或 Vert.x WebClient)
12. 实现 `FunctionService.java` (CRUD + 状态管理 + 启动恢复)
13. 实现 `FunctionResource.java` (REST 端点)

### Phase 3: 集成测试与验证
14. 验证端到端: 创建函数 → 调用函数 → 更新函数 → 删除函数
15. 验证多文件 import: index.ts 引用同目录下的 utils.ts 和 db.ts
16. 验证函数内 SDK 调用 Flexmodel 数据 API
17. 验证 Worker 隔离: 死循环函数不影响主进程
18. 验证超时终止: `worker.terminate()` 生效
19. 验证重启恢复（磁盘目录重新写入）

## 十一、验证方案

```bash
# 1. 启动 Deno 服务
cd flexmodel-sidecar
deno run --allow-net=localhost --allow-env --allow-read --allow-write src/main.ts

# 2. 验证健康检查
curl http://localhost:9999/health

# 3. 部署单文件函数
curl -X POST http://localhost:9999/functions/deploy \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "dev_test",
    "functionId": "test-1",
    "name": "hello",
    "sourceFiles": {
      "index.ts": "export default async function(req, ctx) { return new Response(JSON.stringify({hello: \"world\"}), {headers:{\"content-type\":\"application/json\"}}); }"
    },
    "timeout": 30
  }'

# 4. 调用函数
curl -X POST http://localhost:9999/functions/dev_test/hello/invoke \
  -H "Content-Type: application/json" \
  -d '{}'

# 5. 部署多文件函数（验证 import）
curl -X POST http://localhost:9999/functions/deploy \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "dev_test",
    "functionId": "test-3",
    "name": "multi-file",
    "sourceFiles": {
      "index.ts": "import { greet } from \"./utils.ts\";\nexport default async function(req, ctx) { return new Response(JSON.stringify({msg: greet(\"Flexmodel\")}), {headers:{\"content-type\":\"application/json\"}}); }",
      "utils.ts": "export function greet(name: string): string { return \"Hello, \" + name + \"!\"; }",
      "db.ts": "export async function getStudents(ctx: any) { return ctx.flexmodel.data.find(\"Student\", {}); }"
    },
    "timeout": 30
  }'

curl -X POST http://localhost:9999/functions/dev_test/multi-file/invoke -d '{}'
# 预期: { "msg": "Hello, Flexmodel!" }

# 6. 验证死循环函数被 terminate (不影响主进程)
curl -X POST http://localhost:9999/functions/deploy \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "dev_test",
    "functionId": "test-2",
    "name": "bad-loop",
    "sourceFiles": {
      "index.ts": "export default async function() { while(true){} }"
    },
    "timeout": 5
  }'

curl -X POST http://localhost:9999/functions/dev_test/bad-loop/invoke -d '{}'
# 预期: 5秒后返回超时错误，主进程仍可用

# 7. 编译 Java 侧
mvn compile -pl flexmodel-server -am

# 8. 启动完整服务验证端到端
mvn quarkus:dev -pl flexmodel-server
```

## 十二、V2 演进方向（当前不实现）

以下能力作为后续迭代方向，MVP 不实现但架构预留扩展空间。

### 12.1 高优先级

| 能力 | 说明 |
|------|------|
| **版本管理** | 新增 `f_function_version` 表，支持版本回滚、A/B 部署 |
| **Worker Pool** | 复用 Worker 减少创建/销毁开销，提升 QPS |
| **触发器模型** | 复用 `f_trigger` 表，支持 HTTP 触发器配置（auth_mode、method、path） |
| **多种鉴权模式** | PUBLIC / JWT / API_KEY / INTERNAL |
| **执行指标 (Metrics)** | 记录调用次数、成功率、P95/P99 耗时，接入 Prometheus |
| **函数日志写表** | `f_function_log` 表持久化，支持 UI 查看和搜索 |
| **资源限制** | 监控 Worker 内存占用，超限 terminate |

### 12.2 中优先级

| 能力 | 说明 |
|------|------|
| **外部依赖 (npm/JSR)** | 支持 `deno.json` import maps，允许函数 import npm 包（如 `npm:lodash`） |
| **SDK batch 批量操作** | 减少 HTTP 往返次数 |
| **更好的 SDK 错误处理** | 结构化错误 `{ code, model, detail }` |
| **定时触发 (Cron)** | 支持 Cron 表达式触发函数执行 |
| **函数密钥 (Secrets)** | 加密环境变量，`ctx.secrets.get("KEY")` |
| **函数间调用** | 函数 A 调用函数 B |
| **本地开发模式** | CLI 工具 + 热重载 |
| **SDK 扩展方法** | `exists` / `count` / `upsert` |

### 12.3 低优先级

| 能力 | 说明 |
|------|------|
| **函数市场 (Templates)** | 预置常用函数模板 |
| **多实例部署** | Sidecar 水平扩展，Registry 迁移到 Redis |
| **流式响应 (SSE)** | 支持函数返回 ReadableStream |
| **多租户隔离** | 资源配额、网络策略隔离 |

