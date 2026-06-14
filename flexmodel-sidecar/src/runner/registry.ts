// ============================================================
// Function Registry — In-memory metadata cache
//
// - deploy() writes source files to disk + stores metadata
// - delete() removes metadata + disk directory
// - Key: "projectId:name"
// ============================================================

import type { DeployRequest, FunctionMeta } from "../types.ts";

const FUNCTIONS_DIR = Deno.env.get("FUNCTIONS_DIR") ?? "/tmp/flexmodel-functions";

// ---- Wrapper Code Generator ----

function generateWrapperCode(): string {
  return `
// Auto-generated wrapper — do not modify manually
// Worker message loop + SDK build + user module loading

let requestIdCounter = 0;
const pendingRequests = new Map();

function sendRpcRequest(operation, params) {
  return new Promise((resolve, reject) => {
    const requestId = "req_" + (++requestIdCounter);
    pendingRequests.set(requestId, { resolve, reject });
    self.postMessage({ type: "sdk-request", data: { requestId, operation, params } });
  });
}

self.addEventListener("message", async (e) => {
  const { type } = e.data;

  if (type === "sdk-response") {
    const pending = pendingRequests.get(e.data.requestId);
    if (pending) { pendingRequests.delete(e.data.requestId); pending.resolve(e.data.result); }
    return;
  }
  if (type === "sdk-error") {
    const pending = pendingRequests.get(e.data.requestId);
    if (pending) { pendingRequests.delete(e.data.requestId); pending.reject(new Error(e.data.error)); }
    return;
  }

  if (type === "invoke") {
    const { request, callbackUrl } = e.data;
    try {
      const mod = await import("./index.ts");
      const handler = mod.default;
      if (typeof handler !== "function") {
        self.postMessage({ type: "error", data: { message: "export default is not a function in index.ts" } });
        return;
      }

      const ctx = buildContext(callbackUrl);
      const url = request.url || "http://localhost/function";
      const req = new Request(url, {
        method: request.method || "POST",
        headers: request.headers,
        body: request.body ? JSON.stringify(request.body) : undefined,
      });

      const response = await handler(req, ctx);
      let body = response;
      let status = 200;
      let headers = {};

      if (response instanceof Response) {
        status = response.status;
        response.headers.forEach((value, key) => { headers[key] = value; });
        try {
          body = await response.json();
        } catch {
          body = await response.text();
        }
      }

      self.postMessage({ type: "result", data: { status, headers, body } });
    } catch (err) {
      self.postMessage({ type: "error", data: { message: err instanceof Error ? err.message : String(err) } });
    }
  }
});

function buildContext(callbackUrl) {
  return {
    flexmodel: {
      data: {
        find:    (model, params)       => sendRpcRequest("data.find",    { model, params }),
        findOne: (model, id)           => sendRpcRequest("data.findOne", { model, id }),
        create:  (model, data)         => sendRpcRequest("data.create",  { model, data }),
        update:  (model, id, data)     => sendRpcRequest("data.update",  { model, id, data }),
        delete:  (model, id)           => sendRpcRequest("data.delete",  { model, id }),
      },
    },
    log: {
      info:  (message, data) => self.postMessage({ type: "log", data: { level: "info",  message, data } }),
      warn:  (message, data) => self.postMessage({ type: "log", data: { level: "warn",  message, data } }),
      error: (message, data) => self.postMessage({ type: "log", data: { level: "error", message, data } }),
    },
    json: (data, status = 200) =>
      new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } }),
    text: (data, status = 200) =>
      new Response(data, { status, headers: { "content-type": "text/plain" } }),
  };
}
`.trim();
}

// ---- Registry ----

class Registry {
  private functions = new Map<string, FunctionMeta>();  // key: "projectId:name"

  /** Deploy: write source files to disk + generate wrapper + store metadata */
  async deploy(req: DeployRequest): Promise<void> {
    const functionDir = `${FUNCTIONS_DIR}/${req.projectId}/${req.functionId}`;

    // Ensure parent directory exists
    try { await Deno.mkdir(functionDir, { recursive: true }); } catch { /* ignore */ }

    // Clean old directory (for redeploy)
    try { await Deno.remove(functionDir, { recursive: true }); } catch { /* ignore */ }
    await Deno.mkdir(functionDir, { recursive: true });

    // Write all user source files (flat structure, no subdirectories)
    for (const [filename, content] of Object.entries(req.sourceFiles)) {
      if (filename.includes("/")) {
        throw new Error(`Subdirectories are not supported: ${filename}`);
      }
      await Deno.writeTextFile(`${functionDir}/${filename}`, content);
    }

    // Generate wrapper
    await Deno.writeTextFile(`${functionDir}/_worker_wrapper.ts`, generateWrapperCode());

    // Store metadata
    const entryUrl = `file://${functionDir}/_worker_wrapper.ts`;
    const key = `${req.projectId}:${req.name}`;
    this.functions.set(key, {
      id: req.functionId,
      projectId: req.projectId,
      name: req.name,
      timeout: req.timeout,
      functionDir,
      entryUrl,
    });

    console.log(`[registry] Deployed function: ${key} → ${functionDir}`);
  }

  /** Get function metadata */
  get(projectId: string, name: string): FunctionMeta | undefined {
    return this.functions.get(`${projectId}:${name}`);
  }

  /** Check if function is registered */
  has(projectId: string, name: string): boolean {
    return this.functions.has(`${projectId}:${name}`);
  }

  /** Delete function: remove metadata + disk directory */
  async delete(projectId: string, name: string): Promise<void> {
    const key = `${projectId}:${name}`;
    const meta = this.functions.get(key);
    if (meta) {
      try { await Deno.remove(meta.functionDir, { recursive: true }); } catch { /* ignore */ }
      this.functions.delete(key);
      console.log(`[registry] Removed function: ${key}`);
    }
  }

  /** Get count of registered functions */
  get size(): number {
    return this.functions.size;
  }
}

export const registry = new Registry();
