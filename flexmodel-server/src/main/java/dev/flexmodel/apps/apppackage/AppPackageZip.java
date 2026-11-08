package dev.flexmodel.apps.apppackage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import dev.flexmodel.apps.form.dto.AppFormRecord;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static dev.flexmodel.apps.apppackage.dto.AppPackage.*;

/**
 * .flexmodel.zip 包的受限读取与写出。
 *
 * <p>读取不信任 entry 元数据：以实际解压字节计数执行 ZIP bomb 限制，
 * 拒绝路径穿越、重复 entry、未知文件与超深 JSON；解析使用拒绝未知字段的严格映射器。</p>
 */
@ApplicationScoped
public class AppPackageZip {
  private static final Pattern FORM_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
  private static final Set<String> FIXED_ENTRIES = Set.of(
    MANIFEST_FILE, APP_FILE, FORMS_FILE, NAVIGATION_FILE, WORKBENCHES_FILE, FLOWS_FILE, AUTHORIZATIONS_FILE);

  @Inject
  public ObjectMapper json;

  private ObjectMapper strict;

  /**
   * 包内容：结构文件 + 可选记录数据 + 原始字节（用于摘要）。
   */
  public record Content(
    Manifest manifest,
    AppInfo app,
    List<AppFormRecord> forms,
    List<AppNavRecord> navigation,
    List<WorkbenchExport> workbenches,
    List<Map<String, Object>> flows,
    List<AuthorizationExport> authorizations,
    Map<String, List<Map<String, Object>>> data,
    Map<String, byte[]> rawFiles
  ) {
  }

  private synchronized ObjectMapper strict() {
    if (strict == null) {
      strict = json.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
    }
    return strict;
  }

  /**
   * 写出包文件：内容顺序固定；返回实际压缩字节数，超限时删除并报错。
   */
  public long write(Path target, Content content) throws IOException {
    try {
      Map<String, byte[]> entries = new LinkedHashMap<>();
      entries.put(MANIFEST_FILE, json.writeValueAsBytes(content.manifest()));
      entries.put(APP_FILE, json.writeValueAsBytes(content.app()));
      entries.put(FORMS_FILE, json.writeValueAsBytes(content.forms()));
      entries.put(NAVIGATION_FILE, json.writeValueAsBytes(content.navigation()));
      entries.put(WORKBENCHES_FILE, json.writeValueAsBytes(content.workbenches()));
      entries.put(FLOWS_FILE, json.writeValueAsBytes(content.flows()));
      entries.put(AUTHORIZATIONS_FILE, json.writeValueAsBytes(content.authorizations()));
      for (var entry : new TreeMap<>(content.data()).entrySet()) {
        entries.put(DATA_DIRECTORY + entry.getKey() + ".json", json.writeValueAsBytes(new DataFile(entry.getValue())));
      }
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
        for (var entry : entries.entrySet()) {
          zip.putNextEntry(new ZipEntry(entry.getKey()));
          zip.write(entry.getValue());
          zip.closeEntry();
        }
      }
      long size = Files.size(target);
      if (size > MAX_PACKAGE_BYTES) {
        throw new ValidationException("应用包超过 " + (MAX_PACKAGE_BYTES / 1024 / 1024) + " MiB 限制");
      }
      return size;
    } catch (IOException | RuntimeException error) {
      try {
        Files.deleteIfExists(target);
      } catch (IOException suppressed) {
        error.addSuppressed(suppressed);
      }
      throw error;
    }
  }

  /**
   * 防御性读取：限制条目数、路径、解压字节与 JSON 深度后解析为受限 DTO。
   */
  public Content read(Path zip) throws IOException {
    try (InputStream input = Files.newInputStream(zip)) {
      return read(input);
    }
  }

  /**
   * 流式读取入口：内置模板直接来自 classpath，不落临时文件。
   */
  public Content read(InputStream source) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    long totalBytes = 0;
    try (ZipInputStream stream = new ZipInputStream(source, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = stream.getNextEntry()) != null) {
        if (files.size() >= MAX_ENTRIES) {
          throw new ValidationException("应用包条目超过 " + MAX_ENTRIES + " 个");
        }
        String name = entry.getName();
        requireEntryName(name);
        if (files.containsKey(name)) {
          throw new ValidationException("应用包存在重复文件: " + name);
        }
        byte[] bytes = readEntry(stream, name);
        files.put(name, bytes);
        totalBytes += bytes.length;
        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
          throw new ValidationException("应用包解压后超过 100 MiB");
        }
      }
    }
    for (String required : FIXED_ENTRIES) {
      if (!files.containsKey(required)) {
        throw new ValidationException("应用包缺少必需文件: " + required);
      }
    }
    for (String name : files.keySet()) {
      if (jsonDepth(files.get(name)) > MAX_JSON_DEPTH) {
        throw new ValidationException("应用包 JSON 嵌套超过 " + MAX_JSON_DEPTH + " 层: " + name);
      }
    }
    return parse(files);
  }

  private byte[] readEntry(ZipInputStream stream, String name) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    long total = 0;
    int read;
    while ((read = stream.read(chunk)) >= 0) {
      total += read;
      if (total > MAX_ENTRY_BYTES) {
        throw new ValidationException("应用包单文件超过 50 MiB: " + name);
      }
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }

  private void requireEntryName(String name) {
    if (name == null || name.isBlank()
      || name.startsWith("/") || name.startsWith("\\")
      || name.contains("\\") || name.contains("..")
      || name.matches("^[A-Za-z]:.*")) {
      throw new ValidationException("应用包文件路径不合法: " + name);
    }
    if (FIXED_ENTRIES.contains(name)) return;
    if (name.startsWith(DATA_DIRECTORY) && name.endsWith(".json")) {
      String formName = name.substring(DATA_DIRECTORY.length(), name.length() - ".json".length());
      if (FORM_NAME_PATTERN.matcher(formName).matches()) return;
    }
    throw new ValidationException("应用包包含未知文件: " + name);
  }

  private Content parse(Map<String, byte[]> files) throws IOException {
    ObjectMapper mapper = strict();
    Manifest manifest = parseValue(mapper, files.get(MANIFEST_FILE), Manifest.class);
    AppInfo app = parseValue(mapper, files.get(APP_FILE), AppInfo.class);
    List<AppFormRecord> forms = parseList(mapper, files.get(FORMS_FILE), AppFormRecord.class);
    List<AppNavRecord> navigation = parseList(mapper, files.get(NAVIGATION_FILE), AppNavRecord.class);
    List<WorkbenchExport> workbenches = parseList(mapper, files.get(WORKBENCHES_FILE), WorkbenchExport.class);
    List<AuthorizationExport> authorizations = parseList(mapper, files.get(AUTHORIZATIONS_FILE), AuthorizationExport.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> flows = parseValue(mapper, files.get(FLOWS_FILE), List.class);
    Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
    for (var entry : files.entrySet()) {
      if (!entry.getKey().startsWith(DATA_DIRECTORY)) continue;
      String formName = entry.getKey().substring(DATA_DIRECTORY.length(), entry.getKey().length() - ".json".length());
      DataFile file = parseValue(mapper, entry.getValue(), DataFile.class);
      data.put(formName, file.rows() == null ? List.of() : file.rows());
    }
    return new Content(manifest, app, forms, navigation, workbenches,
      flows == null ? List.of() : flows, authorizations, data, files);
  }

  private <T> T parseValue(ObjectMapper mapper, byte[] bytes, Class<T> type) throws IOException {
    try {
      return mapper.readValue(bytes, type);
    } catch (IOException error) {
      String message = String.valueOf(error.getMessage()).split("\n")[0];
      throw new ValidationException("应用包 JSON 结构不合法: " + message, error);
    }
  }

  private <T> List<T> parseList(ObjectMapper mapper, byte[] bytes, Class<T> type) throws IOException {
    CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
    try {
      return mapper.readValue(bytes, listType);
    } catch (IOException error) {
      String message = String.valueOf(error.getMessage()).split("\n")[0];
      throw new ValidationException("应用包 JSON 结构不合法: " + message, error);
    }
  }

  /**
   * 计划摘要：按 entry 名排序对全部原始字节做 SHA-256。
   */
  public String planHash(Map<String, byte[]> rawFiles) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String name : new TreeMap<>(rawFiles).keySet()) {
        digest.update(name.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(rawFiles.get(name));
        digest.update((byte) 0);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("生成计划摘要失败", error);
    }
  }

  public String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("生成摘要失败", error);
    }
  }

  /**
   * 原始字节的结构化 JSON 深度扫描（识别字符串与转义，解析前执行）。
   */
  static int jsonDepth(byte[] bytes) {
    int depth = 0;
    int max = 0;
    boolean inString = false;
    boolean escaped = false;
    for (byte raw : bytes) {
      char current = (char) (raw & 0xFF);
      if (inString) {
        if (escaped) escaped = false;
        else if (current == '\\') escaped = true;
        else if (current == '"') inString = false;
        continue;
      }
      if (current == '"') inString = true;
      else if (current == '{' || current == '[') max = Math.max(max, ++depth);
      else if (current == '}' || current == ']') depth = Math.max(0, depth - 1);
    }
    return max;
  }
}
