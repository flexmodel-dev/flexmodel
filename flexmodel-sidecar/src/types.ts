// ============================================================
// Flexmodel Functions — Shared TypeScript Type Definitions
// ============================================================

// ---- Function Metadata (cached in Registry) ----

export interface FunctionMeta {
  id: string;
  projectId: string;
  name: string;
  timeout: number;
  functionDir: string;  // disk directory path
  entryUrl: string;     // file:// URL pointing to _worker_wrapper.ts
}

// ---- Deploy Request (from Java → Deno) ----

export interface DeployRequest {
  projectId: string;
  functionId: string;
  name: string;
  sourceFiles: Record<string, string>;  // filename → content
  timeout: number;
}

// ---- Invoke Request (from Java → Deno) ----

export interface InvokeRequest {
  input?: unknown;
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

// ---- Batch Operation (for SDK data.batch) ----

export interface BatchOp {
  op: string;
  model: string;
  params?: Record<string, unknown>;
}

// ---- Worker Messages ----

export type WorkerOutMessage =
  | { type: "sdk-request"; data: { requestId: string; operation: string; params: unknown } }
  | { type: "log"; data: { level: string; message: string; data?: unknown } }
  | { type: "result"; data: { status: number; headers: Record<string, string>; body: unknown } }
  | { type: "error"; data: { message: string } };

export type WorkerInMessage =
  | { type: "invoke"; request: InvokeRequest; callbackUrl: string }
  | { type: "sdk-response"; requestId: string; result: unknown }
  | { type: "sdk-error"; requestId: string; error: string };
