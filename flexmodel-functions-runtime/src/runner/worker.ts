// ============================================================
// Worker Executor — Main Process
//
// Creates an isolated Deno Worker for each function invocation.
// Enforces timeout via worker.terminate().
// Worker loads function code via file:// URL (native relative import support).
// SDK runs directly inside Worker — no RPC proxying needed.
// ============================================================

import type { FunctionMeta, InvokeRequest, InvokeResult } from "../types.ts";
import { registry } from "./registry.ts";

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
          net: ["localhost"],          // SDK fetches Java API on localhost
          read: [meta.functionDir],    // only allow reading function's own directory
          write: false,
          env: ["FLEXMODEL_JAVA_HOST", "FLEXMODEL_JAVA_PORT"],  // SDK reads these for baseURL
          run: false,
          ffi: false,
        },
      },
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(`Failed to create Worker: ${message}`);
  }

  return new Promise((resolve, reject) => {
    const startTime = performance.now();

    // 2. Timeout enforcement
    const timer = setTimeout(() => {
      worker.terminate();
      reject(new Error(`Function execution timed out after ${meta.timeout}s`));
    }, meta.timeout * 1000);

    // 3. Handle messages from Worker
    worker.onmessage = (e: MessageEvent) => {
      const { type, data } = e.data;

      if (type === "result") {
        clearTimeout(timer);
        const executionTimeMs = Math.round(performance.now() - startTime);
        worker.terminate();
        resolve({
          status: data.status,
          headers: data.headers,
          body: data.body,
          _meta: { executionTimeMs },
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

    // 4. Trigger execution — pass authToken + projectId so the wrapper can
    //    inject them into the SDK singleton before running user code.
    worker.postMessage({
      type: "invoke",
      request: req,
      authToken: req.authToken,
      projectId: meta.projectId,
    });
  });
}
