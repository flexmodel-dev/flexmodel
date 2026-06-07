package dev.flexmodel.flow.common.util;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class InstanceDataUtil {

  private InstanceDataUtil() {
  }

  /**
   * 从JSON字符串解析实例数据Map
   * @param instanceDataStr JSON字符串
   * @return 实例数据Map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getInstanceDataMap(String instanceDataStr) {
    if (StringUtils.isBlank(instanceDataStr)) {
      return new HashMap<>();
    }
    return JsonUtils.parseToObject(instanceDataStr, Map.class);
  }

  /**
   * 将实例数据Map转换为JSON字符串
   * @param instanceDataMap 实例数据Map
   * @return JSON字符串
   */
  public static String getInstanceDataStr(Map<String, Object> instanceDataMap) {
    if (instanceDataMap == null || instanceDataMap.isEmpty()) {
      return JsonUtils.toJsonString(new HashMap<>());
    }
    return JsonUtils.toJsonString(instanceDataMap);
  }
}
