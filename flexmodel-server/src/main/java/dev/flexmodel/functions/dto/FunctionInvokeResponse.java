package dev.flexmodel.functions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
@Data
public class FunctionInvokeResponse {

    private int status;

    private Map<String, String> headers;

    private Object body;

    @JsonProperty("_meta")
    private InvocationMeta meta;

    @Data
    public static class InvocationMeta {
        private long executionTimeMs;
        private List<LogEntry> logs;
    }

    @Data
    public static class LogEntry {
        private String level;
        private String message;
        private Object data;
    }
}
