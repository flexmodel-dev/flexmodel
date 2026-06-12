# Flexmodel Sidecar — Deno Runtime
# Uses host.docker.internal to reach the Java server on the host machine.
FROM denoland/deno:2.3.11

WORKDIR /app

# Cache dependencies first (layer optimization)
COPY deno.json .
RUN deno cache deno.json

# Copy source code
COPY src/ src/

# Pre-cache all TypeScript dependencies
RUN deno cache src/main.ts

EXPOSE 9999

# Default env: Java server on Docker host
ENV FLEXMODEL_JAVA_HOST=host.docker.internal
ENV FLEXMODEL_JAVA_PORT=8080
ENV FLEXMODEL_PORT=9999
ENV FLEXMODEL_HOST=0.0.0.0

CMD ["run", "--allow-net=host.docker.internal", "--allow-env", "src/main.ts"]
