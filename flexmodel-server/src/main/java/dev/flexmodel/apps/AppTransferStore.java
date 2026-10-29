package dev.flexmodel.apps;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.dto.AppTransfer.*;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class AppTransferStore {
  static final long TTL = Duration.ofMinutes(30).toMillis();
  @Inject ObjectMapper json;
  @ConfigProperty(name = "flexmodel.transfers.directory")
  Optional<String> directory;

  private Path root() {
    return directory.map(Path::of).orElseGet(() -> Path.of(System.getProperty("java.io.tmpdir"), "flexmodel-transfers"))
      .toAbsolutePath().normalize();
  }

  Path file(String id, String suffix) {
    if (id == null || !id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
      throw new WebApplicationException("导入任务不存在", 404);
    try {
      Path root = root();
      Files.createDirectories(root);
      return root.resolve(id + suffix);
    } catch (IOException error) { throw new IllegalStateException("无法创建导入暂存目录", error); }
  }

  <T> void save(String id, String suffix, T value) {
    Path target = file(id, suffix);
    try {
      Path temporary = Files.createTempFile(target.getParent(), "stage-", ".tmp");
      try {
        json.writeValue(temporary.toFile(), value);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      } finally { Files.deleteIfExists(temporary); }
    } catch (IOException error) { throw new IllegalStateException("无法保存导入预览", error); }
  }

  <T> T read(String id, String suffix, Class<T> type) {
    Path target = file(id, suffix);
    if (!Files.isRegularFile(target)) throw new WebApplicationException("导入预览不存在或已过期，请重新上传", 410);
    try { return json.readValue(target.toFile(), type); }
    catch (IOException error) { throw new IllegalStateException("无法读取导入预览", error); }
  }

  void requireOwner(String appId, String userId, String expectedApp, String expectedUser, long expiresAt) {
    if (!appId.equals(expectedApp) || !userId.equals(expectedUser))
      throw new WebApplicationException("导入任务不存在", 404);
    if (System.currentTimeMillis() > expiresAt) throw new WebApplicationException("导入预览已过期，请重新上传", 410);
  }

  @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void cleanup() {
    Path root = root();
    if (!Files.isDirectory(root)) return;
    try (var files = Files.list(root)) {
      long cutoff = System.currentTimeMillis() - TTL;
      files.filter(path -> path.getFileName().toString().matches("([0-9a-f-]{36}\\.(upload|plan)\\.json|stage-.*\\.tmp)"))
        .forEach(path -> {
          try { if (Files.getLastModifiedTime(path).toMillis() < cutoff) Files.deleteIfExists(path); }
          catch (IOException ignored) { /* A concurrent request may still own this file; retry next sweep. */ }
        });
    } catch (IOException error) { org.jboss.logging.Logger.getLogger(getClass()).warn("清理导入暂存文件失败", error); }
  }
}
