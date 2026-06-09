// ============================================================
// Functions Routes — Deploy, Delete, Invoke
// ============================================================

import { Hono } from "hono";
import { registry } from "../runner/registry.ts";
import { invokeFunction } from "../runner/worker.ts";
import type { DeployRequest, InvokeRequest } from "../types.ts";

const router = new Hono();

// ---- POST /functions/deploy ----
// Register function metadata (no source code — lazy loaded on first invoke)
router.post("/functions/deploy", async (c) => {
  try {
    const body: DeployRequest = await c.req.json();

    // Validate required fields
    if (!body.projectId || !body.name || !body.functionId) {
      return c.json(
        { success: false, error: "Missing required fields: projectId, name, functionId" },
        400,
      );
    }

    registry.deploy(body);

    return c.json({
      success: true,
      name: body.name,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    console.error("[functions] Deploy error:", message);
    return c.json({ success: false, error: message }, 500);
  }
});

// ---- DELETE /functions/:projectId/:name ----
// Remove function from registry
router.delete("/functions/:projectId/:name", (c) => {
  const { projectId, name } = c.req.param();

  if (!registry.has(projectId, name)) {
    return c.json(
      { success: false, error: `Function not found: ${projectId}:${name}` },
      404,
    );
  }

  registry.delete(projectId, name);

  return c.json({ success: true });
});

// ---- POST /functions/:projectId/:name/invoke ----
// Invoke a function by creating an isolated Worker
router.post("/functions/:projectId/:name/invoke", async (c) => {
  const { projectId, name } = c.req.param();

  if (!registry.has(projectId, name)) {
    return c.json(
      {
        success: false,
        error: `Function not found: ${projectId}:${name}`,
      },
      404,
    );
  }

  try {
    const body = await c.req.json().catch(() => ({}));
    const request: InvokeRequest = {
      method: body.method ?? "GET",
      headers: body.headers ?? {},
      body: body.body,
      query: body.query,
    };

    const result = await invokeFunction(projectId, name, request);

    return c.json(result);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);

    // Determine if it's a timeout error
    const isTimeout = message.includes("timed out");

    return c.json(
      {
        status: isTimeout ? 504 : 500,
        headers: { "content-type": "application/json" },
        body: { error: isTimeout ? "Function execution timed out" : message },
        _meta: {
          executionTimeMs: 0,
          logs: [
            {
              level: "error",
              message,
            },
          ],
        },
      },
      isTimeout ? 504 : 500,
    );
  }
});

export { router as functionsRouter };
