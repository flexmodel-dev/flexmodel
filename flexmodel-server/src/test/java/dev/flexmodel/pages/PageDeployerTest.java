package dev.flexmodel.pages;

import dev.flexmodel.common.FlexmodelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageDeployer 单元测试。
 * 测试 Zip 解包、路径穿越过滤、单文件写入。
 *
 * @author cjbi
 */
class PageDeployerTest {

  @TempDir
  Path tempRoot;

  private PageDeployer deployer;

  @BeforeEach
  void setUp() {
    deployer = new PageDeployer();
    deployer.flexmodelConfig = new TestFlexmodelConfig(tempRoot.toString());
  }

  // ============================================================
  // Zip 解包
  // ============================================================

  @Test
  void deploy_unpacksZipSuccessfully() throws IOException {
    byte[] zipBytes = createZip(
      "index.html", "hello world",
      "assets/style.css", "body { color: red; }"
    );

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertNotNull(result.deploymentId());
    assertTrue(result.deploymentId().startsWith("dep_"));
    assertEquals(2, result.fileCount());
    assertTrue(result.sizeBytes() > 0);

    // 验证文件内容
    Path deploymentDir = tempRoot.resolve("myproject").resolve(result.deploymentId());
    assertEquals("hello world", Files.readString(deploymentDir.resolve("index.html")));
    assertEquals("body { color: red; }", Files.readString(deploymentDir.resolve("assets").resolve("style.css")));
  }

  @Test
  void deploy_skipsAbsolutePathEntry() throws IOException {
    byte[] zipBytes = createZip(
      "index.html", "hello",
      "/etc/passwd", "root:x:0:0"
    );

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertEquals(1, result.fileCount());

    Path deploymentDir = tempRoot.resolve("myproject").resolve(result.deploymentId());
    assertTrue(Files.exists(deploymentDir.resolve("index.html")));
    assertFalse(Files.exists(deploymentDir.resolve("etc").resolve("passwd")));
  }

  @Test
  void deploy_skipsBackslashAbsolutePathEntry() throws IOException {
    byte[] zipBytes = createZip(
      "index.html", "hello",
      "\\Windows\\System32", "dangerous"
    );

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertEquals(1, result.fileCount());
  }

  @Test
  void deploy_skipsPathTraversalEntry() throws IOException {
    byte[] zipBytes = createZip(
      "index.html", "hello",
      "../outside.txt", "dangerous"
    );

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertEquals(1, result.fileCount());

    // 确保穿越文件没有写到 pages root 外
    assertFalse(Files.exists(tempRoot.resolve("outside.txt")));
  }

  @Test
  void deploy_skipsNestedPathTraversal() throws IOException {
    byte[] zipBytes = createZip(
      "index.html", "hello",
      "foo/../../outside.txt", "dangerous"
    );

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertEquals(1, result.fileCount());
    assertFalse(Files.exists(tempRoot.resolve("outside.txt")));
  }

  @Test
  void deploy_acceptsDoubleDotInFilename() throws IOException {
    // "file..backup.txt" 是合法文件名，不应被误拒
    byte[] zipBytes = createZip(
      "file..backup.txt", "backup content"
    );

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertEquals(1, result.fileCount());

    Path deploymentDir = tempRoot.resolve("myproject").resolve(result.deploymentId());
    assertTrue(Files.exists(deploymentDir.resolve("file..backup.txt")));
  }

  @Test
  void deploy_handlesDirectoryEntries() throws IOException {
    byte[] zipBytes = createZipWithDirectory("assets/", "assets/main.js", "console.log('hi')");

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(zipBytes));

    assertEquals(1, result.fileCount());

    Path deploymentDir = tempRoot.resolve("myproject").resolve(result.deploymentId());
    assertTrue(Files.isDirectory(deploymentDir.resolve("assets")));
    assertTrue(Files.exists(deploymentDir.resolve("assets").resolve("main.js")));
  }

  @Test
  void deploy_handlesEmptyZip() throws IOException {
    // 创建一个空的 zip（无条目）
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(baos);
    zos.close();
    byte[] emptyZip = baos.toByteArray();

    PageDeployer.DeployResult result = deployer.deploy("myproject", new java.io.ByteArrayInputStream(emptyZip));

    assertEquals(0, result.fileCount());
    assertEquals(0, result.sizeBytes());
    assertNotNull(result.deploymentId());
  }

  // ============================================================
  // 单文件写入
  // ============================================================

  @Test
  void writeSingleFile_createsFile() throws IOException {
    PageDeployer.DeployResult result = deployer.writeSingleFile(
      "myproject", "dep_test123", "index.html", "<h1>Hello</h1>"
    );

    assertEquals("dep_test123", result.deploymentId());
    assertEquals(1, result.fileCount());
    assertTrue(result.sizeBytes() > 0);

    Path filePath = tempRoot.resolve("myproject").resolve("dep_test123").resolve("index.html");
    assertEquals("<h1>Hello</h1>", Files.readString(filePath));
  }

  @Test
  void writeSingleFile_createsNestedPath() throws IOException {
    PageDeployer.DeployResult result = deployer.writeSingleFile(
      "myproject", "dep_test456", "sub/dir/file.txt", "nested content"
    );

    assertEquals(1, result.fileCount());

    Path filePath = tempRoot.resolve("myproject").resolve("dep_test456").resolve("sub").resolve("dir").resolve("file.txt");
    assertEquals("nested content", Files.readString(filePath));
  }

  // ============================================================
  // 辅助方法
  // ============================================================

  private byte[] createZip(String... entries) throws IOException {
    // entries: 交替传入 name, content
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(baos);
    for (int i = 0; i < entries.length; i += 2) {
      ZipEntry entry = new ZipEntry(entries[i]);
      zos.putNextEntry(entry);
      zos.write(entries[i + 1].getBytes());
      zos.closeEntry();
    }
    zos.close();
    return baos.toByteArray();
  }

  private byte[] createZipWithDirectory(String dirName, String fileName, String content) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ZipOutputStream zos = new ZipOutputStream(baos);
    // 目录条目
    zos.putNextEntry(new ZipEntry(dirName));
    zos.closeEntry();
    // 文件条目
    zos.putNextEntry(new ZipEntry(fileName));
    zos.write(content.getBytes());
    zos.closeEntry();
    zos.close();
    return baos.toByteArray();
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
    public EventsConfig events() {
      return null;
    }

    @Override
    public String projectUrlTemplate() { return ""; }

    @Override
    public java.util.Optional<String> projectBaseDomain() {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Map<String, DatasourceConfig> datasources() { return java.util.Map.of(); }

    @Override
    public String apiRootPath() { return "/api"; }

    @Override
    public String version() {
      return "dev";
    }

    @Override
    public JwtConfig jwt() { return null; }
  }
}
