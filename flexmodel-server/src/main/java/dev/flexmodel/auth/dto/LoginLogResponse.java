package dev.flexmodel.auth.dto;

import dev.flexmodel.codegen.entity.LoginLog;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
public record LoginLogResponse(
  String id,
  String ip,
  String agent,
  boolean success,
  String message,
  LocalDateTime createdAt
) {

  public static LoginLogResponse from(LoginLog log) {
    return new LoginLogResponse(
      log.getId(),
      log.getClientIp(),
      log.getUserAgent(),
      Boolean.TRUE.equals(log.getSuccess()),
      log.getMessage(),
      log.getCreatedAt()
    );
  }
}
