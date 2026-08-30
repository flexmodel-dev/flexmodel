package dev.flexmodel.observability;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.session.Session;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义 OTel SpanExporter —— 将 Quarkus 自动埋点产生的 Span 落库到项目级 {@code f_span} 表。
 * <p>
 * 通过 Quarkus OTel 的 {@code cdi} exporter 机制接入：设置
 * {@code quarkus.otel.traces.exporter=cdi} 后，{@code SpanExporterCDIProvider} 会收集
 * 所有实现 {@link SpanExporter} 的 CDI bean 并 composite 调用。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>failsafe —— 任何异常都不抛出，避免拖垮 OTel 批处理线程和应用请求</li>
 *   <li>project_id 从 HTTP 根 span 的 {@code url.path} 提取，同批次按 trace_id 回填子 span</li>
 *   <li>按 project_id 分组，分别写入对应项目库的 f_span 表（项目级隔离）</li>
 *   <li>attributes 序列化为 JSON 列</li>
 * </ul>
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FmSpanExporter extends AbstractRepository implements SpanExporter {

  private static final Pattern PROJECT_PATH = Pattern.compile("/(?:api/)?projects/([^/]+)/");

  @Override
  public io.opentelemetry.sdk.common.CompletableResultCode export(java.util.Collection<SpanData> spans) {
    if (spans == null || spans.isEmpty()) {
      return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
    }
    try {
      // 第一遍：按 trace_id 收集根 span 的 projectId（来自 HTTP url.path 属性）
      Map<String, String> traceProject = new HashMap<>();
      for (SpanData s : spans) {
        String pid = extractProjectId(s);
        if (pid != null) {
          traceProject.putIfAbsent(s.getTraceId(), pid);
        }
      }
      // 按 projectId 分组构建 Span 记录，分别写入对应项目库
      Map<String, List<Span>> byProject = new HashMap<>();
      for (SpanData s : spans) {
        String pid = traceProject.get(s.getTraceId());
        if (pid == null) {
          continue;
        }
        Span rec = toSpan(s);
        if (rec != null) {
          byProject.computeIfAbsent(pid, k -> new ArrayList<>()).add(rec);
        }
      }
      if (!byProject.isEmpty()) {
        persist(byProject);
      }
    } catch (Throwable t) {
      // exporter 绝不能抛出，否则会中断 OTel 批处理
      log.debug("Failed to export spans to f_span", t);
    }
    return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
  }

  @Override
  public io.opentelemetry.sdk.common.CompletableResultCode flush() {
    return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
  }

  @Override
  public io.opentelemetry.sdk.common.CompletableResultCode shutdown() {
    return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess();
  }

  @PreDestroy
  public void onDestroy() {
    try {
      shutdown();
    } catch (Throwable ignored) {
    }
  }

  /**
   * 按 projectId 分组，分别写入对应项目库的 f_span 表。
   */
  private void persist(Map<String, List<Span>> byProject) {
    for (var entry : byProject.entrySet()) {
      try (Session session = getProjectSession(entry.getKey())) {
        for (Span rec : entry.getValue()) {
          session.dsl().mergeInto(Span.class).values(rec).execute();
        }
      } catch (Throwable t) {
        log.debug("Failed to persist {} spans for project {}", entry.getValue().size(), entry.getKey(), t);
      }
    }
  }

  private Span toSpan(SpanData s) {
    try {
      Span rec = new Span();
      rec.setTraceId(s.getTraceId());
      rec.setSpanId(s.getSpanId());
      rec.setParentId(s.getParentSpanId());
      rec.setName(s.getName());
      rec.setKind(s.getKind().name());
      rec.setStartTime(s.getStartEpochNanos());
      rec.setDurationNs(s.getEndEpochNanos() - s.getStartEpochNanos());
      rec.setAttributes(toAttributesJson(s.getAttributes()));
      rec.setStatus(s.getStatus().getStatusCode().name());
      return rec;
    } catch (Throwable t) {
      return null;
    }
  }

  private String extractProjectId(SpanData s) {
    try {
      Attributes attrs = s.getAttributes();
      // 优先从手动设置的自定义属性提取（Quartz Job 等非 HTTP 入口）
      String customPid = attrs.get(AttributeKey.stringKey("flexmodel.project_id"));
      if (customPid != null) {
        return customPid;
      }
      String urlPath = attrs.get(AttributeKey.stringKey("url.path"));
      if (urlPath == null) {
        urlPath = attrs.get(AttributeKey.stringKey("http.target"));
      }
      if (urlPath == null) {
        return null;
      }
      Matcher m = PROJECT_PATH.matcher(urlPath);
      return m.find() ? m.group(1) : null;
    } catch (Throwable t) {
      return null;
    }
  }

  private Object toAttributesJson(Attributes attrs) {
    if (attrs == null || attrs.isEmpty()) {
      return null;
    }
    Map<String, Object> map = new HashMap<>();
    attrs.forEach((key, value) -> {
      if (value != null) {
        map.put(key.getKey(), value.toString());
      }
    });
    return JsonUtils.toJsonString(map);
  }
}
