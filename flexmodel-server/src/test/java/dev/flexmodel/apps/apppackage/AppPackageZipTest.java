package dev.flexmodel.apps.apppackage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.form.dto.AppFormFieldControlRecord;
import dev.flexmodel.apps.form.dto.AppFormFieldControls;
import dev.flexmodel.apps.form.dto.AppFormFieldRecord;
import dev.flexmodel.apps.form.dto.AppFormItemRecord;
import dev.flexmodel.apps.form.dto.AppFormLayoutRecord;
import dev.flexmodel.apps.form.dto.AppFormRecord;
import dev.flexmodel.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static dev.flexmodel.apps.apppackage.AppPackageZip.Content;
import static dev.flexmodel.apps.apppackage.dto.AppPackage.*;
import static org.junit.jupiter.api.Assertions.*;

class AppPackageZipTest {
  @TempDir
  Path directory;
  ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  AppPackageZip zip() {
    var codec = new AppPackageZip();
    codec.json = mapper;
    return codec;
  }

  private Content minimalContent() {
    Manifest manifest = new Manifest(FORMAT, VERSION, "2026-10-30T00:00:00Z",
      new ManifestSource("app_a", "test"),
      List.of(new ManifestForm("f_order", 2, false, null)), List.of());
    AppInfo app = new AppInfo("测试应用", "描述", "Boxes", "blue");
    AppFormFieldRecord name = new AppFormFieldRecord("name", "名称", "TEXT", true, false, null, null, null);
    AppFormFieldRecord amount = new AppFormFieldRecord("amount", "金额", "NUMBER", false, false, null, null, null);
    AppFormRecord form = new AppFormRecord("f_order", "订单", "", "name", "name",
      LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0), List.of(name, amount),
      new AppFormLayoutRecord(List.of(
        new AppFormItemRecord("FIELD", "name", "名称", null, "FULL", null, null, null, null),
        new AppFormItemRecord("FIELD", "amount", "金额", null, "FULL", null, null, null, null)),
        Map.of("name", new AppFormFieldControls(
          new AppFormFieldControlRecord(List.of("CREATE", "UPDATE"), Map.of("amount", Map.of("_gt", 0))),
          null,
          new AppFormFieldControlRecord(List.of("UPDATE"), null)))),
      List.of());
    return new Content(manifest, app, List.of(form), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of());
  }

  @Test
  void roundTripsMinimalPackage() throws IOException {
    Path file = directory.resolve("pkg.flexmodel.zip");
    long size = zip().write(file, minimalContent());
    assertTrue(size > 0);
    assertTrue(Files.exists(file));

    Content read = zip().read(file);
    assertEquals("测试应用", read.app().name());
    assertEquals(1, read.forms().size());
    assertEquals("f_order", read.forms().getFirst().name());
    assertEquals(2, read.forms().getFirst().fields().size());
    assertEquals("name", read.forms().getFirst().displayField());
    var controls = read.forms().getFirst().layout().controls();
    assertEquals(List.of("CREATE", "UPDATE"), controls.get("name").hidden().scenes());
    assertEquals(Map.of("amount", Map.of("_gt", 0)), controls.get("name").hidden().condition());
    assertEquals(List.of("UPDATE"), controls.get("name").readonly().scenes());
    assertNull(controls.get("name").required());
    assertEquals(FORMAT, read.manifest().format());
    assertEquals(VERSION, read.manifest().version());
    assertEquals(7, read.rawFiles().size());
  }

  @Test
  void deletesTargetAndFailsWhenPackageExceedsLimit() throws IOException {
    // 单 entry 超过 50 MiB：写入与读取两端都必须拒绝
    Path file = directory.resolve("big.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
      out.putNextEntry(new ZipEntry(MANIFEST_FILE));
      out.write(new byte[1024]);
      out.closeEntry();
      out.putNextEntry(new ZipEntry(DATA_DIRECTORY + "f_form.json"));
      out.write("rows\":[".getBytes(StandardCharsets.UTF_8)); // 压缩效率无关紧要，靠字节数触发
      out.write(new byte[(int) MAX_ENTRY_BYTES + 1024]);
      out.closeEntry();
    }
    assertThrows(ValidationException.class, () -> zip().read(file));
  }

  @Test
  void rejectsPathTraversalAndAbsoluteEntries() throws IOException {
    assertRejected("路径穿越", "../evil.json");
    assertRejected("绝对路径", "/etc/passwd");
    assertRejected("反斜杠", "a\\b.json");
    assertRejected("盘符", "C:/evil.json");
  }

  @Test
  void rejectsDuplicateEntries() throws IOException {
    // ZipOutputStream 拒绝重复名，只能按 ZIP 格式手写小端字节构造两个同名 entry
    byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
    byte[] name = MANIFEST_FILE.getBytes(StandardCharsets.UTF_8);
    var crc = new java.util.zip.CRC32();
    crc.update(content);
    int crcValue = (int) crc.getValue();

    Path file = directory.resolve("dup.zip");
    try (var out = new java.io.DataOutputStream(Files.newOutputStream(file))) {
      int[] offsets = new int[2];
      for (int index = 0; index < 2; index++) {
        offsets[index] = out.size();
        le32(out, 0x04034b50);
        le16(out, 20);
        le16(out, 0);
        le16(out, 0);
        le16(out, 0);
        le16(out, 0);
        le32(out, crcValue);
        le32(out, content.length);
        le32(out, content.length);
        le16(out, name.length);
        le16(out, 0);
        out.write(name);
        out.write(content);
      }
      int cdStart = out.size();
      for (int index = 0; index < 2; index++) {
        le32(out, 0x02014b50);
        le16(out, 20);
        le16(out, 20);
        le16(out, 0);
        le16(out, 0);
        le16(out, 0);
        le16(out, 0);
        le32(out, crcValue);
        le32(out, content.length);
        le32(out, content.length);
        le16(out, name.length);
        le16(out, 0);
        le16(out, 0);
        le16(out, 0);
        le16(out, 0);
        le32(out, 0);
        le32(out, offsets[index]);
        out.write(name);
      }
      int cdSize = out.size() - cdStart;
      le32(out, 0x06054b50);
      le16(out, 0);
      le16(out, 0);
      le16(out, 2);
      le16(out, 2);
      le32(out, cdSize);
      le32(out, cdStart);
      le16(out, 0);
    }
    assertThrows(ValidationException.class, () -> zip().read(file));
  }

  private static void le16(java.io.DataOutputStream out, int value) throws IOException {
    out.write(value & 0xff);
    out.write((value >>> 8) & 0xff);
  }

  private static void le32(java.io.DataOutputStream out, int value) throws IOException {
    out.write(value & 0xff);
    out.write((value >>> 8) & 0xff);
    out.write((value >>> 16) & 0xff);
    out.write((value >>> 24) & 0xff);
  }

  @Test
  void rejectsUnknownFiles() throws IOException {
    assertRejected("未知文件", "extra.txt");
    assertRejected("非法数据文件", DATA_DIRECTORY + "1bad.json");
  }

  @Test
  void rejectsTooManyEntries() throws IOException {
    Path file = directory.resolve("many.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
      for (int index = 0; index <= MAX_ENTRIES; index++) {
        out.putNextEntry(new ZipEntry(DATA_DIRECTORY + "f_form" + index + ".json"));
        out.closeEntry();
      }
    }
    assertThrows(ValidationException.class, () -> zip().read(file));
  }

  @Test
  void rejectsMissingRequiredFiles() throws IOException {
    Path file = directory.resolve("partial.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
      for (String name : List.of(MANIFEST_FILE, APP_FILE, FORMS_FILE, NAVIGATION_FILE,
        WORKBENCHES_FILE)) {
        out.putNextEntry(new ZipEntry(name));
        out.write("null".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    assertThrows(ValidationException.class, () -> zip().read(file));
  }

  @Test
  void rejectsDeepJson() throws IOException {
    StringBuilder deep = new StringBuilder("{\"a\":");
    for (int index = 0; index < MAX_JSON_DEPTH + 5; index++) deep.append('[');
    for (int index = 0; index < MAX_JSON_DEPTH + 5; index++) deep.append(']');
    deep.append("}");
    Path file = directory.resolve("deep.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
      for (String name : List.of(MANIFEST_FILE, APP_FILE, FORMS_FILE, NAVIGATION_FILE,
        WORKBENCHES_FILE, AUTHORIZATIONS_FILE)) {
        out.putNextEntry(new ZipEntry(name));
        out.write("null".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
      out.putNextEntry(new ZipEntry(DATA_DIRECTORY + "f_form.json"));
      out.write(deep.toString().getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    assertThrows(ValidationException.class, () -> zip().read(file));
  }

  @Test
  void planHashIsStableAndSensitive() throws IOException {
    Path left = directory.resolve("left.zip");
    Path right = directory.resolve("right.zip");
    zip().write(left, minimalContent());
    Content content = zip().read(left);
    assertEquals(zip().planHash(content.rawFiles()), zip().planHash(content.rawFiles()));
    zip().write(right, content);
    assertEquals(zip().planHash(zip().read(left).rawFiles()), zip().planHash(zip().read(right).rawFiles()));
    assertNotEquals(zip().planHash(content.rawFiles()), zip().sha256("other".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void jsonDepthTracksStringsAndEscapes() {
    assertEquals(0, AppPackageZip.jsonDepth("null".getBytes(StandardCharsets.UTF_8)));
    assertEquals(2, AppPackageZip.jsonDepth("{\"a\":[1]}".getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, AppPackageZip.jsonDepth("{\"a\":\"[{((\"".getBytes(StandardCharsets.UTF_8)));
    assertEquals(1, AppPackageZip.jsonDepth("{\"a\":\"\\\"[\"".getBytes(StandardCharsets.UTF_8)));
  }

  private void assertRejected(String label, String... entries) throws IOException {
    Path file = directory.resolve(label + ".zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
      for (String entry : entries) {
        out.putNextEntry(new ZipEntry(entry));
        out.write("{}".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    assertThrows(ValidationException.class, () -> zip().read(file), label);
  }
}
