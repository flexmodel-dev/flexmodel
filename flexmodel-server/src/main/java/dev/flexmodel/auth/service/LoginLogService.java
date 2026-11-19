package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.LoginLogResponse;
import dev.flexmodel.auth.repository.LoginLogRepository;
import dev.flexmodel.codegen.entity.LoginLog;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class LoginLogService {

  private static final int MAX_SIZE = 100;

  @Inject
  LoginLogRepository loginLogRepository;

  public void record(String username, String userId, boolean success, String message,
                     String clientIp, String userAgent) {
    try {
      LoginLog loginLog = new LoginLog();
      loginLog.setId(UUID.randomUUID().toString());
      loginLog.setUsername(truncate(username == null ? "" : username, 255));
      loginLog.setUserId(userId);
      loginLog.setClientIp(truncate(clientIp, 45));
      loginLog.setUserAgent(truncate(userAgent, 500));
      loginLog.setSuccess(success);
      loginLog.setMessage(truncate(message, 500));
      loginLog.setCreatedAt(LocalDateTime.now());
      loginLogRepository.save(loginLog);
    } catch (Exception e) {
      // 登录日志失败不能影响登录本身
      log.warn("Failed to record login log for user '{}'", username, e);
    }
  }

  public PageDTO<LoginLogResponse> findByUserId(String userId, int page, int size) {
    int normalizedPage = Math.max(page, 1);
    int normalizedSize = Math.clamp(size, 1, MAX_SIZE);
    long total = loginLogRepository.countByUserId(userId);
    if (total == 0) {
      return PageDTO.empty();
    }
    return new PageDTO<>(
      loginLogRepository.findByUserId(userId, normalizedPage, normalizedSize)
        .stream()
        .map(LoginLogResponse::from)
        .toList(),
      total
    );
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
