package dev.flexmodel.projectauth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from edge token validation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgeValidateResponse(
  @JsonProperty("valid") boolean valid,
  @JsonProperty("projectId") String projectId,
  @JsonProperty("functionName") String functionName,
  @JsonProperty("authToken") String authToken,
  @JsonProperty("authType") String authType,
  @JsonProperty("userId") String userId
) {
  public static EdgeValidateResponse invalid() {
    return new EdgeValidateResponse(false, null, null, null, null, null);
  }

  public static EdgeValidateResponse valid(String projectId, String functionName,
                                           String authToken, String authType, String userId) {
    return new EdgeValidateResponse(true, projectId, functionName, authToken, authType, userId);
  }
}
