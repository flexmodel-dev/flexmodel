// ============================================================
// Health Check Route
// ============================================================

import { Hono } from "hono";
import { registry } from "../runner/registry.ts";

const startTime = Date.now();

const router = new Hono();

router.get("/health", (c) => {
  const uptime = Math.floor((Date.now() - startTime) / 1000);
  return c.json({
    status: "ok",
    uptime,
    functions: registry.size,
  });
});

export { router as healthRouter };
