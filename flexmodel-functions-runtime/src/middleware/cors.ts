// ============================================================
// CORS Middleware for Edge Routes
//
// Allows cross-origin requests from the main domain and
// wildcard subdomains ({projectId}.domain).
// In development (PROJECT_BASE_DOMAIN=localhost), allows
// all localhost origins regardless of port.
// ============================================================

import type {Context} from "hono";
import {cors} from "hono/cors";

const PROJECT_BASE_DOMAIN = Deno.env.get("PROJECT_BASE_DOMAIN") ?? Deno.env.get("EDGE_DOMAIN") ?? "localhost";
const EXTRA_ALLOWED_ORIGINS = (Deno.env.get("CORS_ALLOWED_ORIGINS") ?? "")
    .split(",").map((s) => s.trim()).filter(Boolean);

/**
 * CORS middleware for edge routes.
 * Allows requests from:
 * - https://{projectId}.{PROJECT_BASE_DOMAIN} (subdomain wildcard)
 * - https://{PROJECT_BASE_DOMAIN} (main domain)
 * - http://localhost:* (development — any port)
 * - http://127.0.0.1:* (development — any port)
 */
export const edgeCorsMiddleware = cors({
    origin: (origin: string, c: Context) => {
        // Development: allow any localhost / 127.0.0.1 origin regardless of port
        if (origin.startsWith("http://localhost:") || origin === "http://localhost"
            || origin.startsWith("http://127.0.0.1:") || origin === "http://127.0.0.1") {
            return origin;
        }
        // Allow all subdomains of the project base domain
        if (origin.endsWith(`.${PROJECT_BASE_DOMAIN}`) || origin === `https://${PROJECT_BASE_DOMAIN}` ||
            origin === `http://${PROJECT_BASE_DOMAIN}`) {
            return origin;
        }
        // Allow extra origins configured via CORS_ALLOWED_ORIGINS (comma-separated)
        if (EXTRA_ALLOWED_ORIGINS.includes(origin)) {
            return origin;
        }
        // Deny other origins
        return "";
    },
    allowMethods: ["POST", "OPTIONS"],
    allowHeaders: ["Authorization", "Content-Type"],
    exposeHeaders: ["x-function-meta"],
    maxAge: 300,
});
