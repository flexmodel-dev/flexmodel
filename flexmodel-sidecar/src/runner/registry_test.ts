// ============================================================
// Registry — Tests
// ============================================================

import { assertEquals, assertExists, assertRejects } from "@std/assert";
import { registry } from "./registry.ts";
import {
  cleanupTempDirs,
  makeTempDir,
  restoreEnv,
  setEnv,
} from "../test_helpers.ts";

Deno.test("Registry.deploy writes source files and wrapper", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await registry.deploy({
      projectId: "reg-p1",
      functionId: "f1",
      name: "foo",
      sourceFiles: { "index.ts": "export default () => {};" },
      timeout: 10,
    });

    const meta = registry.get("reg-p1", "foo");
    assertExists(meta);
    assertEquals(meta!.name, "foo");

    // verify wrapper file exists on disk
    const wrapperPath = meta!.functionDir + "/_worker_wrapper.ts";
    const info = await Deno.stat(wrapperPath);
    assertEquals(info.isFile, true);

    await registry.delete("reg-p1", "foo");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("Registry.deploy rejects filenames with subdirectories", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await assertRejects(
      () =>
        registry.deploy({
          projectId: "reg-p1b",
          functionId: "f1b",
          name: "bad",
          sourceFiles: { "sub/index.ts": "export default () => {};" },
          timeout: 10,
        }),
      Error,
      "Subdirectories are not supported",
    );
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("Registry.has and .get return correct values", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    assertEquals(registry.has("reg-p2", "missing"), false);
    assertEquals(registry.get("reg-p2", "missing"), undefined);

    await registry.deploy({
      projectId: "reg-p2",
      functionId: "f2",
      name: "exists",
      sourceFiles: { "index.ts": "export default () => {};" },
      timeout: 10,
    });

    assertEquals(registry.has("reg-p2", "exists"), true);
    assertExists(registry.get("reg-p2", "exists"));

    await registry.delete("reg-p2", "exists");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("Registry.delete removes metadata and directory", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await registry.deploy({
      projectId: "reg-p3",
      functionId: "f3",
      name: "doomed",
      sourceFiles: { "index.ts": "export default () => {};" },
      timeout: 10,
    });

    const meta = registry.get("reg-p3", "doomed")!;
    await registry.delete("reg-p3", "doomed");

    assertEquals(registry.has("reg-p3", "doomed"), false);

    // directory should be gone
    let exists = true;
    try {
      await Deno.stat(meta.functionDir);
    } catch {
      exists = false;
    }
    assertEquals(exists, false);
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("Registry.size reflects current count", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    const before = registry.size;

    await registry.deploy({
      projectId: "reg-p4",
      functionId: "f4",
      name: "sizer",
      sourceFiles: { "index.ts": "export default () => {};" },
      timeout: 10,
    });

    assertEquals(registry.size, before + 1);

    await registry.delete("reg-p4", "sizer");
    assertEquals(registry.size, before);
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});

Deno.test("Registry deploy can overwrite same key (redeploy)", async () => {
  const tempDir = makeTempDir();
  setEnv("FUNCTIONS_DIR", tempDir);

  try {
    await registry.deploy({
      projectId: "reg-p5",
      functionId: "f5",
      name: "double",
      sourceFiles: { "index.ts": "// v1" },
      timeout: 10,
    });

    await registry.deploy({
      projectId: "reg-p5",
      functionId: "f5-new",
      name: "double",
      sourceFiles: { "index.ts": "// v2" },
      timeout: 20,
    });

    const meta = registry.get("reg-p5", "double")!;
    assertEquals(meta.timeout, 20);

    // wrapper should reflect new code
    const content = await Deno.readTextFile(meta.functionDir + "/index.ts");
    assertEquals(content, "// v2");

    await registry.delete("reg-p5", "double");
  } finally {
    await cleanupTempDirs();
    restoreEnv();
  }
});
