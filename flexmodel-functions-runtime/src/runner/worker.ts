// ============================================================
// Worker Executor — Main Process
//
// Creates/reuses isolated Deno Workers for function invocation.
// Workers are pooled and reused across invocations to eliminate
// cold-start latency (module loading, npm resolution, TS compilation).
// Enforces timeout via worker.terminate().
// Worker loads function code via file:// URL (native relative import support).
// SDK runs directly inside Worker — no RPC proxying needed.
// ============================================================

import type {FunctionMeta, InvokeResult} from "../types.ts";
import {registry} from "./registry.ts";
import {workerPool} from "./worker_pool.ts";

/**
 * Invoke a function by name within a project.
 * - Creates an isolated Worker loading the wrapper via file:// URL
 * - Worker internally imports user code + SDK
 * - Returns the result with execution metadata
 */
export async function invokeFunction(
  projectId: string,
  name: string,
  body: unknown,
  authToken?: string,
  invokeId?: string,
  forwardedHeaders?: Record<string, string>,
): Promise<InvokeResult> {
  const meta = registry.get(projectId, name);
  if (!meta) {
    throw new Error(`Function not found: ${projectId}:${name}`);
  }

    return executeInWorker(meta, body, authToken, invokeId, forwardedHeaders);
}

/**
 * Execute function in an isolated Worker with timeout enforcement.
 *
 * Uses WorkerPool to reuse Workers across invocations. On first call
 * for a function, a new Worker is created (cold start). On subsequent
 * calls, an idle Worker is reused — skipping module loading, npm
 * resolution, and TS compilation.
 */
async function executeInWorker(
  meta: FunctionMeta,
  body: unknown,
  authToken?: string,
  invokeId?: string,
  forwardedHeaders?: Record<string, string>,
): Promise<InvokeResult> {
  // Validate that the function directory exists before attempting Worker creation
  try {
    await Deno.stat(meta.functionDir);
  } catch {
    throw new Error(
      `Function directory not found: ${meta.functionDir}. The function may need to be re-deployed.`,
    );
  }

    // 1. Try to acquire an idle Worker from the pool (warm path)
    let worker: Worker = workerPool.acquire(meta) as Worker;
    let fromPool = worker !== null;

    if (!worker) {
        // 2. Cold path: create a new Worker via the pool's factory
        try {
            worker = workerPool.createWorker(meta);
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : String(err);
            throw new Error(`Failed to create Worker: ${message}`);
        }
  }

  return new Promise((resolve, reject) => {
    const startTime = performance.now();

      // 3. Timeout enforcement
    const timer = setTimeout(() => {
        worker!.terminate();
      reject(new Error(`Function execution timed out after ${meta.timeout}s`));
    }, meta.timeout * 1000);

      // 4. Handle messages from Worker
      worker!.onmessage = (e: MessageEvent) => {
      const { type, data } = e.data;

      if (type === "result") {
        clearTimeout(timer);
        const executionTimeMs = Math.round(performance.now() - startTime);
          // Return Worker to pool on success — avoid cold start next time
          workerPool.release(meta, worker!);
        resolve({
          status: data.status,
          headers: data.headers,
          body: data.body,
          _meta: {executionTimeMs, invokeId},
        });
        return;
      }

      if (type === "error") {
        clearTimeout(timer);
          // Don't return errored Workers to pool — they may be in a bad state
          worker!.terminate();
        reject(new Error(data.message));
        return;
      }
    };

      worker!.onerror = (e: ErrorEvent) => {
      clearTimeout(timer);
          // Worker-level errors: terminate, don't pool
          worker!.terminate();
      reject(new Error(`Worker error: ${e.message}`));
    };

      // 5. Trigger execution — pass body + metadata so the wrapper can
    //    build a standard Request object and inject authToken into SDK singleton.
      worker!.postMessage({
      type: "invoke",
      body,
      authToken,
      projectId: meta.projectId,
      invokeId,
      functionName: meta.name,
          forwardedHeaders,
    });
  });
}
