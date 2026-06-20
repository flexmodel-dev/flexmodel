package dev.flexmodel.functions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Internal deploy request sent from Java → Deno functions runtime.
 *
 * @author cjbi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionRuntimeDeployRequest {

    private String projectId;

    private String functionId;

    private String name;

    private Map<String, String> sourceFiles;

    private int timeout;
}
