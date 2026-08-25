// ============================================================
// Worker Pool — Reuse Workers across invocations
//
// Instead of creating/destroying a Deno Worker for each
// invocation (cold start every time), Workers that complete
// successfully are returned to the pool and reused.
//
// This mirrors how AWS Lambda / Cloudflare Workers keep
// execution environments warm between invocations.
//
// Key design decisions:
// - Only idle Workers are tracked (in-use Workers are "checked out")
// - On error/timeout, the Worker is terminated, not pooled
// - On redeploy, all idle Workers for the function are drained
// - Pool size capped per function to bound memory usage
// - warmup() pre-loads user code (npm deps) into Workers at deploy time
// ============================================================

import type {FunctionMeta} from "../types.ts";

const DEFAULT_MAX_IDLE_PER_FUNCTION = 3;

export class WorkerPool {
    // key: "projectId:name" → idle workers ready to use
    private idle = new Map<string, Worker[]>();
    private maxIdle: number;

    constructor(maxIdlePerFunction = DEFAULT_MAX_IDLE_PER_FUNCTION) {
        this.maxIdle = maxIdlePerFunction;
    }

    /**
     * Try to get an idle Worker for the given function.
     * Returns null if none available — caller must create a new one.
     */
    acquire(meta: FunctionMeta): Worker | null {
        const key = this.key(meta);
        const pool = this.idle.get(key);
        if (pool && pool.length > 0) {
            return pool.pop()!;
        }
        return null;
    }

    /**
     * Return a Worker to the pool after a successful invocation.
     * If the pool is full, terminate the Worker instead.
     */
    release(meta: FunctionMeta, worker: Worker): void {
        const key = this.key(meta);
        let pool = this.idle.get(key);
        if (!pool) {
            pool = [];
            this.idle.set(key, pool);
        }
        if (pool.length < this.maxIdle) {
            pool.push(worker);
        } else {
            worker.terminate();
        }
    }

    /**
     * Create a new Deno Worker for the given function.
     * Centralized factory to avoid duplicating permission config.
     */
    createWorker(meta: FunctionMeta): Worker {
        return new Worker(meta.entryUrl, {
            type: "module",
            deno: {
                permissions: {
                    net: true,
                    read: [meta.functionDir],
                    write: false,
                    env: true,
                    run: false,
                    ffi: false,
                },
            },
        });
    }

    /**
     * Pre-warm Workers at deploy time: create Workers and trigger
     * user-code import (including all npm dependencies) so the first
     * real invocation hits a fully-loaded Worker.
     *
     * Sends a "warmup" message that causes the wrapper to
     * `await import("./index.ts")` — resolving npm:openai etc.
     * Once the import succeeds, the Worker is returned to the pool.
     */
    warmup(meta: FunctionMeta, count = 1): Promise<void> {
        const promises = Array.from({length: count}, () => {
            return new Promise<void>((resolve) => {
                let worker: Worker;
                try {
                    worker = this.createWorker(meta);
                } catch {
                    resolve(); // Worker creation failed — non-fatal
                    return;
                }

                const timeout = setTimeout(() => {
                    try {
                        worker.terminate();
                    } catch { /* ignore */
                    }
                    resolve(); // Timeout — non-fatal, first invocation will cold-start
                }, 15_000);

                const warmupStart = performance.now();

                worker.onmessage = (e: MessageEvent) => {
                    const {type} = e.data;
                    if (type === "warmed_up") {
                        clearTimeout(timeout);
                        const elapsed = Math.round(performance.now() - warmupStart);
                        console.log(`[worker_pool] Warmup complete for ${this.key(meta)} in ${elapsed}ms`);
                        this.release(meta, worker);
                        resolve();
                    } else if (type === "warmup_error") {
                        clearTimeout(timeout);
                        console.warn(`[worker_pool] Warmup import failed for ${this.key(meta)}: ${e.data?.data?.message ?? "unknown"}`);
                        try {
                            worker.terminate();
                        } catch { /* ignore */
                        }
                        resolve(); // Non-fatal — first invocation will retry the import
                    }
                };

                worker.onerror = () => {
                    clearTimeout(timeout);
                    try {
                        worker.terminate();
                    } catch { /* ignore */
                    }
                    resolve();
                };

                // Trigger warmup: this causes the wrapper to import user code
                worker.postMessage({type: "warmup"});
            });
        });

        return Promise.all(promises).then(() => {
        });
    }

    /**
     * Terminate all idle Workers for a function.
     * Called when the function is redeployed or deleted to ensure
     * new invocations pick up the updated code.
     */
    drain(projectId: string, name: string): void {
        const key = `${projectId}:${name}`;
        const pool = this.idle.get(key);
        if (pool) {
            for (const worker of pool) {
                try {
                    worker.terminate();
                } catch { /* ignore */
                }
            }
            this.idle.delete(key);
        }
    }

    /**
     * Terminate all pooled Workers across all functions.
     */
    drainAll(): void {
        for (const [, pool] of this.idle) {
            for (const worker of pool) {
                try {
                    worker.terminate();
                } catch { /* ignore */
                }
            }
        }
        this.idle.clear();
    }

    /** Get pool statistics for monitoring. */
    stats(): { function: string; idle: number }[] {
        return Array.from(this.idle.entries()).map(([key, pool]) => ({
            function: key,
            idle: pool.length,
        }));
    }

    private key(meta: FunctionMeta): string {
        return `${meta.projectId}:${meta.name}`;
    }
}

export const workerPool = new WorkerPool();
