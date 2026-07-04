package dev.flexmodel.flow.common.util;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * 脚本执行上下文：封装输入输出数据
 * context = {
 *     input: {},
 *     output: {}
 * }
 */
@Getter
@Setter
@ToString
public class RequestScriptContext {

  public static final String SCRIPT_CONTEXT_KEY = "context";

  private final String projectId;

  private Map<String, Object> input;

  private Map<String, Object> output;

  public RequestScriptContext(String projectId) {
    this.projectId = projectId;
  }

  @SuppressWarnings("all")
  public Map<String, Object> buildContextMap() {
    Map<String, Object> context = new HashMap<>();

    if (input != null) {
      context.put("input", input);
    }
    if (output != null) {
      context.put("output", output);
    }

    return context;
  }

  /**
   * 从 Map 中同步回 input/output
   *
   * @param contextMap JavaScript 执行后的 context Map
   */
  @SuppressWarnings("all")
  public void syncFromMap(Map<String, Object> contextMap) {
    Object inputObj = contextMap.get("input");
    if (inputObj instanceof Map) {
      this.input = (Map<String, Object>) inputObj;
    }
    Object outputObj = contextMap.get("output");
    if (outputObj instanceof Map) {
      this.output = (Map<String, Object>) outputObj;
    }
  }

}
