// ============================================================
// Health Check Route — Tests
// ============================================================

import { assertEquals, assertExists } from "@std/assert";
import { createApp } from "../server.ts";

Deno.test("GET /health returns ok with uptime and functions count", async () => {
  const app = createApp();
  const res = await app.fetch(new Request("http://localhost/health"));
  assertEquals(res.status, 200);

  const body = await res.json();
  assertEquals(body.status, "ok");
  assertExists(body.uptime);
  assertEquals(typeof body.uptime, "number");
  assertEquals(body.uptime >= 0, true);
  assertEquals(typeof body.functions, "number");
  assertEquals(body.functions >= 0, true);
});
