package dev.flexmodel.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.codegen.entity.Function;
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
import java.util.List;
import java.util.Map;

/**
 * Function lifecycle management: CRUD, deploy to Deno sidecar, invoke, startup recovery.
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FunctionService {

    @Inject
    FunctionRepository functionRepository;

    @Inject
    FunctionInvoker functionInvoker;

    @Inject
    ObjectMapper objectMapper;

    // ============================================================
    // CRUD Operations
    // ============================================================

    /** Deploy (upsert) a function: save DB → deploy to Deno */
    public FunctionResponse deploy(String projectId, String name, FunctionDeployRequest req) {
        Function fn = functionRepository.findByName(projectId, name);
        boolean isNew = (fn == null);

        if (isNew) {
            fn = new Function();
            fn.setProjectId(projectId);
            fn.setName(name);
            fn.setTimeout(req.getTimeout() != null ? req.getTimeout() : 30);
            fn.setCreatedAt(LocalDateTime.now());
        } else {
            if (req.getName() != null && !req.getName().isBlank()) {
                fn.setName(req.getName());
            }
            if (req.getTimeout() != null) {
                fn.setTimeout(req.getTimeout());
            }
        }

        // Serialize sourceFiles to JSON string
        try {
            fn.setSourceFiles(objectMapper.writeValueAsString(req.getSourceFiles()));
        } catch (JsonProcessingException e) {
            throw new FunctionException("Failed to serialize source files", e);
        }
        fn.setUpdatedAt(LocalDateTime.now());

        functionRepository.save(projectId, fn);
        log.info("Function {}: {}:{}", isNew ? "created" : "updated", projectId, name);

        // Deploy to Deno sidecar
        try {
            deployToSidecar(fn);
        } catch (Exception e) {
            log.error("Deploy failed for function: {}:{}", projectId, name, e);
        }

        return FunctionResponse.from(fn);
    }

    /** Delete a function: delete from Deno → delete from DB */
    public void delete(String projectId, String name) {
        Function fn = functionRepository.findByName(projectId, name);
        if (fn == null) {
            throw new FunctionException("Function not found: " + name);
        }

        // Delete from Deno sidecar
        functionInvoker.delete(projectId, name);

        // Delete from DB
        functionRepository.deleteById(projectId, fn.getId());
        log.info("Function deleted: {}:{}", projectId, name);
    }

    /** Get function detail */
    public FunctionResponse findByName(String projectId, String name) {
        Function fn = functionRepository.findByName(projectId, name);
        if (fn == null) {
            throw new FunctionException("Function not found: " + name);
        }
        return FunctionResponse.from(fn);
    }

    /** List functions with pagination */
    public PageDTO<FunctionResponse> findPage(String projectId, FunctionPageRequest request) {
        Predicate filter = Expressions.TRUE;
        if (request.getName() != null && !request.getName().isBlank()) {
            filter = filter.and(Expressions.field(Function::getName).eq(request.getName()));
        }

        long total = functionRepository.count(projectId, filter);
        if (total == 0) {
            return PageDTO.empty();
        }

        List<FunctionResponse> list = functionRepository.find(projectId, filter, request.getPage(), request.getSize())
            .stream()
            .map(FunctionResponse::from)
            .toList();

        return new PageDTO<>(list, total);
    }

    // ============================================================
    // Invocation
    // ============================================================

    /** Invoke a function via the Deno sidecar */
    public FunctionInvokeResponse invoke(String projectId, String name,
                                          FunctionInvokeRequest req) {
        Function fn = functionRepository.findByName(projectId, name);
        if (fn == null) {
            throw new FunctionException("Function not found: " + name);
        }

        FunctionInvokeResponse response = functionInvoker.invoke(projectId, name, req);

        if (response.getMeta() != null) {
            log.info("Function {} executed in {}ms", name, response.getMeta().getExecutionTimeMs());
            if (response.getMeta().getLogs() != null) {
                for (FunctionInvokeResponse.LogEntry entry : response.getMeta().getLogs()) {
                    log.info("[fn:{}][{}] {}", name, entry.getLevel(), entry.getMessage());
                }
            }
        }

        return response;
    }

    // ============================================================
    // Startup Recovery
    // ============================================================

    /** On startup: sync all functions to Deno sidecar */
    void onStart(@Observes StartupEvent event) {
        log.info("Starting function recovery: syncing functions to sidecar...");
        int success = 0;
        int failed = 0;

        String projectId = "dev_test";
        try {
            List<Function> functions = functionRepository.find(projectId, Expressions.TRUE, 1, 1000);
            for (Function fn : functions) {
                try {
                    deployToSidecar(fn);
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.error("Startup deploy failed: {}:{}", projectId, fn.getName(), e);
                }
            }
            log.info("Function recovery complete: {} success, {} failed", success, failed);
        } catch (Exception e) {
            log.warn("Function startup recovery encountered an error", e);
        }
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /** Deploy function source files to Deno sidecar */
    private void deployToSidecar(Function fn) {
        Map<String, String> sourceFiles;
        try {
            sourceFiles = objectMapper.readValue(fn.getSourceFiles(),
                new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new FunctionException("Failed to parse source files", e);
        }

        SidecarDeployRequest deployReq = SidecarDeployRequest.builder()
            .projectId(fn.getProjectId())
            .functionId(fn.getId())
            .name(fn.getName())
            .sourceFiles(sourceFiles)
            .timeout(fn.getTimeout() != null ? fn.getTimeout() : 30)
            .build();

        functionInvoker.deploy(deployReq);
    }
}
