// ============================================================
// Authentication Middleware for Edge Routes
//
// All token validation is delegated to the Java server via
// POST /api/edge/validate. The Deno runtime no longer needs
// JWT_SECRET or any local auth logic — Java is the single
// source of truth for authentication.
// ============================================================

import type {Context} from "hono";

const JAVA_HOST = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
const JAVA_PORT = Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080";

/** Edge auth context injected into Hono context */
export interface EdgeAuthContext {
    projectId: string;
    functionName: string;
    authToken: string;
    invokeId: string;
    authType: "invoke-token" | "api-key" | "idp" | "anonymous";
}

/**
 * Validate any edge token (invoke-token JWT or API Key) by calling the Java server.
 */
async function validateViaJava(
    token: string,
    projectId: string,
    functionName: string,
): Promise<EdgeAuthContext | null> {
    try {
        const response = await fetch(
            `http://${JAVA_HOST}:${JAVA_PORT}/api/edge/validate`,
            {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({token, projectId, functionName}),
            },
        );

        if (!response.ok) return null;

        const result = await response.json();
        if (!result.valid) return null;

        return {
            projectId: result.projectId,
            functionName: result.functionName,
            authToken: result.authToken,
            invokeId: result.invokeId,
            authType: result.authType,
        };
    } catch {
        return null;
    }
}

/**
 * Edge authentication middleware.
 * Extracts the Authorization header (if present) and validates via Java.
 * When no token is provided, Java determines whether anonymous access is allowed
 * (no IdP configured → anonymous; IdP configured → rejected).
 */
export async function edgeAuthMiddleware(c: Context, next: () => Promise<void>) {
    const projectId = c.req.param("projectId") ?? "";
    const functionName = c.req.param("name") ?? "";

    // Extract token from Authorization header (may be absent)
    const authHeader = c.req.header("Authorization") ?? "";
    const token = authHeader.startsWith("Bearer ")
        ? authHeader.slice(7).trim()
        : "";

    const authCtx = await validateViaJava(token, projectId, functionName);
    if (!authCtx) {
        return c.json({error: "Invalid token"}, 401);
    }

    c.set("edgeAuth", authCtx);
    await next();
}
