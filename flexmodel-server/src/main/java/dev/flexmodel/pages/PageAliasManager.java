package dev.flexmodel.pages;

import dev.flexmodel.common.FlexmodelConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 创建/原子切换相对软链，Windows fallback（硬链/目录 copy）。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class PageAliasManager {

  @Inject
  FlexmodelConfig flexmodelConfig;

  /**
   * 创建或原子切换别名软链：{root}/{projectId}/{alias} → {deploymentId}
   * <p>
   * 策略：先创建临时软链 → Files.move(ATOMIC_MOVE) 原子覆盖目标。
   * Windows fallback：软链不可用时用硬链或目录 copy。
   */
  public void createAlias(String projectId, String alias, String deploymentId) {
    Path root = Paths.get(flexmodelConfig.pages().rootPath()).normalize();
    Path projectDir = root.resolve(projectId);
    Path target = projectDir.resolve(alias);
    Path deploymentDir = projectDir.resolve(deploymentId);

        // 确保部署目录存在
    if (!Files.exists(deploymentDir)) {
      throw new PageException("Deployment directory not found: " + deploymentDir);
    }

    // 尝试软链方式
    try {
      createSymlinkAlias(target, deploymentDir, projectDir);
      log.info("Alias '{}' → '{}' created via symlink for project {}", alias, deploymentId, projectId);
      return;
    } catch (IOException e) {
      log.warn("Symlink creation failed for alias '{}' (likely Windows): {}", alias, e.getMessage());
    }

    // Fallback: 目录 copy
    try {
      // 删除旧目标
      if (Files.exists(target)) {
        deleteRecursive(target);
      }
      Files.walkFileTree(deploymentDir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          Path relative = deploymentDir.relativize(dir);
          Path dest = target.resolve(relative);
          Files.createDirectories(dest);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Path relative = deploymentDir.relativize(file);
          Path dest = target.resolve(relative);
          Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
          return FileVisitResult.CONTINUE;
        }
      });
      log.info("Alias '{}' → '{}' created via directory copy for project {}", alias, deploymentId, projectId);
    } catch (IOException e) {
      throw new PageException("Failed to create alias '" + alias + "' via fallback copy: " + e.getMessage(), e);
    }
  }

  /**
   * 删除别名（软链或目录）
   */
  public void removeAlias(String projectId, String alias) {
    Path root = Paths.get(flexmodelConfig.pages().rootPath()).normalize();
    Path target = root.resolve(projectId).resolve(alias);

    if (!Files.exists(target)) {
      return;
    }

    try {
      if (Files.isSymbolicLink(target)) {
        Files.delete(target);
      } else {
        deleteRecursive(target);
      }
      log.info("Alias '{}' removed for project {}", alias, projectId);
    } catch (IOException e) {
      throw new PageException("Failed to remove alias '" + alias + "': " + e.getMessage(), e);
    }
  }

  /**
   * 获取别名指向的实际部署目录名
   */
  public String resolveAlias(String projectId, String alias) {
    Path root = Paths.get(flexmodelConfig.pages().rootPath()).normalize();
    Path aliasPath = root.resolve(projectId).resolve(alias);

    if (!Files.exists(aliasPath)) {
      return null;
    }

    try {
      if (Files.isSymbolicLink(aliasPath)) {
        Path target = Files.readSymbolicLink(aliasPath);
        // 相对软链：target 就是 deploymentId 目录名
        return target.getFileName().toString();
      } else {
        // copy fallback：目录名就是 deploymentId（copy 时保留了目录名）
        // 无法区分，返回 null 表示需要从 DB 查询
        return null;
      }
    } catch (IOException e) {
      log.warn("Failed to resolve alias '{}': {}", alias, e.getMessage());
      return null;
    }
  }

  private void createSymlinkAlias(Path target, Path deploymentDir, Path projectDir) throws IOException {
    // 创建临时软链
    String tempName = "_tmp_" + deploymentDir.getFileName().toString() + "_" + System.nanoTime();
    Path tempLink = projectDir.resolve(tempName);

    // 相对路径软链
    Path relativeTarget = projectDir.relativize(deploymentDir);
    Files.createSymbolicLink(tempLink, relativeTarget);

    // 原子覆盖目标
    try {
      Files.move(tempLink, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      // 非原子 move（某些 FS 不支持），仍然安全因为目标会被替换
      Files.move(tempLink, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void deleteRecursive(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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
    } else {
      Files.deleteIfExists(path);
    }
  }
}
