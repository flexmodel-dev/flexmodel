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

// ---- Invoke Result (from Deno → Java) ----

export interface InvokeResult {
  status: number;
  headers: Record<string, string>;
  body: unknown;
  _meta: {
    executionTimeMs: number;
    invokeId?: string;
  };
}

// ---- Worker Messages ----

export type WorkerOutMessage =
  | { type: "result"; data: { status: number; headers: Record<string, string>; body: unknown } }
  | { type: "error"; data: { message: string } };

export type WorkerInMessage =
    | {
  type: "invoke";
  body: unknown;
  authToken?: string;
  projectId: string;
  invokeId?: string;
  functionName?: string;
  forwardedHeaders?: Record<string, string>
};
