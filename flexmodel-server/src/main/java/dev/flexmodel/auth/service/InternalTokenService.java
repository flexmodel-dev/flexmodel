package dev.flexmodel.auth.service;

import dev.flexmodel.common.config.web.jwt.JwtService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;

/**
 * 为 functions-runtime 回调签发临时 JWT（每次 invoke 调用）。
 *
 * @author cjbi
 */
@ApplicationScoped
public class InternalTokenService {

    private static final String ACCOUNT = "svc:runtime";
    private static final Duration TTL = Duration.ofMinutes(5);

  @Inject
  JwtService jwtService;

    /**
     * 为 functions-runtime 回调签发临时 JWT（每次 invoke 调用）。
     */
    public String signToken(String projectId) {
      return jwtService.sign(ACCOUNT, Map.of("projectId", projectId), TTL);
    }
}
