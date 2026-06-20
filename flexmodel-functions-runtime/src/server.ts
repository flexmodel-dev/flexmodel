// ============================================================
// Hono.js Server Initialization
// ============================================================

import { Hono } from "hono";
import { logger } from "hono/logger";
import { functionsRouter } from "./router/functions.ts";
import { healthRouter } from "./router/health.ts";

/**
 * Create and configure the Hono.js application.
 */
export function createApp(): Hono {
  const app = new Hono();

  // Request logging (dev-friendly)
  app.use("*", logger());

  // Mount route groups
  app.route("/", healthRouter);
  app.route("/", functionsRouter);

  // Global error handler
  app.onError((err, c) => {
    console.error("[server] Unhandled error:", err.message);
    return c.json(
      {
        error: "Internal server error",
        message: err.message,
      },
      500,
    );
  });

  return app;
}
