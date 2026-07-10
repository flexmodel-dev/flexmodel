// ============================================================
// Worker Executor — Integration Tests
//
// These tests create actual Deno Workers to exercise the full
// pipeline: invokeFunction -> Worker -> wrapper -> user module.
// Requires --unstable-worker-options flag.
// ============================================================

import {assertEquals, assertRejects} from "@std/assert";
import {invokeFunction} from "./worker.ts";
import {registry} from "./registry.ts";
import {cleanupTempDirs, makeTempDir, restoreEnv, setEnv,} from "../test_helpers.ts";

// Helper to quickly deploy a test function
async function deployTestFunction(
  projectId: string,
  name: string,
  code: string,
  timeout = 5,
) {
  await registry.deploy({
    projectId,
    functionId: `${projectId}-${name}-id`,
    name,
    sourceFiles: { "index.ts": code },
    timeout,
  });
}

Deno.test("invokeFunction runs a simple function returning plain object", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await deployTestFunction(
      "wk-p1",
      "jsonFn",
      `
        export default async (req: Request) => {
          return { answer: 42 };
        };
      `,
    );

    const result = await invokeFunction("wk-p1", "jsonFn", {});

    assertEquals(result.status, 200);
    assertEquals(result.body, { answer: 42 });
    assertEquals(result._meta.executionTimeMs >= 0, true);

    await registry.delete("wk-p1", "jsonFn");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("invokeFunction runs a function returning a Response object", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await deployTestFunction(
      "wk-p2",
      "responseFn",
      `
        export default async (req: Request) => {
          return new Response(JSON.stringify({type:"response"}), {
            status: 201,
            headers: { "content-type": "application/json", "x-custom": "yes" }
          });
        };
      `,
    );

    const result = await invokeFunction("wk-p2", "responseFn", {});

    assertEquals(result.status, 201);
    assertEquals((result.body as Record<string, unknown>).type, "response");
    assertEquals(result.headers["x-custom"], "yes");

    await registry.delete("wk-p2", "responseFn");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("invokeFunction enforces timeout", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await deployTestFunction(
      "wk-p3",
      "slow",
      `
        export default async (req: Request) => {
          await new Promise(r => setTimeout(r, 10000));
          return { ok: true };
        };
      `,
      1, // 1 second timeout
    );

    await assertRejects(
      () =>
          invokeFunction("wk-p3", "slow", {}),
      Error,
      "timed out",
    );

    await registry.delete("wk-p3", "slow");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("invokeFunction propagates runtime errors", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await deployTestFunction(
      "wk-p4",
      "boom",
      `
        export default async (req: Request) => {
          throw new Error("intentional boom");
        };
      `,
    );

    await assertRejects(
      () =>
          invokeFunction("wk-p4", "boom", {}),
      Error,
      "intentional boom",
    );

    await registry.delete("wk-p4", "boom");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("invokeFunction fails when function directory is missing", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await deployTestFunction(
      "wk-p5",
      "ghost",
        `export default async (req: Request) => ({ok:true});`,
    );

    // Manually nuke directory to simulate corruption
    const meta = registry.get("wk-p5", "ghost")!;
    await Deno.remove(meta.functionDir, { recursive: true });

    await assertRejects(
      () =>
          invokeFunction("wk-p5", "ghost", {}),
      Error,
      "Function directory not found",
    );

    // metadata still exists but dir is gone; clean registry entry
    // registry.delete would try to remove the already-removed dir, which is fine
    await registry.delete("wk-p5", "ghost");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("invokeFunction runs user code that calls SDK directly", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  // SDK inside the Worker calls fetch directly (not via postMessage),
  // so a globalThis.fetch mock in the main process won't be visible.
  // Spin up a real local mock server that the Worker can hit.
  const mockServer = Deno.serve({ port: 0 }, (req) => {
    const url = new URL(req.url);
    // SDK calls /api/projects/:pid/models/:model/records
    if (url.pathname.includes("/api/projects/wk-p6/models/User/records")) {
      return new Response(
        JSON.stringify({ list: [{ id: "u1" }], total: 1 }),
        { headers: { "content-type": "application/json" } },
      );
    }
    return new Response("{}", { status: 404 });
  });
  const mockPort = mockServer.addr.port;

  // Point SDK's baseURL at the mock server
  setEnv("FLEXMODEL_JAVA_HOST", "localhost");
  setEnv("FLEXMODEL_JAVA_PORT", String(mockPort));

  try {
    await deployTestFunction(
      "wk-p6",
      "sdkUser",
      `
        import { flexmodelClient } from "@flexmodel/sdk";

        export default async (req: Request) => {
          // runtime has already called setAuthToken + setProjectId on the singleton
          const users = await flexmodelClient.data.from("User").findMany({ page: 1, size: 10 });
          return { users };
        };
      `,
    );

    const result = await invokeFunction("wk-p6", "sdkUser", {}, "test-token");
    assertEquals(result.status, 200);
    const body = result.body as Record<string, unknown>;
    assertEquals(body.users !== undefined, true);

    await registry.delete("wk-p6", "sdkUser");
  } finally {
    await mockServer.shutdown();
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("invokeFunction passes Request with accessible body and headers", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await deployTestFunction(
        "wk-p7",
        "requestInspector",
        `
        export default async (req: Request) => {
          const body = await req.json();
          return {
            method: req.method,
            url: req.url,
            projectId: req.headers.get("x-flexmodel-project-id"),
            invokeId: req.headers.get("x-flexmodel-invoke-id"),
            functionName: req.headers.get("x-flexmodel-function-name"),
            contentType: req.headers.get("content-type"),
            echo: body,
          };
        };
      `,
    );

    const invokeId = "test-invoke-123";
    const result = await invokeFunction("wk-p7", "requestInspector", {message: "hello"}, undefined, invokeId);

    assertEquals(result.status, 200);
    const body = result.body as Record<string, unknown>;
    assertEquals(body.method, "POST");
    assertEquals(body.projectId, "wk-p7");
    assertEquals(body.invokeId, invokeId);
    assertEquals(body.functionName, "requestInspector");
    assertEquals(body.contentType, "application/json");
    assertEquals((body.echo as Record<string, unknown>).message, "hello");

    await registry.delete("wk-p7", "requestInspector");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});
