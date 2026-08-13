package dev.flexmodel.pages;

import dev.flexmodel.common.FlexmodelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageAliasManager 单元测试。
 * 测试路径穿越校验、软链创建/删除/解析。
 *
 * @author cjbi
 */
class PageAliasManagerTest {

  @TempDir
  Path tempRoot;

  private PageAliasManager aliasManager;

  @BeforeEach
  void setUp() {
    aliasManager = new PageAliasManager();
    // 手动注入配置（使用 @TempDir 作为 pages root）
    aliasManager.flexmodelConfig = new TestFlexmodelConfig(tempRoot.toString());
  }

  // ============================================================
  // 路径穿越校验
  // ============================================================

  @Test
  void validatePathComponent_rejectsDotDot() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "..", "dep_abc123"));
    assertTrue(ex.getMessage().contains("'..'"));
  }

  @Test
  void validatePathComponent_rejectsSlashInAlias() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "foo/bar", "dep_abc123"));
    assertTrue(ex.getMessage().contains("separators"));
  }

  @Test
  void validatePathComponent_rejectsBackslashInAlias() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "foo\\bar", "dep_abc123"));
    assertTrue(ex.getMessage().contains("separators"));
  }

  @Test
  void validatePathComponent_rejectsDotDotPrefix() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "..hidden", "dep_abc123"));
    assertTrue(ex.getMessage().contains("'..'"));
  }

  @Test
  void validatePathComponent_rejectsBlankProjectId() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("", "production", "dep_abc123"));
    assertTrue(ex.getMessage().contains("projectId must not be blank"));
  }

  @Test
  void validatePathComponent_rejectsBlankAlias() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "  ", "dep_abc123"));
    assertTrue(ex.getMessage().contains("must not be blank"));
  }

  @Test
  void validatePathComponent_rejectsTraversalInDeploymentId() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "production", "../etc"));
    assertTrue(ex.getMessage().contains("'..'"));
  }

  @Test
  void validatePathComponent_acceptsValidNames() throws IOException {
    Path deploymentDir = tempRoot.resolve("myproject").resolve("dep_abc123");
    Files.createDirectories(deploymentDir);
    Files.writeString(deploymentDir.resolve("index.html"), "hello");

    assertDoesNotThrow(() -> aliasManager.createAlias("myproject", "production", "dep_abc123"));
  }

  @Test
  void validatePathComponent_acceptsDoubleDotInFilename() throws IOException {
    // "file..backup" 是合法的文件名组件，不应被拒绝
    Path deploymentDir = tempRoot.resolve("myproject").resolve("dep_abc123");
    Files.createDirectories(deploymentDir);
    Files.writeString(deploymentDir.resolve("index.html"), "hello");

    assertDoesNotThrow(() -> aliasManager.createAlias("myproject", "preview..backup", "dep_abc123"));
  }

  // ============================================================
  // 软链创建与解析
  // ============================================================

  @Test
  void createAlias_createsSymlink() throws IOException {
    Path deploymentDir = tempRoot.resolve("myproject").resolve("dep_abc123");
    Files.createDirectories(deploymentDir);
    Files.writeString(deploymentDir.resolve("index.html"), "hello");

    aliasManager.createAlias("myproject", "production", "dep_abc123");

    Path aliasPath = tempRoot.resolve("myproject").resolve("production");
    assertTrue(Files.exists(aliasPath));
    assertTrue(Files.isSymbolicLink(aliasPath));

    // 解析别名应返回 deploymentId
    String resolved = aliasManager.resolveAlias("myproject", "production");
    assertEquals("dep_abc123", resolved);
  }

  @Test
  void createAlias_throwsWhenDeploymentNotFound() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.createAlias("myproject", "production", "dep_nonexist"));
    assertTrue(ex.getMessage().contains("Deployment directory not found"));
  }

  @Test
  void createAlias_switchesExistingAlias() throws IOException {
    Path dep1 = tempRoot.resolve("myproject").resolve("dep_11111111");
    Files.createDirectories(dep1);
    Files.writeString(dep1.resolve("index.html"), "version 1");

    Path dep2 = tempRoot.resolve("myproject").resolve("dep_22222222");
    Files.createDirectories(dep2);
    Files.writeString(dep2.resolve("index.html"), "version 2");

    aliasManager.createAlias("myproject", "production", "dep_11111111");
    assertEquals("dep_11111111", aliasManager.resolveAlias("myproject", "production"));

    aliasManager.createAlias("myproject", "production", "dep_22222222");
    assertEquals("dep_22222222", aliasManager.resolveAlias("myproject", "production"));
  }

  // ============================================================
  // 别名删除
  // ============================================================

  @Test
  void removeAlias_deletesSymlink() throws IOException {
    Path deploymentDir = tempRoot.resolve("myproject").resolve("dep_abc123");
    Files.createDirectories(deploymentDir);
    Files.writeString(deploymentDir.resolve("index.html"), "hello");

    aliasManager.createAlias("myproject", "production", "dep_abc123");
    assertTrue(Files.exists(tempRoot.resolve("myproject").resolve("production")));

    aliasManager.removeAlias("myproject", "production");
    assertFalse(Files.exists(tempRoot.resolve("myproject").resolve("production")));

    // 部署目录本身不应被删除
    assertTrue(Files.exists(deploymentDir));
  }

  @Test
  void removeAlias_noErrorIfAliasNotExists() {
    assertDoesNotThrow(() -> aliasManager.removeAlias("myproject", "nonexistent"));
  }

  // ============================================================
  // 别名解析
  // ============================================================

  @Test
  void resolveAlias_returnsNullIfNotExists() {
    assertNull(aliasManager.resolveAlias("myproject", "nonexistent"));
  }

  @Test
  void resolveAlias_rejectsTraversal() {
    PageException ex = assertThrows(PageException.class,
      () -> aliasManager.resolveAlias("myproject", ".."));
    assertTrue(ex.getMessage().contains("'..'"));
  }

  // ============================================================
  // 测试用 FlexmodelConfig 实现
  // ============================================================

  private static class TestFlexmodelConfig implements FlexmodelConfig {

    private final String pagesRootPath;

    TestFlexmodelConfig(String pagesRootPath) {
      this.pagesRootPath = pagesRootPath;
    }

    @Override
    public PagesConfig pages() {
      return () -> pagesRootPath;
    }

    @Override
    public String projectUrlTemplate() { return ""; }

    @Override
    public String projectBaseDomain() {
      return "";
    }

    @Override
    public java.util.Map<String, DatasourceConfig> datasources() { return java.util.Map.of(); }

    @Override
    public String apiRootPath() { return "/api"; }

    @Override
    public JwtConfig jwt() { return null; }
  }
}
