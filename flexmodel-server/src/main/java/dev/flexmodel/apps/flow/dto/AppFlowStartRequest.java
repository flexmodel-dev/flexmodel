package dev.flexmodel.apps.flow.dto;

import java.util.Map;

public record AppFlowStartRequest(String recordId, String requestKey, Map<String, Object> input) {
}
