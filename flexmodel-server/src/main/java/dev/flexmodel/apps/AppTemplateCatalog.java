package dev.flexmodel.apps;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.dto.AppTemplate;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static dev.flexmodel.apps.dto.AppPackage.EXTENSION;
import static dev.flexmodel.apps.dto.AppPackage.MAX_PACKAGE_BYTES;

/**
 * 只读的内置应用模板目录。
 *
 * <p>启动时读取 app-templates/index.json 并逐个校验包可用性；损坏的条目只告警跳过，
 * 不阻断服务启动。包文件名固定为 app-templates/&lt;id&gt;.flexmodel.zip，索引与请求都无法提供任意路径。</p>
 */
@ApplicationScoped
@Slf4j
public class AppTemplateCatalog {
  static final String BASE = "app-templates/";
  private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

  /**
   * 平台种子目录：每个实例都会初始化 admin 用户与 admin/developer 角色，是模板唯一允许引用的身份。
   */
  private static final Map<String, Object> BASELINE_DIRECTORY = Map.of(
    "members", List.of(Map.of("id", "admin")),
    "roles", List.of(Map.of("id", "admin"), Map.of("id", "developer")),
    "organizations", List.of());
  private static final AppPackageValidator.SystemRecordLookup NO_SYSTEM_RECORDS = (form, id) -> false;

  @Inject
  ObjectMapper json;
  @Inject
  AppPackageZip packages;
  @Inject
  AppPackageValidator validator;

  private volatile List<Template> templates = List.of();
  private volatile Map<String, Template> byId = Map.of();

  public record Template(AppTemplate.Summary summary, String resourceName) {
  }

  void onStart(@Observes StartupEvent event) {
    load(this::classpath);
  }

  /**
   * 目录列表：顺序由 index.json 的 order 决定，仅返回展示摘要。
   */
  public List<AppTemplate.Summary> summaries() {
    return templates.stream().map(Template::summary).toList();
  }

  public Template require(String id) {
    Template template = id == null ? null : byId.get(id);
    if (template == null) throw new WebApplicationException("模板不存在: " + id, 404);
    return template;
  }

  InputStream open(Template template) throws IOException {
    InputStream input = classpath(template.resourceName());
    if (input == null) throw new IllegalStateException("内置模板资源缺失: " + template.resourceName());
    return input;
  }

  void load(Function<String, InputStream> resources) {
    List<Template> loaded = new ArrayList<>();
    Map<String, Template> index = new LinkedHashMap<>();
    for (AppTemplate.Entry entry : readIndex(resources)) {
      Template template = loadEntry(entry, resources);
      if (template == null) continue;
      if (index.putIfAbsent(template.summary().id(), template) != null) {
        log.warn("跳过重复的应用模板ID: {}", template.summary().id());
        continue;
      }
      loaded.add(template);
    }
    templates = List.copyOf(loaded);
    byId = Map.copyOf(index);
    log.info("Loaded {} built-in app template(s)", templates.size());
  }

  private List<AppTemplate.Entry> readIndex(Function<String, InputStream> resources) {
    try (InputStream input = resources.apply(BASE + "index.json")) {
      if (input == null) {
        log.warn("应用模板索引不存在: {}", BASE + "index.json");
        return List.of();
      }
      AppTemplate.Index index = strict().readValue(input, AppTemplate.Index.class);
      if (index == null || index.templates() == null) return List.of();
      return index.templates().stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing((AppTemplate.Entry entry) -> entry.order() == null ? Integer.MAX_VALUE : entry.order())
          .thenComparing(entry -> String.valueOf(entry.id())))
        .toList();
    } catch (IOException | RuntimeException error) {
      log.error("应用模板索引无法解析，模板目录为空: {}", error.getMessage());
      return List.of();
    }
  }

  private Template loadEntry(AppTemplate.Entry entry, Function<String, InputStream> resources) {
    String id = entry.id();
    if (id == null || !ID.matcher(id).matches()) {
      log.warn("跳过非法应用模板ID: {}", id);
      return null;
    }
    if (entry.name() == null || entry.name().isBlank() || entry.name().length() > 100) {
      log.warn("跳过缺少名称的应用模板: {}", id);
      return null;
    }
    String resourceName = BASE + id + EXTENSION;
    try (InputStream input = resources.apply(resourceName)) {
      if (input == null) {
        log.warn("应用模板包缺失: {}", resourceName);
        return null;
      }
      AppPackageZip.Content content = packages.read(bounded(input));
      AppPackageValidator.Result validation = validator.validate(content, BASELINE_DIRECTORY, NO_SYSTEM_RECORDS);
      if (!validation.valid()) {
        log.warn("应用模板 {} 在平台基线目录下不可导入: {}", id, validation.issues());
        return null;
      }
      AppTemplate.Summary summary = new AppTemplate.Summary(id, entry.name().trim(),
        entry.description() == null ? "" : entry.description(), entry.category(), entry.iconName(),
        content.forms() == null ? 0 : content.forms().size(),
        content.data() != null && !content.data().isEmpty(),
        content.manifest().exportedAt());
      return new Template(summary, resourceName);
    } catch (IOException | RuntimeException error) {
      log.warn("跳过不可用的应用模板 {}: {}", id, error.getMessage());
      return null;
    }
  }

  private ObjectMapper strict() {
    return json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
  }

  private InputStream bounded(InputStream input) {
    return new FilterInputStream(input) {
      private long total;

      @Override
      public int read() throws IOException {
        int value = super.read();
        if (value >= 0) add(1);
        return value;
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        int count = super.read(bytes, offset, length);
        if (count > 0) add(count);
        return count;
      }

      private void add(long count) {
        total += count;
        if (total > MAX_PACKAGE_BYTES)
          throw new IllegalStateException("内置模板包超过 " + (MAX_PACKAGE_BYTES / 1024 / 1024) + " MiB");
      }
    };
  }

  private InputStream classpath(String name) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return loader == null
      ? AppTemplateCatalog.class.getClassLoader().getResourceAsStream(name)
      : loader.getResourceAsStream(name);
  }
}
