package dev.flexmodel.functions.dto;

import dev.flexmodel.codegen.entity.Function;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author cjbi
 */
@Data
@Builder
public class FunctionResponse {

    private String id;
    private String projectId;
    private String name;
    private String slug;
    private String description;
    private String entryPoint;
    private String status;
    private int currentVersion;
    private int timeout;
    private int memoryLimit;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Associated triggers (for HTTP endpoints)
    private List<TriggerRef> triggers;

    @Data
    @Builder
    public static class TriggerRef {
        private String id;
        private String path;
        private String method;
        private String authMode;
        private boolean enabled;
    }

    public static FunctionResponse from(Function function) {
        return FunctionResponse.builder()
            .id(function.getId())
            .projectId(function.getProjectId())
            .name(function.getName())
            .slug(function.getSlug())
            .description(function.getDescription())
            .entryPoint(function.getEntryPoint())
            .status(function.getStatus())
            .currentVersion(function.getCurrentVersion() != null ? function.getCurrentVersion() : 1)
            .timeout(function.getTimeout() != null ? function.getTimeout() : 30)
            .memoryLimit(function.getMemoryLimit() != null ? function.getMemoryLimit() : 128)
            .createdBy(function.getCreatedBy())
            .updatedBy(function.getUpdatedBy())
            .createdAt(function.getCreatedAt())
            .updatedAt(function.getUpdatedAt())
            .build();
    }
}
