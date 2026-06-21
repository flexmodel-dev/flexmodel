// ============================================================
// Worker Executor — Main Process
//
// Creates an isolated Deno Worker for each function invocation.
// Enforces timeout via worker.terminate().
// Proxies SDK RPC requests from Worker → Java API.
// Worker loads function code via file:// URL (native relative import support).
// ============================================================

import type { FunctionMeta, InvokeRequest, InvokeResult } from "../types.ts";
import { registry } from "./registry.ts";
import { handleRpcRequest } from "../sdk/flexmodel.ts";

const JAVA_HOST = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
const JAVA_PORT = parseInt(Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080");

// Worker → projectId 映射，供 SDK 回调时构造 RecordResource URL
const workerProjects = new WeakMap<Worker, string>();

/**
 * Invoke a function by name within a project.
 * - Creates an isolated Worker loading the wrapper via file:// URL
 * - Worker internally imports user code + SDK
 * - Returns the result with execution metadata
 */
export async function invokeFunction(
  projectId: string,
  name: string,
  req: InvokeRequest,
): Promise<InvokeResult> {
  const meta = registry.get(projectId, name);
  if (!meta) {
    throw new Error(`Function not found: ${projectId}:${name}`);
  }

  return executeInWorker(meta, req);
}

/**
 * Execute function in an isolated Worker with timeout enforcement.
 */
async function executeInWorker(
  meta: FunctionMeta,
  req: InvokeRequest,
): Promise<InvokeResult> {
  // Validate that the function directory exists before attempting Worker creation
  try {
    await Deno.stat(meta.functionDir);
  } catch {
    throw new Error(
      `Function directory not found: ${meta.functionDir}. The function may need to be re-deployed.`,
    );
  }

  let worker: Worker;
  try {
    // 1. Create Worker with minimal permissions, loading via file:// URL
    worker = new Worker(meta.entryUrl, {
      type: "module",
      deno: {
        permissions: {
          net: ["localhost"],          // only allow callback to Java API
          read: [meta.functionDir],    // only allow reading function's own directory
          write: false,
          env: false,
          run: false,
          ffi: false,
        },
      },
    });

    // 记录 Worker 对应的 projectId
    workerProjects.set(worker, meta.projectId);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(`Failed to create Worker: ${message}`);
  }

  return new Promise((resolve, reject) => {
    const startTime = performance.now();
    const collectedLogs: Array<{ level: "info" | "warn" | "error"; message: string; data?: unknown }> = [];

    // 2. Timeout enforcement
    const timer = setTimeout(() => {
      worker.terminate();
      reject(new Error(`Function execution timed out after ${meta.timeout}s`));
    }, meta.timeout * 1000);

    // 3. Handle messages from Worker
    worker.onmessage = (e: MessageEvent) => {
      const { type, data } = e.data;

      if (type === "sdk-request") {
        const projectId = workerProjects.get(worker);
        handleRpcRequest(data.operation, data.params, projectId)
          .then((result) => {
            worker.postMessage({ type: "sdk-response", requestId: data.requestId, result });
          })
          .catch((err) => {
            worker.postMessage({ type: "sdk-error", requestId: data.requestId, error: err instanceof Error ? err.message : String(err) });
          });
        return;
      }

      if (type === "log") {
        collectedLogs.push({ level: data.level, message: data.message, data: data.data });
        console.log(`[fn:${meta.name}][${data.level}] ${data.message}`, data.data ?? "");
        return;
      }

      if (type === "result") {
        clearTimeout(timer);
        const executionTimeMs = Math.round(performance.now() - startTime);
        worker.terminate();
        resolve({
          status: data.status,
          headers: data.headers,
          body: data.body,
          _meta: { executionTimeMs, logs: collectedLogs },
        });
        return;
      }

      if (type === "error") {
        clearTimeout(timer);
        worker.terminate();
        reject(new Error(data.message));
        return;
      }
    };

    worker.onerror = (e: ErrorEvent) => {
      clearTimeout(timer);
      worker.terminate();
      reject(new Error(`Worker error: ${e.message}`));
    };

    // 4. Trigger execution — no more sourceCode in the message,
    //    Worker loads user code via import("./index.ts") from the wrapper
    worker.postMessage({
      type: "invoke",
      request: req,
      callbackUrl: `http://${JAVA_HOST}:${JAVA_PORT}`,
    });
  });
}
