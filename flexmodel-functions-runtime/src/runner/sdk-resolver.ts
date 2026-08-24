// ============================================================
// SDK Resolver — locate @flexmodel/sdk for isolated Workers
//
// Workers run user code + the Flexmodel SDK through a function-scoped
// import map. Pointing that map at a locally built copy of the SDK
// (instead of an npm specifier) keeps Workers offline and immune to
// npm registry flakiness such as tarball checksum mismatches on
// mirrors, and removes the cold-start network download.
//
// Resolution order:
//   1. FLEXMODEL_SDK_PATH env var — absolute path to the SDK JS file
//   2. repo-relative default      — ../../../flexmodel-sdks/typescript/dist/index.js
//   3. npm fallback               — npm:@flexmodel/sdk@<version>
// ============================================================

const NPM_FALLBACK = "npm:@flexmodel/sdk@0.0.8";
// Relative to this module (flexmodel-functions-runtime/src/runner/),
// three levels up reach the repo root.
const REPO_DEFAULT = "../../../flexmodel-sdks/typescript/dist/index.js";

export interface SdkResolution {
    /** Import-map specifier written into each function's deno.json. */
    specifier: string;
    /** Filesystem path the Worker needs read permission on (undefined for npm). */
    readPath?: string;
}

function toFileUrl(absPath: string): string {
    return "file:///" + absPath.replace(/\\/g, "/");
}

function resolveSdk(): SdkResolution {
    // 1. Explicit env override (absolute filesystem path).
    const envPath = Deno.env.get("FLEXMODEL_SDK_PATH");
    if (envPath) {
        try {
            const abs = Deno.realPathSync(envPath);
            return {specifier: toFileUrl(abs), readPath: abs};
        } catch {
            // not found / not readable — fall through to next candidate
        }
    }

    // 2. Repo-relative default, resolved against this module's location
    //    (not CWD) so it is reproducible regardless of where tests run from.
    try {
        const url = new URL(REPO_DEFAULT, import.meta.url);
        const abs = Deno.realPathSync(url);
        return {specifier: toFileUrl(abs), readPath: abs};
    } catch {
        // SDK dist not built / not co-located — fall through to npm
    }

    // 3. npm fallback (requires network at Worker load time).
    return {specifier: NPM_FALLBACK};
}

export const sdkResolution: SdkResolution = resolveSdk();
