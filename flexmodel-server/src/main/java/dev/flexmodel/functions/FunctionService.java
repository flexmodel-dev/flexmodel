package dev.flexmodel.functions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.codegen.entity.Function;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Function lifecycle management: CRUD, deploy to Deno sidecar, invoke.
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

        if (req.getSourceFiles() != null && !req.getSourceFiles().isEmpty()) {
            fn.setSourceFiles(req.getSourceFiles());
        }
        fn.setUpdatedAt(LocalDateTime.now());

        functionRepository.save(projectId, fn);
        log.info("Function {}: {}:{}", isNew ? "created" : "updated", projectId, name);

        // Deploy to Deno sidecar
        try {
            deployToSidecar(projectId, fn);
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

        functionInvoker.delete(projectId, name);
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

    /** Invoke a function via the Deno sidecar — deploy first to ensure it's available */
    public Response invoke(String projectId, String name,
                           FunctionInvokeRequest req) {
        Function fn = functionRepository.findByName(projectId, name);
        if (fn == null) {
            throw new FunctionException("Function not found: " + name);
        }

        // Always deploy before invoke to keep sidecar in sync
        deployToSidecar(projectId, fn);

        Response response = functionInvoker.invoke(projectId, name, req);
        log.info("Function {} invoked, status={}", name, response.getStatus());
        return response;
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    private void deployToSidecar(String projectId, Function fn) {
        if (fn.getId() == null || fn.getId().isBlank()) {
            throw new FunctionException("Function ID is required for deployment: " + fn.getName());
        }

        Map<String, String> sourceFiles = objectMapper.convertValue(
            fn.getSourceFiles(), new TypeReference<Map<String, String>>() {});

        if (sourceFiles == null || sourceFiles.isEmpty()) {
            throw new FunctionException("Function source files are required for deployment: " + fn.getName());
        }

        SidecarDeployRequest deployReq = SidecarDeployRequest.builder()
            .projectId(projectId)
            .functionId(fn.getId())
            .name(fn.getName())
            .sourceFiles(sourceFiles)
            .timeout(fn.getTimeout() != null ? fn.getTimeout() : 30)
            .build();

        functionInvoker.deploy(deployReq);
    }
}
