package dev.flexmodel.functions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deploy request sent from Java → Deno sidecar (metadata only, no sourceCode).
 *
 * @author cjbi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionDeployRequest {

    private String projectId;

    @JsonProperty("functionId")
    private String functionId;

    private String name;

    private int version;

    private String entryPoint;

    private int timeout;

    private int memoryLimit;
}
