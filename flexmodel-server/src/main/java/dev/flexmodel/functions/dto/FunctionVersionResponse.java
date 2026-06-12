package dev.flexmodel.functions.dto;

import dev.flexmodel.codegen.entity.FunctionVersion;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
@Data
@Builder
public class FunctionVersionResponse {

    private String id;
    private String functionId;
    private int version;
    private String createdBy;
    private LocalDateTime createdAt;

    public static FunctionVersionResponse from(FunctionVersion version) {
        return FunctionVersionResponse.builder()
            .id(version.getId())
            .functionId(version.getFunctionId())
            .version(version.getVersion())
            .createdBy(version.getCreatedBy())
            .createdAt(version.getCreatedAt())
            .build();
    }
}
