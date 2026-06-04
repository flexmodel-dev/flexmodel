package dev.flexmodel.common;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * @author cjbi
 */
public class SessionContextHolder {

  private static final ThreadLocal<SessionContext> CONTEXT_HOLDER = ThreadLocal.withInitial(SessionContext::new);

  public static void setProjectDatabaseName(String projectDatabaseName) {
    CONTEXT_HOLDER.get().setProjectDatabaseName(projectDatabaseName);
  }

  public static String getProjectDatabaseName() {
    return CONTEXT_HOLDER.get().getProjectDatabaseName();
  }

  public static void setProjectId(String projectId) {
    CONTEXT_HOLDER.get().setProjectId(projectId);
  }

  public static String getProjectId() {
    return CONTEXT_HOLDER.get().getProjectId();
  }

  public static void setUserId(String userId) {
    CONTEXT_HOLDER.get().setUserId(userId);
  }

  public static String getUserId() {
    return CONTEXT_HOLDER.get().getUserId();
  }

  public static void setBranchName(String branchName) {
    CONTEXT_HOLDER.get().setBranchName(branchName);
  }

  public static String getBranchName() {
    return CONTEXT_HOLDER.get().getBranchName();
  }

  public static void setCaller(String caller) {
    CONTEXT_HOLDER.get().setCaller(caller);
  }

  public static String getCaller() {
    return CONTEXT_HOLDER.get().getCaller();
  }

  public static void setScopes(Set<String> scopes) {
    CONTEXT_HOLDER.get().setScopes(scopes);
  }

  public static Set<String> getScopes() {
    return CONTEXT_HOLDER.get().getScopes();
  }


  @Getter
  @Setter
  static class SessionContext {
    private String projectId;
    private String projectDatabaseName;
    private String userId;
    private String branchName;
    private String caller;
    private Set<String> scopes;
  }

}
