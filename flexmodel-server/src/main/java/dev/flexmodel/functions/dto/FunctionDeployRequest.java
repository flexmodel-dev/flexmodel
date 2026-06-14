package dev.flexmodel.functions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Deploy (upsert) request for cloud functions.
 *
 * @author cjbi
 */
@Data
public class FunctionDeployRequest {

    @NotBlank(message = "函数名称不能为空")
    private String name;

    @NotNull(message = "源码不能为空")
    private Map<String, String> sourceFiles;

    private Integer timeout = 30;
}
