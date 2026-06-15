// ============================================================
// Worker Executor — Integration Tests
//
// These tests create actual Deno Workers to exercise the full
// pipeline: invokeFunction -> Worker -> wrapper -> user module.
// Requires --unstable-worker-options flag.
// ============================================================

import { assertEquals, assertRejects } from "@std/assert";
import { invokeFunction } from "./worker.ts";
import { registry } from "./registry.ts";
import {
  cleanupTempDirs,
  makeTempDir,
  mockFetch,
  restoreEnv,
  restoreFetch,
  setEnv,
} from "../test_helpers.ts";

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

    const result = await invokeFunction("wk-p1", "jsonFn", {
      method: "POST",
      headers: {},
      body: {},
    });

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

    const result = await invokeFunction("wk-p2", "responseFn", {
      method: "GET",
      headers: {},
    });

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
        export default async () => {
          await new Promise(r => setTimeout(r, 10000));
          return { ok: true };
        };
      `,
      1, // 1 second timeout
    );

    await assertRejects(
      () =>
        invokeFunction("wk-p3", "slow", { method: "POST", headers: {} }),
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
        export default async () => {
          throw new Error("intentional boom");
        };
      `,
    );

    await assertRejects(
      () =>
        invokeFunction("wk-p4", "boom", { method: "POST", headers: {} }),
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
      `export default () => ({ok:true});`,
    );

    // Manually nuke directory to simulate corruption
    const meta = registry.get("wk-p5", "ghost")!;
    await Deno.remove(meta.functionDir, { recursive: true });

    await assertRejects(
      () =>
        invokeFunction("wk-p5", "ghost", { method: "POST", headers: {} }),
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

Deno.test("invokeFunction proxies SDK requests to Java backend", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  mockFetch((input, init) => {
    const req = new Request(input, init);
    if (req.url.includes("/api/data/User")) {
      return Promise.resolve(
        new Response(JSON.stringify({ items: [{ id: "u1" }] }), {
          headers: { "content-type": "application/json" },
        }),
      );
    }
    return Promise.resolve(new Response("{}", { status: 404 }));
  });

  try {
    await deployTestFunction(
      "wk-p6",
      "sdkUser",
      `
        export default async (req: Request, ctx: any) => {
          const users = await ctx.flexmodel.data.find("User", { page: 1, size: 10 });
          return { users };
        };
      `,
    );

    const result = await invokeFunction("wk-p6", "sdkUser", {
      method: "POST",
      headers: {},
    });
    assertEquals(result.status, 200);
    const body = result.body as Record<string, unknown>;
    assertEquals(body.users !== undefined, true);

    await registry.delete("wk-p6", "sdkUser");
  } finally {
    restoreFetch();
    await cleanupTempDirs();
    restoreEnv();
  }
});
