// ============================================================
// Types — Shared type definitions for the functions runtime
// ============================================================

// ---- Deploy Request (Java → Deno) ----

export interface DeployRequest {
  projectId: string;
  functionId: string;
  name: string;
  sourceFiles: Record<string, string>;
  timeout?: number;
}

// ---- Function Metadata ----

export interface FunctionMeta {
  id: string;
  projectId: string;
  name: string;
  timeout: number;
  functionDir: string;
  entryUrl: string;
}

// ---- Invoke Result (from Deno → Java) ----

export interface InvokeResult {
  status: number;
  headers: Record<string, string>;
  body: unknown;
  _meta: {
    executionTimeMs: number;
    traceId?: string;
    logs?: Array<{ level: string; message: string }>;
  };
}

// ---- Worker Messages ----

export type WorkerOutMessage =
  | {
  type: "result";
  data: {
    status: number;
    headers: Record<string, string>;
    body: unknown;
    logs?: Array<{ level: string; message: string }>
  }
}
  | { type: "error"; data: { message: string } };

export type WorkerInMessage =
    | {
  type: "invoke";
  body: unknown;
  authToken?: string;
  projectId: string;
  traceId?: string;
  functionName?: string;
  forwardedHeaders?: Record<string, string>
};
