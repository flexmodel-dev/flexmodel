package dev.flexmodel.functions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.codegen.entity.Function;
import dev.flexmodel.codegen.entity.FunctionVersion;
import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.codegen.enumeration.TriggerType;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Function lifecycle management: CRUD, state machine, invocation, startup recovery.
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FunctionService {

    @Inject
    FunctionRepository functionRepository;

    @Inject
    FunctionVersionRepository versionRepository;

    @Inject
    dev.flexmodel.scheduling.TriggerRepository triggerRepository;

    @Inject
    FunctionInvoker functionInvoker;

    @Inject
    ObjectMapper objectMapper;

    // ---- Status Constants ----

    static final String STATUS_CREATING = "CREATING";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_DEPLOY_FAILED = "DEPLOY_FAILED";
    static final String STATUS_UPDATING = "UPDATING";
    static final String STATUS_DELETING = "DELETING";
    static final String STATUS_INACTIVE = "INACTIVE";

    static final String JOB_TYPE_FUNCTION = "FUNCTION";

    // ============================================================
    // CRUD Operations
    // ============================================================

    /** Create a function: save to DB (CREATING) → save version v1 → deploy metadata → ACTIVE / DEPLOY_FAILED */
    public FunctionResponse create(String projectId, FunctionCreateRequest req) {
        // 1. Save function entity with CREATING status
        Function fn = new Function();
        fn.setProjectId(projectId);
        fn.setName(req.getName());
        fn.setSlug(req.getSlug());
        fn.setDescription(req.getDescription());
        fn.setEntryPoint(req.getEntryPoint() != null ? req.getEntryPoint() : "default");
        fn.setTimeout(req.getTimeout() != null ? req.getTimeout() : 30);
        fn.setMemoryLimit(req.getMemoryLimit() != null ? req.getMemoryLimit() : 128);
        fn.setStatus(STATUS_CREATING);
        fn.setCurrentVersion(1);
        fn.setCreatedAt(LocalDateTime.now());
        fn.setUpdatedAt(LocalDateTime.now());
        functionRepository.save(projectId, fn);
        log.info("Function created: {}:{} (CREATING)", projectId, req.getSlug());

        // 2. Save version v1
        FunctionVersion version = new FunctionVersion();
        version.setFunctionId(fn.getId());
        version.setVersion(1);
        version.setSourceCode(req.getSourceCode());
        version.setCreatedAt(LocalDateTime.now());
        versionRepository.save(projectId, version);
        log.info("Function version saved: {}:{} v1", projectId, req.getSlug());

        // 3. Create HTTP trigger if path is specified
        if (req.getTriggerPath() != null && !req.getTriggerPath().isBlank()) {
            createHttpTrigger(projectId, fn, req.getTriggerPath(),
                req.getTriggerMethod() != null ? req.getTriggerMethod() : "POST",
                req.getAuthMode() != null ? req.getAuthMode() : "PUBLIC");
        }

        // 4. Deploy metadata to Deno sidecar
        try {
            functionInvoker.deploy(fn, 1);
            fn.setStatus(STATUS_ACTIVE);
            log.info("Function deployed successfully: {}:{}", projectId, req.getSlug());
        } catch (Exception e) {
            fn.setStatus(STATUS_DEPLOY_FAILED);
            log.error("Deploy failed for function: {}:{}", projectId, req.getSlug(), e);
        }
        fn.setUpdatedAt(LocalDateTime.now());
        functionRepository.save(projectId, fn);

        return FunctionResponse.from(fn);
    }

    /** Update a function: increment version, redeploy metadata */
    public FunctionResponse update(String projectId, String slug, FunctionUpdateRequest req) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }

        fn.setStatus(STATUS_UPDATING);
        fn.setUpdatedAt(LocalDateTime.now());
        functionRepository.save(projectId, fn);

        int newVersion = (fn.getCurrentVersion() != null ? fn.getCurrentVersion() : 1) + 1;

        if (req.getEntryPoint() != null) {
            fn.setEntryPoint(req.getEntryPoint());
        }
        if (req.getTimeout() != null) {
            fn.setTimeout(req.getTimeout());
        }
        if (req.getMemoryLimit() != null) {
            fn.setMemoryLimit(req.getMemoryLimit());
        }
        if (req.getDescription() != null) {
            fn.setDescription(req.getDescription());
        }

        FunctionVersion version = new FunctionVersion();
        version.setFunctionId(fn.getId());
        version.setVersion(newVersion);
        version.setSourceCode(req.getSourceCode());
        version.setCreatedAt(LocalDateTime.now());
        versionRepository.save(projectId, version);
        log.info("Function version saved: {}:{} v{}", projectId, slug, newVersion);

        try {
            functionInvoker.deploy(fn, newVersion);
            fn.setCurrentVersion(newVersion);
            fn.setStatus(STATUS_ACTIVE);
            log.info("Function updated successfully: {}:{} v{}", projectId, slug, newVersion);
        } catch (Exception e) {
            fn.setStatus(STATUS_DEPLOY_FAILED);
            log.error("Deploy failed for function update: {}:{}", projectId, slug, e);
        }
        fn.setUpdatedAt(LocalDateTime.now());
        functionRepository.save(projectId, fn);

        return FunctionResponse.from(fn);
    }

    /** Rollback to a specific version */
    public FunctionResponse rollback(String projectId, String slug, int targetVersion) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }

        // Verify the target version exists
        FunctionVersion version = versionRepository.findByFunctionAndVersion(fn.getId(), targetVersion);
        if (version == null) {
            throw new FunctionException("Version not found: v" + targetVersion);
        }

        fn.setStatus(STATUS_UPDATING);
        functionRepository.save(projectId, fn);

        try {
            functionInvoker.deploy(fn, targetVersion);
            fn.setCurrentVersion(targetVersion);
            fn.setStatus(STATUS_ACTIVE);
            log.info("Function rolled back: {}:{} to v{}", projectId, slug, targetVersion);
        } catch (Exception e) {
            fn.setStatus(STATUS_DEPLOY_FAILED);
            log.error("Rollback failed for function: {}:{}", projectId, slug, e);
        }
        fn.setUpdatedAt(LocalDateTime.now());
        functionRepository.save(projectId, fn);

        return FunctionResponse.from(fn);
    }

    /** Delete a function: DELETING → delete from sidecar → delete DB records */
    public void delete(String projectId, String slug) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }

        fn.setStatus(STATUS_DELETING);
        functionRepository.save(projectId, fn);

        // Delete from Deno sidecar
        functionInvoker.delete(projectId, slug);

        // Delete function triggers
        List<Trigger> triggers = findFunctionTriggers(projectId, fn.getId());
        for (Trigger trigger : triggers) {
            triggerRepository.deleteById(projectId, trigger.getId());
        }

        // Delete versions
        versionRepository.deleteByFunctionId(projectId, fn.getId());

        // Delete function
        functionRepository.deleteById(projectId, fn.getId());
        log.info("Function deleted: {}:{}", projectId, slug);
    }

    /** Get function detail */
    public FunctionResponse findById(String projectId, String slug) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }
        FunctionResponse response = FunctionResponse.from(fn);
        response.setTriggers(getTriggerRefs(projectId, fn.getId()));
        return response;
    }

    /** List functions with pagination */
    public PageDTO<FunctionResponse> findPage(String projectId, FunctionPageRequest request) {
        Predicate filter = Expressions.TRUE;
        if (request.getName() != null && !request.getName().isBlank()) {
            filter = filter.and(Expressions.field(Function::getName).eq(request.getName()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            filter = filter.and(Expressions.field(Function::getStatus).eq(request.getStatus()));
        }

        long total = functionRepository.count(projectId, filter);
        if (total == 0) {
            return PageDTO.empty();
        }

        List<FunctionResponse> list = functionRepository.find(projectId, filter, request.getPage(), request.getSize())
            .stream()
            .map(fn -> {
                FunctionResponse resp = FunctionResponse.from(fn);
                resp.setTriggers(getTriggerRefs(projectId, fn.getId()));
                return resp;
            })
            .toList();

        return new PageDTO<>(list, total);
    }

    /** List versions for a function */
    public List<FunctionVersionResponse> listVersions(String projectId, String slug) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }
        return versionRepository.findByFunctionId(projectId, fn.getId())
            .stream()
            .map(FunctionVersionResponse::from)
            .toList();
    }

    // ============================================================
    // Invocation
    // ============================================================

    /** Invoke a function: find trigger → validate auth → forward to Deno */
    public FunctionInvokeResponse invoke(String projectId, String slug,
                                          FunctionInvokeRequest req,
                                          String authHeader) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }
        if (!STATUS_ACTIVE.equals(fn.getStatus())) {
            throw new FunctionException("Function is not active: " + fn.getStatus());
        }

        // Find HTTP triggers for this function
        List<Trigger> triggers = findFunctionTriggers(projectId, fn.getId());
        if (triggers.isEmpty()) {
            throw new FunctionException("No HTTP trigger configured for function: " + slug);
        }

        // Find matching trigger by method
        Trigger trigger = triggers.stream()
            .filter(t -> t.getConfig() != null)
            .filter(t -> {
                Map<String, Object> cfg = parseTriggerConfig(t.getConfig());
                String method = (String) cfg.get("method");
                return method == null || method.equalsIgnoreCase(req.getMethod());
            })
            .findFirst()
            .orElse(triggers.getFirst());

        // Validate auth mode
        validateAuthMode(trigger, authHeader);

        // Invoke via Deno sidecar
        FunctionInvokeResponse response = functionInvoker.invoke(projectId, slug, req);

        // Log execution metrics
        if (response.getMeta() != null) {
            log.info("Function {} executed in {}ms", slug, response.getMeta().getExecutionTimeMs());
            if (response.getMeta().getLogs() != null) {
                for (FunctionInvokeResponse.LogEntry entry : response.getMeta().getLogs()) {
                    log.info("[fn:{}][{}] {}", slug, entry.getLevel(), entry.getMessage());
                }
            }
        }

        return response;
    }

    // ============================================================
    // Internal API (for sidecar lazy load)
    // ============================================================

    /** Get source code for a specific version (called by Deno sidecar for lazy loading) */
    public String getSourceCode(String functionId, int version) {
        FunctionVersion v = versionRepository.findByFunctionAndVersion(functionId, version);
        if (v == null) {
            throw new FunctionException("Version not found: functionId=" + functionId + " v" + version);
        }
        return v.getSourceCode();
    }

    // ============================================================
    // Trigger Management
    // ============================================================

    /** List HTTP triggers for a function */
    public List<Trigger> listTriggers(String projectId, String slug) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }
        return findFunctionTriggers(projectId, fn.getId());
    }

    /** Add an HTTP trigger to a function */
    public Trigger addTrigger(String projectId, String slug, Trigger trigger) {
        Function fn = functionRepository.findBySlug(projectId, slug);
        if (fn == null) {
            throw new FunctionException("Function not found: " + slug);
        }
        trigger.setProjectId(projectId);
        trigger.setType(TriggerType.HTTP);
        trigger.setJobType(JOB_TYPE_FUNCTION);
        trigger.setJobId(fn.getId());
        trigger.setJobGroup(slug);
        trigger.setCreatedAt(LocalDateTime.now());
        trigger.setUpdatedAt(LocalDateTime.now());
        return triggerRepository.save(projectId, trigger);
    }

    /** Update an HTTP trigger */
    public Trigger updateTrigger(String projectId, String slug, String triggerId, Trigger updated) {
        Trigger existing = triggerRepository.findById(projectId, triggerId);
        if (existing == null) {
            throw new FunctionException("Trigger not found: " + triggerId);
        }
        updated.setId(triggerId);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());
        updated.setProjectId(projectId);
        updated.setType(TriggerType.HTTP);
        updated.setJobType(JOB_TYPE_FUNCTION);
        updated.setJobGroup(slug);
        return triggerRepository.save(projectId, updated);
    }

    /** Delete a trigger */
    public void deleteTrigger(String projectId, String slug, String triggerId) {
        triggerRepository.deleteById(projectId, triggerId);
    }

    // ============================================================
    // Startup Recovery
    // ============================================================

    /** On startup: sync metadata of all ACTIVE functions to Deno sidecar */
    void onStart(@Observes StartupEvent event) {
        log.info("Starting function recovery: syncing ACTIVE functions to sidecar...");
        int success = 0;
        int failed = 0;

        // Recover from the "dev_test" project (default project)
        String projectId = "dev_test";
        try {
            List<Function> functions = functionRepository.findByStatus(projectId, STATUS_ACTIVE);
            for (Function fn : functions) {
                try {
                    functionInvoker.deploy(fn);
                    success++;
                } catch (Exception e) {
                    fn.setStatus(STATUS_DEPLOY_FAILED);
                    functionRepository.save(projectId, fn);
                    failed++;
                    log.error("Startup deploy failed: {}:{}", projectId, fn.getSlug(), e);
                }
            }
            log.info("Function recovery complete: {} success, {} failed (metadata only, lazy sourceCode)", success, failed);
        } catch (Exception e) {
            log.warn("Function startup recovery encountered an error (this may be expected if the sidecar is not running)", e);
        }
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /** Create an HTTP trigger record in f_trigger table */
    private void createHttpTrigger(String projectId, Function fn, String path, String method, String authMode) {
        Trigger trigger = new Trigger();
        trigger.setName("HTTP Trigger for " + fn.getSlug());
        trigger.setDescription("Auto-created HTTP trigger for function: " + fn.getSlug());
        trigger.setType(TriggerType.HTTP);
        trigger.setJobType(JOB_TYPE_FUNCTION);
        trigger.setJobId(fn.getId());
        trigger.setJobGroup(fn.getSlug());
        trigger.setState(true);
        trigger.setProjectId(projectId);
        trigger.setCreatedAt(LocalDateTime.now());
        trigger.setUpdatedAt(LocalDateTime.now());

        // Build config JSON
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("auth_mode", authMode);
        config.put("method", method);
        config.put("path", path);
        trigger.setConfig(config);

        triggerRepository.save(projectId, trigger);
        log.info("HTTP trigger created for function {}: method={} path={} auth={}",
            fn.getSlug(), method, path, authMode);
    }

    /** Find HTTP triggers associated with a function */
    private List<Trigger> findFunctionTriggers(String projectId, String functionId) {
        Predicate filter = Expressions.field(Trigger::getJobId).eq(functionId)
            .and(Expressions.field(Trigger::getJobType).eq(JOB_TYPE_FUNCTION));
        return triggerRepository.find(projectId, filter, 1, 100);
    }

    /** Build trigger reference DTOs */
    private List<FunctionResponse.TriggerRef> getTriggerRefs(String projectId, String functionId) {
        return findFunctionTriggers(projectId, functionId).stream()
            .map(t -> {
                Map<String, Object> cfg = parseTriggerConfig(t.getConfig());
                return FunctionResponse.TriggerRef.builder()
                    .id(t.getId())
                    .path((String) cfg.get("path"))
                    .method((String) cfg.get("method"))
                    .authMode((String) cfg.get("auth_mode"))
                    .enabled(t.getState() != null && t.getState())
                    .build();
            })
            .toList();
    }

    /** Parse trigger config object to Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTriggerConfig(Object config) {
        if (config == null) {
            return Collections.emptyMap();
        }
        if (config instanceof Map) {
            return (Map<String, Object>) config;
        }
        try {
            return objectMapper.convertValue(config, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse trigger config: {}", config, e);
            return Collections.emptyMap();
        }
    }

    /** Validate auth mode based on trigger config */
    private void validateAuthMode(Trigger trigger, String authHeader) {
        Map<String, Object> cfg = parseTriggerConfig(trigger.getConfig());
        String authMode = (String) cfg.getOrDefault("auth_mode", "PUBLIC");

        switch (authMode) {
            case "PUBLIC" -> { /* No auth required */ }
            case "JWT" -> {
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    throw new FunctionException("JWT token required");
                }
                // V2: validate JWT token
            }
            case "API_KEY" -> {
                if (authHeader == null || authHeader.isBlank()) {
                    throw new FunctionException("API Key required");
                }
                // V2: validate API Key
            }
            case "INTERNAL" -> {
                // V1: Internal calls are trusted (coming from within the server)
                log.debug("Internal invocation for trigger: {}", trigger.getId());
            }
            default -> throw new FunctionException("Unknown auth_mode: " + authMode);
        }
    }
}
