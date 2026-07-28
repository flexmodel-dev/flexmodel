package dev.flexmodel.pages;

import dev.flexmodel.common.FlexmodelConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 接收 zip InputStream → 解包 → 写 {root}/{projectId}/{deploymentId}/ → 统计 file_count/size_bytes。
 * 过滤 .. 路径与绝对路径条目（防穿越）。
 *
 * <p>先将 InputStream 完全复制到临时文件，再从临时文件解包。
 * 避免 RESTEasy Reactive multipart 临时文件与 ZipInputStream 的竞争（Windows 文件锁定）。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class PageDeployer {

  @Inject
  FlexmodelConfig flexmodelConfig;

  /**
   * 解包 zip 到文件树，返回部署信息。
   *
   * @param projectId 项目 ID
   * @param zipStream zip 文件输入流（来自 multipart upload）
   * @return DeployResult 包含 deploymentId、fileCount、sizeBytes
   */
  public DeployResult deploy(String projectId, InputStream zipStream) {
    String deploymentId = "dep_" + UUID.randomUUID().toString().substring(0, 8);
    Path root = Paths.get(flexmodelConfig.pages().rootPath()).normalize();
    Path projectDir = root.resolve(projectId);
    Path deploymentDir = projectDir.resolve(deploymentId);

        // 先将 InputStream 完全复制到临时文件，避免与 RESTEasy multipart 清理竞争
    Path tempZip = null;
    try {
      Files.createDirectories(deploymentDir);

      tempZip = Files.createTempFile("pages-upload-", ".zip");
      Files.copy(zipStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
      // InputStream 已完全读取，RESTEasy 可以安全删除其临时文件

      int fileCount = 0;
      long sizeBytes = 0;

      try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          String name = entry.getName();

          // 安全检查：拒绝路径穿越
          if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
            log.warn("Skipping dangerous zip entry: {}", name);
            zis.closeEntry();
            continue;
          }

          // 跳过目录条目
          if (entry.isDirectory()) {
            Path dirPath = deploymentDir.resolve(name);
            Files.createDirectories(dirPath);
            zis.closeEntry();
            continue;
          }

          Path filePath = deploymentDir.resolve(name);

          // 验证规范化路径不越出 deploymentDir
          if (!filePath.normalize().startsWith(deploymentDir.normalize())) {
            log.warn("Skipping zip entry that escapes deployment dir: {}", name);
            zis.closeEntry();
            continue;
          }

          // 确保父目录存在
          Path parentDir = filePath.getParent();
          if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
          }

          // 写文件
          Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
          fileCount++;
          sizeBytes += Files.size(filePath);

          zis.closeEntry();
        }
      }

      log.info("Deployed {} files ({}) to {} for project {}", fileCount, sizeBytes, deploymentDir, projectId);

      return new DeployResult(deploymentId, fileCount, sizeBytes);
    } catch (IOException e) {
      // 清理失败的部署目录
      cleanupDir(deploymentDir);
      throw new PageException("Failed to deploy zip: " + e.getMessage(), e);
    } finally {
      // 清理临时 zip 文件
      if (tempZip != null) {
        try {
          Files.deleteIfExists(tempZip);
        } catch (IOException e) {
          log.warn("Failed to delete temp zip: {}", e.getMessage());
        }
      }
    }
  }

  /**
   * 写单个文件到部署目录（用于默认欢迎页等场景）
   */
  public DeployResult writeSingleFile(String projectId, String deploymentId, String relativePath, String content) {
    Path root = Paths.get(flexmodelConfig.pages().rootPath()).normalize();
    Path projectDir = root.resolve(projectId);
    Path deploymentDir = projectDir.resolve(deploymentId);
    Path filePath = deploymentDir.resolve(relativePath);

    try {
      Files.createDirectories(deploymentDir);
      Path parentDir = filePath.getParent();
      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
      }
      Files.writeString(filePath, content);

      long sizeBytes = Files.size(filePath);
      log.info("Written single file {} ({}) to {} for project {}", relativePath, sizeBytes, deploymentDir, projectId);

      return new DeployResult(deploymentId, 1, sizeBytes);
    } catch (IOException e) {
      throw new PageException("Failed to write file: " + e.getMessage(), e);
    }
  }

  /**
   * 部署结果
   */
  public record DeployResult(String deploymentId, int fileCount, long sizeBytes) {
  }

  private void cleanupDir(Path dir) {
    if (!Files.exists(dir)) return;
    try {
      Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
          Files.delete(d);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      log.warn("Failed to cleanup dir {}: {}", dir, e.getMessage());
    }
  }
}
