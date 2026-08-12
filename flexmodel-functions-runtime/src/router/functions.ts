// ============================================================
// Functions Routes — Deploy, Delete, Invoke (internal + external)
//
// Internal routes (Java → Deno, no auth required):
//   POST   /functions/deploy                    — deploy function
//   DELETE /functions/:projectId/:name          — delete function
//   POST   /functions/:projectId/:name/invoke   — invoke (Java-proxied)
//
// External route (Frontend → Deno, auth required):
//   POST   /functions/:projectId/:name          — invoke (direct, with invoke-token/API-Key)
//   OPTIONS /functions/:projectId/:name         — CORS preflight
// ============================================================

import {Hono} from "hono";
import type {StatusCode} from "hono/utils/http-status";
import {registry} from "../runner/registry.ts";
import {invokeFunction} from "../runner/worker.ts";
import type {DeployRequest} from "../types.ts";
import {type EdgeAuthContext, edgeAuthMiddleware} from "../middleware/auth.ts";
import {edgeCorsMiddleware} from "../middleware/cors.ts";
import {autoDeployMiddleware} from "../middleware/auto-deploy.ts";

const router = new Hono<{ Variables: { edgeAuth: EdgeAuthContext } }>();

// ============================================================
// Internal routes (Java → Deno, no auth required)
// ============================================================

// ---- POST /functions/deploy ----
// Write source files to disk + generate wrapper + register metadata
router.post("/functions/deploy", async (c) => {
    try {
        const body: DeployRequest = await c.req.json();

        if (!body.projectId || !body.name || !body.functionId || !body.sourceFiles) {
            return c.json(
                {success: false, error: "Missing required fields: projectId, name, functionId, sourceFiles"},
                400,
            );
        }

        await registry.deploy(body);

        return c.json({success: true, name: body.name});
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : String(err);
        console.error("[functions] Deploy error:", message);
        return c.json({success: false, error: message}, 500);
    }
});

// ---- DELETE /functions/:projectId/:name ----
// Remove function from registry + delete disk directory
router.delete("/functions/:projectId/:name", async (c) => {
    const {projectId, name} = c.req.param();

    if (!registry.has(projectId, name)) {
        return c.json(
            {success: false, error: `Function not found: ${projectId}:${name}`},
            404,
        );
    }

    await registry.delete(projectId, name);

    return c.json({success: true});
});

// ---- POST /functions/:projectId/:name/invoke ----
// Internal invoke (Java → Deno, no auth required)
// authToken 和 invokeId 通过 HTTP headers 传入（由 Java 服务端设置）
// 请求体直接作为函数的 Request body
router.post("/functions/:projectId/:name/invoke", async (c) => {
    const {projectId, name} = c.req.param();

    if (!registry.has(projectId, name)) {
        return c.json(
            {success: false, error: `Function not found: ${projectId}:${name}`},
            404,
        );
    }

    // 从 headers 提取服务端注入的元数据
    const authToken = c.req.header("x-flexmodel-auth-token");
    const invokeId = c.req.header("x-flexmodel-invoke-id");

    // 收集所有 incoming headers 传给 Worker，让云函数的 Request 对象能访问原始客户端 headers
    const forwardedHeaders: Record<string, string> = {};
    for (const [key, value] of Object.entries(c.req.header())) {
        // 排除 hop-by-hop headers 和内部元数据 headers（内部 headers 通过单独字段传递）
        const lower = key.toLowerCase();
        if (lower !== "host" && lower !== "content-length" && lower !== "transfer-encoding"
            && lower !== "connection" && lower !== "x-flexmodel-auth-token" && lower !== "x-flexmodel-invoke-id") {
            forwardedHeaders[key] = value;
        }
    }

    // 请求体直接作为函数输入（不再嵌套在 input 字段中）
    const body = await c.req.json().catch(() => null);

    try {
        const result = await invokeFunction(projectId, name, body, authToken, invokeId, forwardedHeaders);

        // Return function result directly as HTTP response
        // _meta is passed via response header for debug/observability
        const res = c.newResponse(
            typeof result.body === "string" ? result.body : JSON.stringify(result.body ?? null),
            result.status as StatusCode,
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
        const errorMeta = {executionTimeMs: 0, invokeId};

        return c.json(
            {error: isTimeout ? "Function execution timed out" : message},
            status,
            {"x-function-meta": JSON.stringify(errorMeta)},
        );
    }
});

// ============================================================
// External route (Frontend → Deno, auth required)
// POST /functions/:projectId/:name — direct invoke with invoke-token/API-Key
// No /invoke suffix — this is how we distinguish external vs internal calls.
// ============================================================

// ---- OPTIONS /functions/:projectId/:name — CORS preflight ----
router.options("/functions/:projectId/:name", edgeCorsMiddleware);

// ---- POST /functions/:projectId/:name ----
// External invoke: frontend calls this directly with invoke-token or API Key.
// Auth: invoke-token JWT (Bearer) or API Key (fm_ak_ prefix)
// Auto-deploy: if function not registered, fetch from Java and deploy
router.post(
    "/functions/:projectId/:name",
    edgeCorsMiddleware,
    edgeAuthMiddleware,
    autoDeployMiddleware,
    async (c) => {
        const {projectId, name} = c.req.param();
        const authCtx = c.get("edgeAuth");

        // Collect forwarded headers (exclude internal and hop-by-hop headers)
        const forwardedHeaders: Record<string, string> = {};
        for (const [key, value] of Object.entries(c.req.header())) {
            const lower = key.toLowerCase();
            if (lower !== "host" && lower !== "content-length" && lower !== "transfer-encoding"
                && lower !== "connection" && lower !== "authorization"
                && lower !== "x-flexmodel-auth-token" && lower !== "x-flexmodel-invoke-id") {
                forwardedHeaders[key] = value;
            }
        }

        // Request body as function input
        const body = await c.req.json().catch(() => null);

        try {
            const result = await invokeFunction(
                projectId,
                name,
                body,
                authCtx.authToken,
                authCtx.invokeId,
                forwardedHeaders,
            );

            // Return function result directly as HTTP response
            const res = c.newResponse(
                typeof result.body === "string" ? result.body : JSON.stringify(result.body ?? null),
                result.status as StatusCode,
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
            const errorMeta = {executionTimeMs: 0, invokeId: authCtx.invokeId};

            return c.json(
                {error: isTimeout ? "Function execution timed out" : message},
                status,
                {"x-function-meta": JSON.stringify(errorMeta)},
            );
        }
    },
);

export { router as functionsRouter };
