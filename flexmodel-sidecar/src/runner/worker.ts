// ============================================================
// Worker Executor — Main Process
//
// Creates an isolated Deno Worker for each function invocation.
// Enforces timeout via worker.terminate().
// Proxies SDK RPC requests from Worker → Java API.
// ============================================================

import type { FunctionMeta, InvokeRequest, InvokeResult } from "../types.ts";
import { registry } from "./registry.ts";
import { handleRpcRequest } from "../sdk/flexmodel.ts";

const JAVA_HOST = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
const JAVA_PORT = parseInt(Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080");

/**
 * Invoke a function by name within a project.
 * - Loads source code (lazy load via Registry)
 * - Creates an isolated Worker
 * - Executes the function with timeout protection
 * - Returns the result with execution metadata
 */
export async function invokeFunction(
  projectId: string,
  name: string,
  req: InvokeRequest,
): Promise<InvokeResult> {
  const meta = registry.get(projectId, name);
  if (!meta) {
    throw new Error(
      `Function not found: ${projectId}:${name}`,
    );
  }

  // Lazy load source code
  const sourceCode = await registry.getSourceCode(meta);

  return executeInWorker(meta, sourceCode, req);
}

/**
 * Execute function in an isolated Worker with timeout enforcement.
 */
async function executeInWorker(
  meta: FunctionMeta,
  sourceCode: string,
  req: InvokeRequest,
): Promise<InvokeResult> {
  return new Promise((resolve, reject) => {
    const startTime = performance.now();
    const collectedLogs: Array<{
      level: string;
      message: string;
      data?: unknown;
    }> = [];

    // 1. Create Worker with minimal permissions
    const worker = new Worker(
      new URL("./worker-entry.ts", import.meta.url).href,
      {
        type: "module",
        deno: {
          permissions: {
            net: ["localhost"], // only allow callback to Java API
            read: false,
            write: false,
            env: false,
            run: false,
            ffi: false,
            hrtime: false,
          },
        },
      },
    );

    // 2. Timeout enforcement
    const timer = setTimeout(() => {
      worker.terminate();
      reject(
        new Error(`Function execution timed out after ${meta.timeout}s`),
      );
    }, meta.timeout * 1000);

    // 3. Handle messages from Worker
    worker.onmessage = (e: MessageEvent) => {
      const { type, data } = e.data;

      if (type === "sdk-request") {
        // Proxy SDK RPC: Worker → Main Process → Java API → Main Process → Worker
        handleRpcRequest(data.operation, data.params)
          .then((result) => {
            worker.postMessage({
              type: "sdk-response",
              requestId: data.requestId,
              result,
            });
          })
          .catch((err) => {
            worker.postMessage({
              type: "sdk-error",
              requestId: data.requestId,
              error: err instanceof Error ? err.message : String(err),
            });
          });
        return;
      }

      if (type === "log") {
        // Collect logs + output to console
        const entry = {
          level: data.level,
          message: data.message,
          data: data.data,
        };
        collectedLogs.push(entry);
        console.log(
          `[fn:${meta.name}][${data.level}] ${data.message}`,
          data.data ?? "",
        );
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

    // 4. Start execution
    worker.postMessage({
      type: "invoke",
      sourceCode: sourceCode,
      entryPoint: meta.entryPoint,
      request: req,
      callbackUrl: `http://${JAVA_HOST}:${JAVA_PORT}`,
    });
  });
}
