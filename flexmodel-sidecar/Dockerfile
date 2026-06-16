# Flexmodel Sidecar — Deno Runtime
# Communicates with flexmodel-server over the Docker network.
# In docker-compose, set FLEXMODEL_JAVA_HOST=flexmodel (the service name).
FROM denoland/deno:2.8.3

WORKDIR /app

# Cache dependencies first (layer optimization)
COPY deno.json deno.lock ./
RUN deno cache deno.json

# Copy source code
COPY src/ src/

# Pre-cache all TypeScript dependencies
RUN deno cache src/main.ts

EXPOSE 9999

ENV FLEXMODEL_JAVA_HOST=flexmodel
ENV FLEXMODEL_JAVA_PORT=8080
ENV FLEXMODEL_PORT=9999
ENV FLEXMODEL_HOST=0.0.0.0

# --allow-net without restriction; network isolation handled at container level.
# --allow-env needed for Deno.env access.
# --allow-read=./ --allow-write=./ limit FS access to the app directory.
CMD ["run", "--allow-net", "--allow-env", "--allow-read=./", "--allow-write=./", "--unstable-worker-options", "src/main.ts"]
