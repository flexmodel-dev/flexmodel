package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.LoginLog;

import java.util.List;

/**
 * @author cjbi
 */
public interface LoginLogRepository {

  void save(LoginLog log);

  List<LoginLog> findByUserId(String userId, int page, int size);

  long countByUserId(String userId);

}
