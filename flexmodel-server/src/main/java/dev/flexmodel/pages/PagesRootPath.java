package dev.flexmodel.pages;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Pages 文件存储路径配置。
 * 从环境变量 FLEXMODEL_PAGES_ROOT 读取，默认 ../pages。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class PagesRootPath {

  private static final String DEFAULT_ROOT = "../pages";
  private final String rootPath;

  public PagesRootPath() {
    this.rootPath = System.getenv().getOrDefault("FLEXMODEL_PAGES_ROOT", DEFAULT_ROOT);
    log.info("Pages root path: {}", rootPath);
  }

  public Path resolve() {
    return Paths.get(rootPath).normalize();
  }
}
