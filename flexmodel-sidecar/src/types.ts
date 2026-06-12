// ============================================================
// Flexmodel Functions — Shared TypeScript Type Definitions
// ============================================================

// ---- Function Metadata (cached in Registry) ----

export interface FunctionMeta {
  id: string;
  projectId: string;
  name: string;
  version: number;
  entryPoint: string;
  timeout: number;
  memoryLimit: number;
  sourceCode?: string; // lazy loaded, not populated at deploy time
}

// ---- Deploy Request (from Java → Deno) ----

export interface DeployRequest {
  projectId: string;
  functionId: string;
  name: string;
  version: number;
  entryPoint: string;
  timeout: number;
  memoryLimit: number;
}

// ---- Invoke Request (from Java → Deno) ----

export interface InvokeRequest {
  method: string;
  headers: Record<string, string>;
  body?: unknown;
  query?: Record<string, string>;
}

// ---- Invoke Result (from Deno → Java) ----

export interface InvokeResult {
  status: number;
  headers: Record<string, string>;
  body: unknown;
  _meta: {
    executionTimeMs: number;
    logs: LogEntry[];
  };
}

// ---- Log Entry ----

export interface LogEntry {
  level: "info" | "warn" | "error";
  message: string;
  data?: unknown;
}

// ---- Worker Messages ----

export type WorkerOutMessage =
  | { type: "sdk-request"; data: { requestId: string; operation: string; params: unknown } }
  | { type: "log"; data: { level: string; message: string; data?: unknown } }
  | { type: "result"; data: { status: number; headers: Record<string, string>; body: unknown } }
  | { type: "error"; data: { message: string } };

export type WorkerInMessage =
  | { type: "invoke"; sourceCode: string; entryPoint: string; request: InvokeRequest; callbackUrl: string }
  | { type: "sdk-response"; requestId: string; result: unknown }
  | { type: "sdk-error"; requestId: string; error: string };

// ---- Batch Operation (for SDK batch calls) ----

export interface BatchOp {
  op: string;
  model: string;
  params?: unknown;
}

// ---- RPC Operation Names ----
// "data.find", "data.findOne", "data.create", "data.update", "data.delete", "data.batch"
// Future: "data.exists", "data.count", "data.upsert", "storage.upload", "functions.invoke"
