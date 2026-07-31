package dev.flexmodel.functions.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response for the invoke-token endpoint.
 * Contains the JWT invoke-token and the edge runtime URL for direct frontend invocation.
 *
 * @author cjbi
 */
@Data
@Builder
public class InvokeTokenResponse {

  /**
   * JWT invoke-token (signed with "svc:invoke" + jwtSecret, 5-minute TTL)
   */
  private String invokeToken;

  /**
   * Edge runtime URL, constructed from flexmodel.edge-url-template
   */
  private String runtimeUrl;
}
