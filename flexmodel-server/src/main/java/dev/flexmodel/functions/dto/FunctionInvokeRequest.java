package dev.flexmodel.functions.dto;

import lombok.Data;

/**
 * Simplified invoke request — the body IS the function input.
 *
 * @author cjbi
 */
@Data
public class FunctionInvokeRequest {

    private Object input;
}
