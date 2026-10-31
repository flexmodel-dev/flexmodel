package dev.flexmodel.apps.apppackage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.apppackage.dto.AppPackage.TaskState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

/**
 * 任务回执（.task.json）读写与归属校验。
 */
@ApplicationScoped
public class AppPackageTaskStore {
  @Inject
  AppPackageStore store;
  @Inject
  ObjectMapper json;

  void save(TaskState state) {
    store.saveTask(state);
  }

  /**
   * 缺失或归属不符返回 null，由调用方决定 404/410 语义。
   */
  TaskState find(String uploadId, String userId) {
    store.requireId(uploadId);
    var path = store.task(uploadId);
    if (!java.nio.file.Files.isRegularFile(path)) return null;
    try {
      TaskState state = json.readValue(path.toFile(), TaskState.class);
      return state.userId().equals(userId) ? state : null;
    } catch (Exception error) {
      throw new IllegalStateException("无法读取导入任务回执", error);
    }
  }

  TaskState require(String uploadId, String userId) {
    TaskState state = find(uploadId, userId);
    if (state == null) throw new WebApplicationException("导入任务不存在", 404);
    return state;
  }

  /**
   * 恢复扫描使用：不做归属过滤。
   */
  TaskState findAny(String uploadId) {
    store.requireId(uploadId);
    var path = store.task(uploadId);
    if (!java.nio.file.Files.isRegularFile(path)) return null;
    try {
      return json.readValue(path.toFile(), TaskState.class);
    } catch (Exception error) {
      throw new IllegalStateException("无法读取导入任务回执", error);
    }
  }

  /**
   * 恢复扫描使用：全部任务回执。
   */
  java.util.List<TaskState> all() {
    java.util.List<TaskState> result = new java.util.ArrayList<>();
    for (var path : store.taskFiles()) {
      try {
        TaskState state = json.readValue(path.toFile(), TaskState.class);
        if (state != null) result.add(state);
      } catch (Exception ignored) { /* 损坏回执交由清理任务按 TTL 回收 */ }
    }
    return result;
  }
}
