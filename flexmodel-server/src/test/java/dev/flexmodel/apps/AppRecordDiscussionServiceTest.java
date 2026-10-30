package dev.flexmodel.apps;

import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AppRecordDiscussionServiceTest {
  private final Map<String, Object> mine = Map.of("id", "mine", "author_id", "admin", "parent_id", "");
  private final Map<String, Object> other = Map.of("id", "other", "author_id", "member", "parent_id", "mine");
  private String deleted;

  private AppRecordDiscussionService service() {
    var service = new AppRecordDiscussionService();
    service.sessionContext = new SessionContext();
    service.sessionContext.setAuthenticatedUser(true);
    service.sessionContext.setUserId("admin");
    service.sessionContext.setPermissions(Set.of("*"));
    service.appNavigationService = new AppNavigationService() {
      @Override
      public boolean canAccessForm(String appId, String formName) {
        return true;
      }

      @Override
      public boolean isOwnerOrAdmin(String appId) {
        return true;
      }
    };
    service.appRecordService = new AppRecordService() {
      @Override
      public void requireRecordExists(String appId, String formName, String recordId) {
      }
    };
    service.discussions = new AppRecordDiscussionRepository() {
      @Override
      public List<Map<String, Object>> findRoots(String appId, String formName, String recordId, int page, int size) {
        return List.of(mine);
      }

      @Override
      public List<Map<String, Object>> findReplies(String appId, java.util.Collection<String> rootIds) {
        return List.of(other);
      }

      @Override
      public long count(String appId, String formName, String recordId) {
        return 2;
      }

      @Override
      public long countRoots(String appId, String formName, String recordId) {
        return 1;
      }

      @Override
      public Map<String, Object> find(String appId, String formName, String recordId, String id) {
        return id.equals("mine") ? mine : other;
      }

      @Override
      public void deleteThread(String appId, String id) {
        deleted = id;
      }
    };
    service.transactions = new ProjectUnitOfWork() {
      @Override
      public <T> T executePlatform(String appId, Supplier<T> action) {
        return action.get();
      }
    };
    return service;
  }

  @Test
  void ownerAndAdminCanOnlyDeleteTheirOwnComments() {
    var service = service();
    var root = service.page("app", "form", "record", 1, 20).list().getFirst();
    assertTrue(root.canDelete());
    assertFalse(root.replies().getFirst().canDelete());
    assertThrows(ForbiddenException.class, () -> service.delete("app", "form", "record", "other"));
    assertNull(deleted);
    service.delete("app", "form", "record", "mine");
    assertEquals("mine", deleted);
  }

  @Test
  void ordinaryMemberCanDeleteOwnReplyButNotOthersRoot() {
    var service = service();
    service.sessionContext.setUserId("member");
    service.sessionContext.setPermissions(Set.of());
    var root = service.page("app", "form", "record", 1, 20).list().getFirst();
    assertFalse(root.canDelete());
    assertTrue(root.replies().getFirst().canDelete());
    assertThrows(ForbiddenException.class, () -> service.delete("app", "form", "record", "mine"));
    assertNull(deleted);
    service.delete("app", "form", "record", "other");
    assertEquals("other", deleted);
  }
}
