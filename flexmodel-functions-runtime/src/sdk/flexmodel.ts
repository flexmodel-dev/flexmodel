// ============================================================
// Flexmodel SDK — Main Process RPC Dispatcher
//
// Routes SDK requests from Worker (via postMessage) to the
// Java-side REST API and returns results back.
// ============================================================

import type { BatchOp } from "../types.ts";

const JAVA_HOST = Deno.env.get("FLEXMODEL_JAVA_HOST") ?? "localhost";
const JAVA_PORT = parseInt(Deno.env.get("FLEXMODEL_JAVA_PORT") ?? "8080");
const JAVA_BASE = `http://${JAVA_HOST}:${JAVA_PORT}`;

// ---- Internal Token（从 invoke body 提取，全局共享） ----

let _internalToken = "";

export function setInternalToken(token: string): void {
  _internalToken = token;
}

function getInternalToken(): string {
  return _internalToken;
}

// ---- RPC Handler: Dispatch an operation to the Java API ----

export async function handleRpcRequest(
  operation: string,
  params: unknown,
  projectId?: string,
): Promise<unknown> {
  const [service, action] = operation.split(".");
  if (!service || !action) {
    throw new Error(`Invalid operation: ${operation}`);
  }

  switch (service) {
    case "data":
      return handleDataRpc(action, params, projectId);
    // Future: case "functions", case "storage", case "auth"
    default:
      throw new Error(`Unknown RPC service: ${service}`);
  }
}

// ---- Data Service RPC ----

async function handleDataRpc(
  action: string,
  params: unknown,
  projectId?: string,
): Promise<unknown> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  const token = getInternalToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const base = (model: string) =>
    `${JAVA_BASE}/api/projects/${projectId}/models/${model}/records`;

  switch (action) {
    case "find": {
      const { model, params: queryParams } = params as {
        model: string;
        params: Record<string, unknown>;
      };
      const searchParams = new URLSearchParams();
      if (queryParams?.page) searchParams.set("page", String(queryParams.page));
      if (queryParams?.size) searchParams.set("size", String(queryParams.size));
      if (queryParams?.filter) searchParams.set("filter", JSON.stringify(queryParams.filter));
      if (queryParams?.sort) searchParams.set("sort", JSON.stringify(queryParams.sort));
      const res = await fetch(
        `${base(model)}?${searchParams.toString()}`,
        { headers },
      );
      return res.json();
    }

    case "findOne": {
      const { model, id } = params as { model: string; id: string };
      const res = await fetch(`${base(model)}/${id}`, { headers });
      return res.json();
    }

    case "create": {
      const { model, data } = params as { model: string; data: unknown };
      const res = await fetch(base(model), {
        method: "POST",
        headers,
        body: JSON.stringify(data),
      });
      return res.json();
    }

    case "update": {
      const { model, id, data } = params as {
        model: string;
        id: string;
        data: unknown;
      };
      const res = await fetch(`${base(model)}/${id}`, {
        method: "PUT",
        headers,
        body: JSON.stringify(data),
      });
      return res.json();
    }

    case "delete": {
      const { model, id } = params as { model: string; id: string };
      const res = await fetch(`${base(model)}/${id}`, {
        method: "DELETE",
        headers,
      });
      return res.ok ? { success: true } : res.json();
    }

    case "batch": {
      const { operations } = params as { operations: BatchOp[] };
      const results: unknown[] = [];
      for (const op of operations) {
        try {
          const result = await handleDataRpc(op.op, {
            model: op.model,
            ...op.params,
          } as unknown, projectId);
          results.push(result);
        } catch (err: unknown) {
          results.push({
            error: err instanceof Error ? err.message : String(err),
          });
        }
      }
      return results;
    }

    default:
      throw new Error(`Unknown data operation: ${action}`);
  }
}
