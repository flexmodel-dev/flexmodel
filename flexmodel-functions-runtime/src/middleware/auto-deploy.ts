// ============================================================
// Auto-Deploy Middleware for Edge Routes
//
// When a function is not registered in the runtime (404),
// automatically fetches the function metadata from the Java server
// and deploys it, then retries the invocation.
//
// Uses the authToken from the already-validated edge auth context
// (set by edgeAuthMiddleware) to authenticate with the Java server.
// Calls GET /api/projects/{projectId}/functions/{name} to get
// FunctionResponse, then constructs a DeployRequest from it.
// ============================================================

import type {Context} from "hono";
import {registry} from "../runner/registry.ts";
import type {DeployRequest} from "../types.ts";
import type {EdgeAuthContext} from "./auth.ts";

const JAVA_HOST = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
const JAVA_PORT = Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080";

/**
 * Fetch function metadata from Java server and deploy to runtime.
 * Uses the authToken from edge auth context for authorization.
 */
async function fetchAndDeploy(
    projectId: string,
    name: string,
    authToken: string,
): Promise<boolean> {
    try {
        const response = await fetch(
            `http://${JAVA_HOST}:${JAVA_PORT}/api/projects/${projectId}/functions/${name}`,
            {
                headers: {
                    "Authorization": `Bearer ${authToken}`,
                },
            },
        );

        if (!response.ok) {
            console.error(`[auto-deploy] Failed to fetch function: HTTP ${response.status}`);
            return false;
        }

        const fnResponse = await response.json();

        if (!fnResponse.id || !fnResponse.name || !fnResponse.sourceFiles) {
            console.error("[auto-deploy] Invalid function response from Java server");
            return false;
        }

        // Construct DeployRequest from FunctionResponse
        const deployRequest: DeployRequest = {
            projectId: projectId,
            functionId: fnResponse.id,
            name: fnResponse.name,
            sourceFiles: fnResponse.sourceFiles,
            timeout: fnResponse.timeout ?? 30,
        };

        await registry.deploy(deployRequest);
        console.log(`[auto-deploy] Deployed: ${projectId}:${name}`);
        return true;
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.error(`[auto-deploy] Error: ${message}`);
        return false;
    }
}

/**
 * Auto-deploy middleware.
 * Runs after edgeAuthMiddleware, so edgeAuth context is always available.
 */
export async function autoDeployMiddleware(c: Context, next: () => Promise<void>) {
    const projectId = c.req.param("projectId") ?? "";
    const name = c.req.param("name") ?? "";

    // If function is already registered, proceed normally
    if (registry.has(projectId, name)) {
        await next();
        return;
    }

    // Function not registered — try to fetch and deploy from Java
    const authCtx = c.get("edgeAuth") as EdgeAuthContext | undefined;
    if (!authCtx?.authToken) {
        return c.json(
            {error: `Function not found: ${projectId}:${name}`},
            404,
        );
    }

    console.log(`[auto-deploy] Function not found: ${projectId}:${name}, fetching from Java`);

    const deployed = await fetchAndDeploy(projectId, name, authCtx.authToken);
    if (!deployed) {
        return c.json(
            {error: `Function not found: ${projectId}:${name}`},
            404,
        );
    }

    // Deployment succeeded — proceed with invocation
    await next();
}
