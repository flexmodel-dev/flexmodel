package dev.flexmodel.projectauth.provider;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;

/**
 * 认证上下文，传递给 AuthProvider.authenticate()。
 */
@Getter
@Setter
public class AuthContext {
  private String projectId;
  private String bearerToken;
  private String method;
  private String url;
  private Map<String, String> headers;
  private Map<String, String> query;
}
