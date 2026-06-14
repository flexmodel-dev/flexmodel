// ============================================================
// Worker Entry — Runs inside each isolated Deno Worker
//
// - Loaded via file:// URL (wrapper imports user's ./index.ts)
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
  if (logCount >= MAX_LOG_ENTRIES) return;
  logCount++;
  self.postMessage({ type: "log", data: { level, message, data } });
}

// ---- Build Context (SDK) object for the user's function ----

function buildContext(callbackUrl: string) {
  return {
    flexmodel: {
      call: (operation: string, params?: unknown) =>
        sendRpcRequest(operation, params),
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
  };
}

// ---- Message Handler ----

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

  // Handle invoke — load user module via file:// relative import
  if (type === "invoke") {
    const { request, callbackUrl } = e.data;
    try {
      // Dynamic import of user's index.ts (relative to this wrapper)
      const mod = await import("./index.ts");
      const handler = mod.default;

      if (typeof handler !== "function") {
        self.postMessage({
          type: "error",
          data: { message: "export default is not a function in index.ts" },
        });
        return;
      }

      const ctx = buildContext(callbackUrl);

      const url = request.url || "http://localhost/function";
      const req = new Request(url, {
        method: request.method || "POST",
        headers: new Headers(request.headers || {}),
        body: request.body ? JSON.stringify(request.body) : undefined,
      });

      const response = await handler(req, ctx);

      let body: unknown = response;
      let status = 200;
      let headers: Record<string, string> = {};

      if (response instanceof Response) {
        status = response.status;
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
