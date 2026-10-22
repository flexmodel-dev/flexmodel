package dev.flexmodel.common.persistence;

import dev.flexmodel.session.Session;

/**
 * Transaction operations owned by the server, without extending the engine's Session contract.
 */
public interface ManagedSession extends Session {
  boolean isTransactionActive();

  int lockRow(String model, String column, Object value);

  java.util.List<java.util.Map<String, Object>> lockRows(String model, dev.flexmodel.query.Query query);

  void afterCommit(Runnable callback);
}
