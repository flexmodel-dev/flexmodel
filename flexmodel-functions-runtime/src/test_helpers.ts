// ============================================================
// Flexmodel Functions Runtime — Shared Test Utilities
//
// Helpers for mocking env vars, global fetch, and temp dirs.
// All mocks are restored in finally blocks to avoid cross-test leaks.
// ============================================================

// ---- Environment Variable Mocking ----

const envBackup = new Map<string, string | undefined>();

/** Set an env var, remembering its previous value for later restoration. */
export function setEnv(key: string, value: string): void {
  if (!envBackup.has(key)) {
    envBackup.set(key, Deno.env.get(key));
  }
  Deno.env.set(key, value);
}

/** Restore all env vars to their pre-test state. */
export function restoreEnv(): void {
  for (const [key, value] of envBackup) {
    if (value === undefined) {
      Deno.env.delete(key);
    } else {
      Deno.env.set(key, value);
    }
  }
  envBackup.clear();
}

// ---- fetch Mocking ----

export type FetchHandler = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response> | Response;

let originalFetch: typeof globalThis.fetch | undefined;

/** Replace global fetch with a mock handler. */
export function mockFetch(handler: FetchHandler): void {
  originalFetch = globalThis.fetch;
  globalThis.fetch = handler as typeof globalThis.fetch;
}

/** Restore the original global fetch. */
export function restoreFetch(): void {
  if (originalFetch !== undefined) {
    globalThis.fetch = originalFetch;
    originalFetch = undefined;
  }
}

// ---- Temporary Directory Helpers ----

const tempDirs: string[] = [];

/** Create a temp directory and track it for cleanup. */
export function makeTempDir(): string {
  const dir = Deno.makeTempDirSync();
  tempDirs.push(dir);
  return dir;
}

/** Remove all tracked temp directories. */
export async function cleanupTempDirs(): Promise<void> {
  await Promise.all(
    tempDirs.map((dir) =>
      Deno.remove(dir, { recursive: true }).catch(() => {})
    ),
  );
  tempDirs.length = 0;
}
