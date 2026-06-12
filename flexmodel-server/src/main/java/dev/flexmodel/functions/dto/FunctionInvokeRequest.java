package dev.flexmodel.functions.dto;

import lombok.Data;

import java.util.Map;

/**
 * @author cjbi
 */
@Data
public class FunctionInvokeRequest {

    private String method = "POST";

    private Map<String, String> headers;

    private Object body;

    private Map<String, String> query;
}
