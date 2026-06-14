package dev.flexmodel.functions.dto;

import dev.flexmodel.codegen.entity.Function;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
@Data
@Builder
public class FunctionResponse {

    private String id;
    private String projectId;
    private String name;
    private String sourceFiles;
    private int timeout;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FunctionResponse from(Function fn) {
        return FunctionResponse.builder()
            .id(fn.getId())
            .projectId(fn.getProjectId())
            .name(fn.getName())
            .sourceFiles(fn.getSourceFiles())
            .timeout(fn.getTimeout() != null ? fn.getTimeout() : 30)
            .createdBy(fn.getCreatedBy())
            .updatedBy(fn.getUpdatedBy())
            .createdAt(fn.getCreatedAt())
            .updatedAt(fn.getUpdatedAt())
            .build();
    }
}
