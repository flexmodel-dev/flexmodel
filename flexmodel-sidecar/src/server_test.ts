// ============================================================
// Server App Factory — Tests
// ============================================================

import { assertEquals } from "@std/assert";
import { createApp } from "./server.ts";

Deno.test("createApp returns a Hono instance that responds to /health", async () => {
  const app = createApp();
  const res = await app.fetch(new Request("http://localhost/health"));
  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.status, "ok");
});

Deno.test("global error handler returns 500 JSON for unhandled errors", async () => {
  const app = createApp();
  // Add a route that intentionally throws
  app.get("/throw-test", () => {
    throw new Error("intentional test error");
  });

  const res = await app.fetch(new Request("http://localhost/throw-test"));
  assertEquals(res.status, 500);
  const body = await res.json();
  assertEquals(body.error, "Internal server error");
  assertEquals(typeof body.message, "string");
});
