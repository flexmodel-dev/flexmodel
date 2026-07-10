// ============================================================
// Worker Executor — Main Process
//
// Creates an isolated Deno Worker for each function invocation.
// Enforces timeout via worker.terminate().
// Worker loads function code via file:// URL (native relative import support).
// SDK runs directly inside Worker — no RPC proxying needed.
// ============================================================

import type {FunctionMeta, InvokeResult} from "../types.ts";
import {registry} from "./registry.ts";

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

  // Dynamically construct net permission from env vars.
  // In Docker: FLEXMODEL_JAVA_HOST=flexmodel-server → allow "flexmodel-server:8080"
  // In dev:    FLEXMODEL_JAVA_HOST=localhost        → already covered by "localhost"
  const javaHost = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
  const javaPort = Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080";
  const allowedNet: string[] = ["localhost", `${javaHost}:${javaPort}`];

  let worker: Worker;
  try {
    // 1. Create Worker with minimal permissions, loading via file:// URL
    worker = new Worker(meta.entryUrl, {
      type: "module",
      deno: {
        permissions: {
          net: allowedNet,           // SDK fetches Java API on allowed hosts
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
          _meta: {executionTimeMs, invokeId},
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

    // 4. Trigger execution — pass body + metadata so the wrapper can
    //    build a standard Request object and inject authToken into SDK singleton.
    worker.postMessage({
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
