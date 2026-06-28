// ============================================================
// Functions Routes — Tests
// ============================================================

import { assertEquals } from "@std/assert";
import { createApp } from "../server.ts";
import { registry } from "../runner/registry.ts";
import {
  cleanupTempDirs,
  makeTempDir,
  restoreEnv,
  setEnv,
} from "../test_helpers.ts";

Deno.test("POST /functions/deploy validates required fields", async () => {
  const app = createApp();
  const res = await app.fetch(
    new Request("http://localhost/functions/deploy", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ projectId: "p1", name: "f1" }), // missing functionId, sourceFiles
    }),
  );
  assertEquals(res.status, 400);
  const body = await res.json();
  assertEquals(body.success, false);
  assertEquals(typeof body.error, "string");
});

Deno.test("POST /functions/deploy writes files and returns success", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    const app = createApp();
    const res = await app.fetch(
      new Request("http://localhost/functions/deploy", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          projectId: "func-test-proj",
          functionId: "fid-1",
          name: "hello",
          sourceFiles: {
            "index.ts": `export default async (req: Request) => { return new Response(JSON.stringify({hello:"world"}), {headers:{"content-type":"application/json"}}); };`,
          },
          timeout: 5,
        }),
      }),
    );

    assertEquals(res.status, 200);
    const body = await res.json();
    assertEquals(body.success, true);
    assertEquals(body.name, "hello");

    await registry.delete("func-test-proj", "hello");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("DELETE /functions/:projectId/:name returns 404 when not found", async () => {
  const app = createApp();
  const res = await app.fetch(
    new Request("http://localhost/functions/func-test-proj/nonexistent", {
      method: "DELETE",
    }),
  );
  assertEquals(res.status, 404);
  const body = await res.json();
  assertEquals(body.success, false);
});

Deno.test("DELETE /functions/:projectId/:name removes deployed function", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    const app = createApp();
    // deploy first
    await app.fetch(
      new Request("http://localhost/functions/deploy", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          projectId: "del-proj",
          functionId: "del-id",
          name: "deleteme",
          sourceFiles: { "index.ts": "export default () => new Response('ok');" },
          timeout: 5,
        }),
      }),
    );

    // delete
    const res = await app.fetch(
      new Request("http://localhost/functions/del-proj/deleteme", {
        method: "DELETE",
      }),
    );
    assertEquals(res.status, 200);
    const body = await res.json();
    assertEquals(body.success, true);
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("POST /functions/:projectId/:name/invoke returns 404 for missing function", async () => {
  const app = createApp();
  const res = await app.fetch(
    new Request("http://localhost/functions/missing/missing/invoke", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({}),
    }),
  );
  assertEquals(res.status, 404);
});

Deno.test("POST /functions/:projectId/:name/invoke executes function via Worker", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    const app = createApp();
    // deploy a simple echo function
    await app.fetch(
      new Request("http://localhost/functions/deploy", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          projectId: "inv-proj",
          functionId: "inv-id",
          name: "echo",
          sourceFiles: {
            "index.ts": `
              export default async (input: any) => {
                return { echo: input };
              };
            `,
          },
          timeout: 5,
        }),
      }),
    );

    const res = await app.fetch(
      new Request("http://localhost/functions/inv-proj/echo/invoke", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ input: { message: "hi" } }),
      }),
    );

    assertEquals(res.status, 200);
    const body = await res.json();
    assertEquals((body as Record<string, unknown>).echo !== undefined, true);

    await registry.delete("inv-proj", "echo");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});
