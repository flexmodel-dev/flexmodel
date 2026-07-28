package dev.flexmodel.projectauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Edge token validation request body.
 *
 * @param token        the token to validate (invoke-token JWT, API Key, or IdP token)
 * @param projectId    the project ID from the URL
 * @param functionName the function name from the URL
 */
public record EdgeValidateRequest(
  @JsonProperty("token") String token,
  @JsonProperty("projectId") String projectId,
  @JsonProperty("functionName") String functionName
) {
}
