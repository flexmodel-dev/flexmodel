package dev.flexmodel.functions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author cjbi
 */
@Data
public class FunctionCreateRequest {

    @NotBlank(message = "函数名称不能为空")
    private String name;

    @NotBlank(message = "函数标识符不能为空")
    private String slug;

    private String description;

    @NotNull(message = "源码不能为空")
    private String sourceCode;

    private String entryPoint = "default";

    private Integer timeout = 30;

    private Integer memoryLimit = 128;

    // HTTP Trigger configuration
    private String triggerPath;
    private String triggerMethod = "POST";
    private String authMode = "PUBLIC";
}
