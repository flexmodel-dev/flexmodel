// ============================================================
// SDK RPC Dispatcher — Tests
// ============================================================

import { assertEquals, assertRejects } from "@std/assert";
import { handleRpcRequest } from "./flexmodel.ts";
import { mockFetch, restoreFetch } from "../test_helpers.ts";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

Deno.test("handleRpcRequest data.find delegates to GET /api/projects/:pid/models/:model/records", async () => {
  const calls: { url: string; method: string }[] = [];
  mockFetch((input, init) => {
    const req = new Request(input, init);
    calls.push({ url: req.url, method: req.method });
    return Promise.resolve(jsonResponse({ items: [] }));
  });

  try {
    const result = await handleRpcRequest("data.find", {
      model: "User",
      params: { page: 1, size: 10 },
    }, "test-project");
    assertEquals(calls.length, 1);
    assertEquals(calls[0].method, "GET");
    assertEquals(calls[0].url.includes("/api/projects/test-project/models/User/records"), true);
    const r = result as Record<string, unknown>;
    assertEquals(Array.isArray(r.items), true);
  } finally {
    restoreFetch();
  }
});

Deno.test("handleRpcRequest data.findOne delegates to GET /api/projects/:pid/models/:model/records/:id", async () => {
  mockFetch((input) => {
    return Promise.resolve(jsonResponse({ id: "123", name: "Alice" }));
  });

  try {
    const result = (await handleRpcRequest("data.findOne", {
      model: "User",
      id: "123",
    }, "test-project")) as Record<string, unknown>;
    assertEquals(result.id, "123");
  } finally {
    restoreFetch();
  }
});

Deno.test("handleRpcRequest data.create delegates to POST /api/projects/:pid/models/:model/records with body", async () => {
  let capturedBody = "";
  mockFetch((input, init) => {
    capturedBody = init?.body as string;
    return Promise.resolve(jsonResponse({ id: "new-id" }, 201));
  });

  try {
    await handleRpcRequest("data.create", {
      model: "User",
      data: { name: "Bob" },
    }, "test-project");
    assertEquals(JSON.parse(capturedBody).name, "Bob");
  } finally {
    restoreFetch();
  }
});

Deno.test("handleRpcRequest data.update delegates to PUT /api/projects/:pid/models/:model/records/:id", async () => {
  let capturedMethod = "";
  mockFetch((input, init) => {
    capturedMethod = init?.method ?? "GET";
    return Promise.resolve(jsonResponse({ updated: true }));
  });

  try {
    await handleRpcRequest("data.update", {
      model: "User",
      id: "123",
      data: { name: "Updated" },
    }, "test-project");
    assertEquals(capturedMethod, "PUT");
  } finally {
    restoreFetch();
  }
});

Deno.test("handleRpcRequest data.delete delegates to DELETE and returns success", async () => {
  let capturedMethod = "";
  mockFetch((input, init) => {
    capturedMethod = init?.method ?? "GET";
    return Promise.resolve(new Response(null, { status: 204 }));
  });

  try {
    const result = await handleRpcRequest("data.delete", {
      model: "User",
      id: "123",
    }, "test-project");
    assertEquals(capturedMethod, "DELETE");
    const r = result as Record<string, unknown>;
    assertEquals(r.success, true);
  } finally {
    restoreFetch();
  }
});

Deno.test("handleRpcRequest data.batch executes multiple ops sequentially", async () => {
  const calls: string[] = [];
  mockFetch(() => {
    calls.push("fetch");
    return Promise.resolve(jsonResponse({ ok: true }));
  });

  try {
    const result = (await handleRpcRequest("data.batch", {
      operations: [
        { op: "find", model: "User", params: {} },
        { op: "findOne", model: "User", params: { id: "1" } },
      ],
    }, "test-project")) as unknown[];
    assertEquals(result.length, 2);
    assertEquals(calls.length, 2);
  } finally {
    restoreFetch();
  }
});

Deno.test("handleRpcRequest throws on invalid operation format", async () => {
  await assertRejects(
    () => handleRpcRequest("badFormat", {}),
    Error,
    "Invalid operation",
  );
});

Deno.test("handleRpcRequest throws on unknown service", async () => {
  await assertRejects(
    () => handleRpcRequest("auth.login", {}),
    Error,
    "Unknown RPC service",
  );
});

Deno.test("handleRpcRequest throws on unknown data action", async () => {
  await assertRejects(
    () => handleRpcRequest("data.unknown", {}),
    Error,
    "Unknown data operation",
  );
});
