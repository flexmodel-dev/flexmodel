// ============================================================
// Functions Routes — Deploy, Delete, Invoke
// ============================================================

import { Hono } from "hono";
import { registry } from "../runner/registry.ts";
import { invokeFunction } from "../runner/worker.ts";
import type { DeployRequest, InvokeRequest } from "../types.ts";

const router = new Hono();

// ---- POST /functions/deploy ----
// Write source files to disk + generate wrapper + register metadata
router.post("/functions/deploy", async (c) => {
  try {
    const body: DeployRequest = await c.req.json();

    if (!body.projectId || !body.name || !body.functionId || !body.sourceFiles) {
      return c.json(
        { success: false, error: "Missing required fields: projectId, name, functionId, sourceFiles" },
        400,
      );
    }

    await registry.deploy(body);

    return c.json({ success: true, name: body.name });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    console.error("[functions] Deploy error:", message);
    return c.json({ success: false, error: message }, 500);
  }
});

// ---- DELETE /functions/:projectId/:name ----
// Remove function from registry + delete disk directory
router.delete("/functions/:projectId/:name", async (c) => {
  const { projectId, name } = c.req.param();

  if (!registry.has(projectId, name)) {
    return c.json(
      { success: false, error: `Function not found: ${projectId}:${name}` },
      404,
    );
  }

  await registry.delete(projectId, name);

  return c.json({ success: true });
});

// ---- POST /functions/:projectId/:name/invoke ----
// Invoke a function by creating an isolated Worker (file:// URL)
router.post("/functions/:projectId/:name/invoke", async (c) => {
  const { projectId, name } = c.req.param();

  if (!registry.has(projectId, name)) {
    return c.json(
      { success: false, error: `Function not found: ${projectId}:${name}` },
      404,
    );
  }

  try {
    const body = await c.req.json().catch(() => ({}));
    const request: InvokeRequest = {
      input: body.input,
    };

    const result = await invokeFunction(projectId, name, request);

    // Return function result directly as HTTP response
    // _meta is passed via response header for debug/observability
    const res = c.newResponse(
      typeof result.body === "string" ? result.body : JSON.stringify(result.body ?? null),
      result.status,
      {
        ...result.headers,
        "content-type": result.headers["content-type"] ?? "application/json",
        "x-function-meta": JSON.stringify(result._meta),
      },
    );
    return res;
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    const isTimeout = message.includes("timed out");
    const status = isTimeout ? 504 : 500;
    const errorMeta = { executionTimeMs: 0, logs: [{ level: "error", message }] };

    return c.json(
      { error: isTimeout ? "Function execution timed out" : message },
      status,
      { "x-function-meta": JSON.stringify(errorMeta) },
    );
  }
});

export { router as functionsRouter };
