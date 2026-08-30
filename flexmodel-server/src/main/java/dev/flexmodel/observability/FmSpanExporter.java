package dev.flexmodel.observability;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义 OTel SpanExporter —— 将 Quarkus 自动埋点产生的 Span 落库到平台级 {@code f_span} 表。
 * <p>
 * 通过 Quarkus OTel 的 {@code cdi} exporter 机制接入：设置
 * {@code quarkus.otel.traces.exporter=cdi} 后，{@code SpanExporterCDIProvider} 会收集
 * 所有实现 {@link SpanExporter} 的 CDI bean 并 composite 调用。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>failsafe —— 任何异常都不抛出，避免拖垮 OTel 批处理线程和应用请求</li>
 *   <li>project_id 从 HTTP 根 span 的 {@code url.path} 提取，同批次按 trace_id 回填子 span</li>
 *   <li>attributes 序列化为 JSON 列</li>
 * </ul>
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FmSpanExporter implements SpanExporter {

  private static final Pattern PROJECT_PATH = Pattern.compile("/(?:api/)?projects/([^/]+)/");

  @Inject
  SessionFactory sessionFactory;

  @Override
  public CompletableResultCode export(java.util.Collection<SpanData> spans) {
    if (spans == null || spans.isEmpty()) {
      return CompletableResultCode.ofSuccess();
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
      List<Span> records = new ArrayList<>(spans.size());
      for (SpanData s : spans) {
        Span rec = toSpan(s, traceProject.get(s.getTraceId()));
        if (rec != null) {
          records.add(rec);
        }
      }
      if (!records.isEmpty()) {
        persist(records);
      }
    } catch (Throwable t) {
      // exporter 绝不能抛出，否则会中断 OTel 批处理
      log.debug("Failed to export spans to f_span", t);
    }
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @PreDestroy
  public void onDestroy() {
    try {
      shutdown();
    } catch (Throwable ignored) {
    }
  }

  private void persist(List<Span> records) {
    try (Session session = sessionFactory.createSession()) {
      for (Span rec : records) {
        session.dsl().mergeInto(Span.class).values(rec).execute();
      }
    } catch (Throwable t) {
      log.debug("Failed to persist {} spans", records.size(), t);
    }
  }

  private Span toSpan(SpanData s, String projectId) {
    try {
      Span rec = new Span();
      rec.setTraceId(s.getTraceId());
      rec.setSpanId(s.getSpanId());
      rec.setParentId(s.getParentSpanId());
      rec.setName(s.getName());
      rec.setKind(s.getKind().name());
      rec.setProjectId(projectId);
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
