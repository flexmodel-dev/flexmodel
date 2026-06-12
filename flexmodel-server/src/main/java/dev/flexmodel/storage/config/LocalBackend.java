package dev.flexmodel.storage.config;

import dev.flexmodel.storage.StorageOperations;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 本地文件系统存储后端实现
 * <p>
 * 以本地目录为基础路径，容器对应子目录。
 *
 * @author cjbi
 */
public class LocalBackend implements StorageBackend {

  public static final String TYPE = "local";

  private final Path basePath;
  private final boolean readOnly;

  public LocalBackend(String basePath, boolean readOnly) {
    this.basePath = Paths.get(basePath).normalize().toAbsolutePath();
    this.readOnly = readOnly;
  }

  @Override
  public StorageOperations createOperations(String prefixPath) {
    String fullBasePath = basePath.resolve(prefixPath).normalize().toString();
    return new LocalStorageOperations(fullBasePath);
  }

  @Override
  public void createContainer(String prefix) {
    if (readOnly) {
      throw new StorageReadOnlyException("createContainer");
    }
    Path containerPath = resolveContainerPath(prefix);
    try {
      if (!Files.exists(containerPath)) {
        Files.createDirectories(containerPath);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to create local container: " + prefix, e);
    }
  }

  @Override
  public void deleteContainer(String prefix) {
    if (readOnly) {
      throw new StorageReadOnlyException("deleteContainer");
    }
    Path containerPath = resolveContainerPath(prefix);
    if (!Files.exists(containerPath)) {
      return;
    }
    try {
      Files.walkFileTree(containerPath, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete local container: " + prefix, e);
    }
  }

  @Override
  public boolean containerExists(String prefix) {
    Path containerPath = resolveContainerPath(prefix);
    return Files.exists(containerPath) && Files.isDirectory(containerPath);
  }

  @Override
  public void validate() {
    try {
      if (!Files.exists(basePath)) {
        if (!readOnly) {
          Files.createDirectories(basePath);
        } else {
          throw new RuntimeException("Local storage base path does not exist and cannot be created in read-only mode: " + basePath);
        }
      }
      if (!Files.isDirectory(basePath)) {
        throw new RuntimeException("Local storage base path is not a directory: " + basePath);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to validate local storage base path: " + basePath, e);
    }
  }

  @Override
  public String getType() {
    return TYPE;
  }

  public Path getBasePath() {
    return basePath;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  private Path resolveContainerPath(String prefix) {
    Path resolved = basePath.resolve(prefix).normalize();
    if (!resolved.startsWith(basePath)) {
      throw new SecurityException("Attempted to access path outside base directory: " + prefix);
    }
    return resolved;
  }
}
