package dev.flexmodel.apps.apppackage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.apppackage.dto.AppPackage.StagedPlan;
import dev.flexmodel.apps.apppackage.dto.AppPackage.TaskState;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 应用整包导入的文件暂存：上传包、暂存计划与任务回执，原子写入并按 TTL 清理。
 */
@ApplicationScoped
public class AppPackageStore {
  static final String ID_PATTERN = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";

  @Inject
  ObjectMapper json;
  @ConfigProperty(name = "flexmodel.app-packages.directory")
  Optional<String> directory;

  Path root() {
    return directory.map(Path::of).orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir"), "flexmodel-app-packages"))
      .toAbsolutePath().normalize();
  }

  void requireId(String id) {
    if (id == null || !id.matches(ID_PATTERN)) throw new WebApplicationException("导入任务不存在", 404);
  }

  Path zip(String id) {
    return resolve(id + ".zip");
  }

  Path plan(String id) {
    return resolve(id + ".plan.json");
  }

  Path task(String id) {
    return resolve(id + ".task.json");
  }

  private Path resolve(String name) {
    try {
      Path root = root();
      Files.createDirectories(root);
      return root.resolve(name);
    } catch (IOException error) {
      throw new IllegalStateException("无法创建导入暂存目录", error);
    }
  }

  /**
   * 上传过程中的临时文件，最终经 {@link #publishZip} 原子发布。
   */
  Path stageZip() {
    try {
      Path root = root();
      Files.createDirectories(root);
      return Files.createTempFile(root, "stage-", ".tmp");
    } catch (IOException error) {
      throw new IllegalStateException("无法创建导入导出临时文件", error);
    }
  }

  void publishZip(String id, Path staged) {
    requireId(id);
    try {
      Files.move(staged, zip(id), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException error) {
      throw new IllegalStateException("无法保存上传包", error);
    }
  }

  StagedPlan readPlan(String id) {
    requireId(id);
    Path target = plan(id);
    if (!Files.isRegularFile(target)) throw new WebApplicationException("导入预览不存在或已过期，请重新上传", 410);
    try {
      return json.readValue(target.toFile(), StagedPlan.class);
    } catch (IOException error) {
      throw new IllegalStateException("无法读取导入预览", error);
    }
  }

  boolean planExists(String id) {
    requireId(id);
    return Files.isRegularFile(plan(id));
  }

  void savePlan(String id, StagedPlan value) {
    requireId(id);
    saveAtomic(plan(id), value);
  }

  Path readZip(String id) {
    requireId(id);
    Path target = zip(id);
    if (!Files.isRegularFile(target)) throw new WebApplicationException("导入预览不存在或已过期，请重新上传", 410);
    return target;
  }

  void saveTask(TaskState state) {
    saveAtomic(task(state.uploadId()), state);
  }

  /**
   * 恢复扫描使用：列出全部任务回执文件。
   */
  java.util.List<Path> taskFiles() {
    Path root = root();
    if (!Files.isDirectory(root)) return java.util.List.of();
    try (var files = Files.list(root)) {
      return files.filter(path -> path.getFileName().toString().matches("[0-9a-f-]{36}\\.task\\.json")).toList();
    } catch (IOException error) {
      return java.util.List.of();
    }
  }

  private void saveAtomic(Path target, Object value) {
    try {
      Path temporary = Files.createTempFile(target.getParent(), "stage-", ".tmp");
      try {
        json.writeValue(temporary.toFile(), value);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException error) {
      throw new IllegalStateException("无法保存导入暂存数据", error);
    }
  }

  @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void cleanup() {
    Path root = root();
    if (!Files.isDirectory(root)) return;
    long stageCutoff = System.currentTimeMillis() - dev.flexmodel.apps.apppackage.dto.AppPackage.STORE_TTL_MILLIS;
    long taskCutoff = System.currentTimeMillis() - dev.flexmodel.apps.apppackage.dto.AppPackage.TASK_TTL_MILLIS;
    try (var files = Files.list(root)) {
      files.forEach(path -> {
        String name = path.getFileName().toString();
        try {
          if (name.startsWith("stage-") && name.endsWith(".tmp")) {
            if (Files.getLastModifiedTime(path).toMillis() < stageCutoff) Files.deleteIfExists(path);
            return;
          }
          if (!name.matches("[0-9a-f-]{36}\\.(zip|plan\\.json|task\\.json)")) return;
          long modified = Files.getLastModifiedTime(path).toMillis();
          if (name.endsWith(".task.json")) {
            if (isActiveTask(path)) return; // 活动任务交由启动恢复流程处置
            if (modified >= taskCutoff) return;
          } else if (modified >= stageCutoff) {
            return;
          }
          Files.deleteIfExists(path);
        } catch (IOException ignored) { /* A concurrent request may still own this file; retry next sweep. */ }
      });
    } catch (IOException error) {
      org.jboss.logging.Logger.getLogger(getClass()).warn("清理导入暂存文件失败", error);
    }
  }

  private boolean isActiveTask(Path path) {
    try {
      return isActiveStatus(json.readValue(path.toFile(), TaskState.class).status());
    } catch (IOException ignored) {
      return false;
    }
  }

  static boolean isActiveStatus(String status) {
    return "COMMITTING".equals(status) || "COMPENSATING".equals(status);
  }
}
