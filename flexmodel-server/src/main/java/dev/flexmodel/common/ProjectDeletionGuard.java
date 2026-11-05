package dev.flexmodel.common;

public interface ProjectDeletionGuard {
  void beforeDelete(String projectId);
}
