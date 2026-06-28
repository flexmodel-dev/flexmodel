// ============================================================
// Function Registry — In-memory metadata cache
//
// - deploy() writes source files to disk + stores metadata
// - delete() removes metadata + disk directory
// - Key: "projectId:name"
// - Deploy-before-invoke pattern ensures consistency with Java server
// ============================================================

import type { DeployRequest, FunctionMeta } from "../types.ts";

const FUNCTIONS_DIR = Deno.env.get("FUNCTIONS_DIR") ?? "/tmp/flexmodel-functions";

// ---- Wrapper Code Generator ----

function generateWrapperCode(): string {
  return `// @ts-nocheck
// Auto-generated wrapper — do not modify manually
// Worker message loop + SDK token/projectId injection + user module loading
// console.log/warn/error 被重写，通过 SDK 写入 f_function_log 表

import { flexmodelClient } from "@flexmodel/sdk";

self.addEventListener("message", async (e) => {
  const { type } = e.data;

  if (type === "invoke") {
    const { request, authToken, projectId, invokeId, functionName } = e.data;

    // ---- console 拦截：每条日志通过 SDK 异步写入 f_function_log ----
    const __logPromises = [];
    const __originalConsole = {
      log: console.log.bind(console),
      warn: console.warn.bind(console),
      error: console.error.bind(console),
    };
    const __serialize = (args) => args.map((a) => {
      if (typeof a === "string") return a;
      try { return JSON.stringify(a); } catch { return String(a); }
    }).join(" ");
    const __writeLog = (level, args) => {
      __originalConsole[level](...args);
      if (!invokeId) return;
      const msg = __serialize(args);
      const p = flexmodelClient.data.from("f_function_log").create({
        invoke_id: invokeId,
        function_name: functionName ?? "",
        level: level,
        message: msg,
      }).catch(() => { /* 日志写入失败不影响函数执行 */ });
      __logPromises.push(p);
    };
    console.log = (...args) => __writeLog("log", args);
    console.warn = (...args) => __writeLog("warn", args);
    console.error = (...args) => __writeLog("error", args);

    try {
      // Inject auth token + projectId into the SDK singleton before user code runs
      if (authToken) flexmodelClient.setAuthToken(authToken);
      if (projectId) flexmodelClient.setProjectId(projectId);

      const mod = await import("./index.ts");
      const handler = mod.default;
      if (typeof handler !== "function") {
        await Promise.allSettled(__logPromises);
        self.postMessage({ type: "error", data: { message: "export default is not a function in index.ts" } });
        return;
      }

      const input = request.input ?? null;

      const response = await handler(input);
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

      // flush 日志：确保所有 console 输出落库后再返回结果
      await Promise.allSettled(__logPromises);
      self.postMessage({ type: "result", data: { status, headers, body } });
    } catch (err) {
      await Promise.allSettled(__logPromises);
      self.postMessage({ type: "error", data: { message: err instanceof Error ? err.message : String(err) } });
    }
  }
});
`.trim();
}

// ---- Function-scoped deno.json (import map for @flexmodel/sdk) ----

function generateFunctionDenoJson(): string {
  return JSON.stringify({
    imports: {
      "@flexmodel/sdk": "npm:@flexmodel/sdk@0.0.3",
    },
  }, null, 2);
}

// ---- Registry ----

class Registry {
  private functions = new Map<string, FunctionMeta>();  // key: "projectId:name"

  /** Deploy: write source files to disk + generate wrapper + store metadata */
  async deploy(req: DeployRequest): Promise<void> {
    const rawDir = `${FUNCTIONS_DIR}/${req.projectId}/${req.functionId}`;

    // Ensure parent directory exists
    try { await Deno.mkdir(rawDir, { recursive: true }); } catch { /* ignore */ }

    // Clean old directory (for redeploy)
    try { await Deno.remove(rawDir, { recursive: true }); } catch { /* ignore */ }
    await Deno.mkdir(rawDir, { recursive: true });

    // Resolve to real absolute path (critical for Worker permissions on Windows)
    const functionDir = Deno.realPathSync(rawDir);

    // Write all user source files (flat structure, no subdirectories)
    for (const [filename, content] of Object.entries(req.sourceFiles)) {
      if (filename.includes("/")) {
        throw new Error(`Subdirectories are not supported: ${filename}`);
      }
      await Deno.writeTextFile(`${functionDir}/${filename}`, content);
    }

    // Generate wrapper
    await Deno.writeTextFile(`${functionDir}/_worker_wrapper.ts`, generateWrapperCode());

    // Write function-scoped deno.json (import map for @flexmodel/sdk)
    // Worker loads via file:// URL from this dir, so it cannot inherit the
    // project-level import map — it needs its own.
    await Deno.writeTextFile(`${functionDir}/deno.json`, generateFunctionDenoJson());

    // Store metadata
    const entryUrl = `file:///${functionDir.replace(/\\/g, "/")}/_worker_wrapper.ts`;
    const key = `${req.projectId}:${req.name}`;
    this.functions.set(key, {
      id: req.functionId,
      projectId: req.projectId,
      name: req.name,
      timeout: req.timeout,
      functionDir,
      entryUrl,
    });

    console.log(`[registry] Deployed: ${key}`);
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
      console.log(`[registry] Removed: ${key}`);
    }
  }

  get size(): number {
    return this.functions.size;
  }
}

export const registry = new Registry();
