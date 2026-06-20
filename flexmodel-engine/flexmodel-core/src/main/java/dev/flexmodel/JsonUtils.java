package dev.flexmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.flexmodel.annotation.ModelClass;
import dev.flexmodel.supports.jackson.FlexmodelCoreModule;
import dev.flexmodel.supports.jackson.ModelFieldAnnotationIntrospector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

/**
 * @author cjbi
 */
public class JsonUtils {

  private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

  private static final JsonMapper JSON;

  /**
   * 专用于 Java 对象间转换的 Mapper（不使用 @ModelField 命名），
   * 解决 Entity → DTO 转换时因 @ModelField 蛇形命名与 DTO 驼峰命名不匹配导致字段丢失的问题。
   */
  private static final ObjectMapper CONVERT_MAPPER;

  private JsonUtils() {

  }

  static {
    JsonMapper.Builder builder = new JsonMapper().rebuild();
    //
//        JSON.configure(SerializationFeature.INDENT_OUTPUT, false);
    //不显示为null的字段
//    builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    builder.enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES);
    builder.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    builder.disable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    builder.disable(FAIL_ON_EMPTY_BEANS);
    builder.addModule(new JavaTimeModule());
    builder.addModule(new FlexmodelCoreModule());
    builder.annotationIntrospector(new ModelFieldAnnotationIntrospector());
    ServiceLoader.load(Module.class).forEach(builder::addModule);
    JSON = builder.build();

    // 为 Entity → DTO / DTO → Entity 等 Java 对象间转换构建独立 Mapper，
    // 使用标准 JacksonAnnotationIntrospector 以保持驼峰命名一致
    CONVERT_MAPPER = JSON.rebuild()
        .annotationIntrospector(new JacksonAnnotationIntrospector())
        .build();
  }

  public static String toJsonString(Object obj) {
    try {
      return JSON.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize object to JSON", e);
    }
  }

  public static <T> T parseToObject(String jsonString, Class<T> cls) {
    try {
      if (jsonString == null) {
        return null;
      }
      return JSON.readValue(jsonString, cls);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * 将对象转换为目标类型。
   * <p>
   * 当目标类型是实体（标注了 {@link ModelClass}）或 Map/List 时，
   * 使用带 {@link ModelFieldAnnotationIntrospector} 的 Mapper，保证
   * 数据库列名（@ModelField）在序列化和反序列化中正确匹配。
   * <p>
   * 当目标类型是普通 DTO（无模型注解）时，使用标准 Mapper，
   * 避免 @ModelField 蛇形命名与 DTO 驼峰命名不匹配导致字段值丢失。
   */
  @SuppressWarnings("all")
  public static <T> T convertValue(Object fromValue, Class<T> cls) {
    if (fromValue != null && cls.isAssignableFrom(fromValue.getClass())) {
      return (T) fromValue;
    }
    if (needsModelAnnotationIntrospector(cls)) {
      return JSON.convertValue(fromValue, cls);
    }
    return CONVERT_MAPPER.convertValue(fromValue, cls);
  }

  /**
   * 判断目标类型是否需要 @ModelField 命名（实体、Map、List 等与数据库直接交互的类型）。
   */
  private static boolean needsModelAnnotationIntrospector(Class<?> cls) {
    if (cls == null) {
      return false;
    }
    // Map / List 可能用于数据库操作，保留 @ModelField 命名
    if (Map.class.isAssignableFrom(cls) || List.class.isAssignableFrom(cls)) {
      return true;
    }
    // 检查实体注解
    return cls.isAnnotationPresent(ModelClass.class);
  }

  public static <T> T updateValue(T target, Object source) {
    try {
      return JSON.updateValue(target, source);
    } catch (JsonMappingException e) {
      throw new IllegalArgumentException("Failed to update value from source", e);
    }
  }

  @SuppressWarnings("all")
  public static <T> List<T> parseToList(String json, Class<T> clz) {
    return convertValueList(parseToObject(json, List.class), clz);
  }

  public static <T> List<T> convertValueList(List<?> fromValues, Class<T> cls) {
    List<T> list = new ArrayList<>();
    for (Object fromValue : fromValues) {
      list.add(convertValue(fromValue, cls));
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseToMap(String jsonString) {
    return parseToObject(jsonString, Map.class);
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> parseToMapList(String jsonString) {
    return parseToObject(jsonString, List.class);
  }

}
