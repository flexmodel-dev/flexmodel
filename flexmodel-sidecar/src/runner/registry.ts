// ============================================================
// Function Registry — In-memory metadata cache + lazy source loading
//
// - deploy() stores metadata only (O(1) memory, no sourceCode)
// - getSourceCode() lazy-loads from Java on first invoke
// - LRU cache (50MB) for source code to prevent unbounded memory
// ============================================================

import type { DeployRequest, FunctionMeta } from "../types.ts";

const JAVA_HOST = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
const JAVA_PORT = parseInt(Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080");
const JAVA_BASE = `http://${JAVA_HOST}:${JAVA_PORT}`;

// ---- LRU Cache (simple implementation) ----

class LRUCache<K, V> {
  private map = new Map<K, V>();
  private maxBytes: number;
  private currentBytes = 0;

  constructor(maxBytes: number) {
    this.maxBytes = maxBytes;
  }

  get(key: K): V | undefined {
    if (!this.map.has(key)) return undefined;
    const value = this.map.get(key)!;
    // Move to end (most recently used)
    this.map.delete(key);
    this.map.set(key, value);
    return value;
  }

  set(key: K, value: V, byteSize: number): void {
    // Evict old entries if needed
    while (this.currentBytes + byteSize > this.maxBytes && this.map.size > 0) {
      const firstKey = this.map.keys().next().value;
      if (firstKey !== undefined) {
        this.map.delete(firstKey);
        this.currentBytes = Math.max(0, this.currentBytes - 1024); // approximate
      }
    }
    if (this.map.has(key)) {
      this.map.delete(key);
    }
    this.map.set(key, value);
    this.currentBytes += byteSize;
  }

  has(key: K): boolean {
    return this.map.has(key);
  }

  delete(key: K): void {
    if (this.map.has(key)) {
      this.map.delete(key);
      this.currentBytes = Math.max(0, this.currentBytes - 1024);
    }
  }
}

// ---- Registry ----

class Registry {
  /** Metadata cache: key = "projectId:name" */
  private meta = new Map<string, FunctionMeta>();

  /** Source code LRU cache: key = "functionId:v{version}", max 50MB */
  private sourceCache = new LRUCache<string, string>(50 * 1024 * 1024);

  /** Get a function's metadata by projectId and name */
  get(projectId: string, name: string): FunctionMeta | undefined {
    return this.meta.get(`${projectId}:${name}`);
  }

  /** Check if a function is registered */
  has(projectId: string, name: string): boolean {
    return this.meta.has(`${projectId}:${name}`);
  }

  /** Deploy: store metadata only (no source code in memory) */
  deploy(req: DeployRequest): void {
    const key = `${req.projectId}:${req.name}`;
    const existing = this.meta.get(key);

    this.meta.set(key, {
      id: req.functionId,
      projectId: req.projectId,
      name: req.name,
      version: req.version,
      entryPoint: req.entryPoint,
      timeout: req.timeout,
      memoryLimit: req.memoryLimit,
      // sourceCode NOT set — lazy loaded on first invoke
    });

    console.log(
      `[registry] Deployed function metadata: ${req.projectId}:${req.name} v${req.version}`,
    );

    // Invalidate source cache on version update
    if (existing && existing.version !== req.version) {
      this.invalidateSourceCache(req.functionId, existing.version);
    }
  }

  /** Delete a function from registry */
  delete(projectId: string, name: string): void {
    const key = `${projectId}:${name}`;
    const meta = this.meta.get(key);
    if (meta) {
      this.invalidateSourceCache(meta.id, meta.version);
    }
    this.meta.delete(key);
    console.log(`[registry] Removed function: ${projectId}:${name}`);
  }

  /** Get source code — lazy loads from Java if not cached */
  async getSourceCode(meta: FunctionMeta): Promise<string> {
    // Already loaded in memory
    if (meta.sourceCode) return meta.sourceCode;

    // Check LRU cache
    const cacheKey = `${meta.id}:v${meta.version}`;
    let code = this.sourceCache.get(cacheKey);
    if (code) {
      meta.sourceCode = code;
      return code;
    }

    // Lazy load from Java
    const url =
      `${JAVA_BASE}/internal/functions/${meta.id}/versions/${meta.version}/source`;
    console.log(`[registry] Lazy loading source: ${cacheKey} from ${url}`);
    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(
        `Failed to load source for ${cacheKey}: HTTP ${res.status}`,
      );
    }
    code = await res.text();
    const byteSize = new TextEncoder().encode(code).length;

    this.sourceCache.set(cacheKey, code, byteSize);
    meta.sourceCode = code;
    return code;
  }

  /** Invalidate cached source code for a specific version */
  private invalidateSourceCache(functionId: string, version: number): void {
    this.sourceCache.delete(`${functionId}:v${version}`);
  }

  /** Get all registered function keys */
  keys(): string[] {
    return [...this.meta.keys()];
  }

  /** Get count of registered functions */
  get size(): number {
    return this.meta.size;
  }
}

// Singleton
export const registry = new Registry();
