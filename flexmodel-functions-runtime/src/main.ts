// ============================================================
// Flexmodel Functions Runtime — Main Entry Point
//
// Starts the Hono.js HTTP server on the configured port.
// Listens for deploy/delete/invoke requests from flexmodel-server.
// ============================================================

import { createApp } from "./server.ts";

const PORT = parseInt(Deno.env.get("FLEXMODEL_PORT") ?? "9999");
const HOSTNAME = Deno.env.get("FLEXMODEL_HOST") ?? "0.0.0.0";

if (import.meta.main) {
  const app = createApp();

  console.log(`[flexmodel-functions-runtime] Starting on http://${HOSTNAME}:${PORT}`);

  Deno.serve(
    {
      port: PORT,
      hostname: HOSTNAME,
      onListen: ({ hostname, port }) => {
        console.log(
          `[flexmodel-functions-runtime] Listening on http://${hostname}:${port}`,
        );
      },
    },
    app.fetch,
  );
}
