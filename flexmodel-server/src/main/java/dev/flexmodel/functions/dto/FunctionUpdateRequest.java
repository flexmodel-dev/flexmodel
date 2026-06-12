package dev.flexmodel.functions.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author cjbi
 */
@Data
public class FunctionUpdateRequest {

    private String description;

    @NotNull(message = "源码不能为空")
    private String sourceCode;

    private String entryPoint;

    private Integer timeout;

    private Integer memoryLimit;
}
