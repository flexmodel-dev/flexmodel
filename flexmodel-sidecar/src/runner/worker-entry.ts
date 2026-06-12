// ============================================================
// Worker Entry — Runs inside each isolated Deno Worker
//
// - Receives invoke message from main process
// - Dynamically imports the user's function source code
// - Proxies SDK requests via postMessage back to main process
// - Returns result (or error) via postMessage
// ============================================================

/// <reference no-default-lib="true" />
/// <reference lib="deno.worker" />

// ---- Pending SDK RPC Requests ----

let requestIdCounter = 0;
const pendingRequests = new Map<
  string,
  { resolve: (value: unknown) => void; reject: (reason: Error) => void }
>();

function sendRpcRequest(operation: string, params: unknown): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const requestId = `req_${++requestIdCounter}`;
    pendingRequests.set(requestId, { resolve, reject });
    self.postMessage({
      type: "sdk-request",
      data: { requestId, operation, params },
    });
  });
}

// ---- Logging ----

const MAX_LOG_ENTRIES = 100;
let logCount = 0;

function sendLog(level: string, message: string, data?: unknown): void {
  if (logCount >= MAX_LOG_ENTRIES) return; // truncate beyond limit
  logCount++;
  self.postMessage({ type: "log", data: { level, message, data } });
}

// ---- Build Context (SDK) object for the user's function ----

function buildContext(callbackUrl: string) {
  return {
    flexmodel: {
      // Generic RPC dispatcher (for future extensibility)
      call: (operation: string, params?: unknown) =>
        sendRpcRequest(operation, params),
      // Convenience data operations
      data: {
        find: (model: string, params?: unknown) =>
          sendRpcRequest("data.find", { model, params }),
        findOne: (model: string, id: string) =>
          sendRpcRequest("data.findOne", { model, id }),
        create: (model: string, data: unknown) =>
          sendRpcRequest("data.create", { model, data }),
        update: (model: string, id: string, data: unknown) =>
          sendRpcRequest("data.update", { model, id, data }),
        delete: (model: string, id: string) =>
          sendRpcRequest("data.delete", { model, id }),
        batch: (operations: Array<{ op: string; model: string; params?: unknown }>) =>
          sendRpcRequest("data.batch", { operations }),
      },
    },
    log: {
      info: (message: string, data?: unknown) =>
        sendLog("info", message, data),
      warn: (message: string, data?: unknown) =>
        sendLog("warn", message, data),
      error: (message: string, data?: unknown) =>
        sendLog("error", message, data),
    },
    json: (data: unknown, status = 200) =>
      new Response(JSON.stringify(data), {
        status,
        headers: { "content-type": "application/json" },
      }),
    text: (data: string, status = 200) =>
      new Response(data, {
        status,
        headers: { "content-type": "text/plain" },
      }),
    env: {},
  };
}

// ---- Unified Message Handler ----

self.addEventListener("message", async (e: MessageEvent) => {
  const { type } = e.data;

  // Handle SDK RPC responses
  if (type === "sdk-response") {
    const pending = pendingRequests.get(e.data.requestId);
    if (pending) {
      pendingRequests.delete(e.data.requestId);
      pending.resolve(e.data.result);
    }
    return;
  }

  if (type === "sdk-error") {
    const pending = pendingRequests.get(e.data.requestId);
    if (pending) {
      pendingRequests.delete(e.data.requestId);
      pending.reject(new Error(e.data.error));
    }
    return;
  }

  // Handle invoke
  if (type === "invoke") {
    const { sourceCode, entryPoint, request, callbackUrl } = e.data;
    try {
      // Dynamically import the user's code as a module
      const moduleUrl = `data:application/typescript;base64,${btoa(sourceCode)}`;
      const mod = await import(moduleUrl);

      const handler = mod[entryPoint];
      if (typeof handler !== "function") {
        self.postMessage({
          type: "error",
          data: { message: `Entry point "${entryPoint}" is not a function` },
        });
        return;
      }

      const ctx = buildContext(callbackUrl);

      // Build a standard Request object
      const url = request.url || "http://localhost/function";
      const req = new Request(url, {
        method: request.method || "POST",
        headers: new Headers(request.headers || {}),
        body: request.body ? JSON.stringify(request.body) : undefined,
      });

      const response = await handler(req, ctx);

      // Extract response data
      let body: unknown = response;
      let status = 200;
      let headers: Record<string, string> = {};

      if (response instanceof Response) {
        status = response.status;
        headers = {};
        response.headers.forEach((value, key) => {
          headers[key] = value;
        });
        try {
          body = await response.json();
        } catch {
          body = await response.text();
        }
      }

      self.postMessage({
        type: "result",
        data: { status, headers, body },
      });
    } catch (err: unknown) {
      self.postMessage({
        type: "error",
        data: {
          message: err instanceof Error ? err.message : String(err),
        },
      });
    }
  }
});
