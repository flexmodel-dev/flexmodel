package dev.flexmodel.codegen;

import dev.flexmodel.JsonUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Object serialization utilities.
 * <p>
 * Uses Jackson JSON serialization (wrapped in Base64) instead of Java native
 * serialization ({@link ObjectOutputStream}/{@link ObjectInputStream}) to avoid
 * {@code serialVersionUID} incompatibility between JDK versions. JDK classes
 * like {@link java.util.HashMap} and {@link java.util.ArrayList} change their
 * {@code serialVersionUID} across releases, which breaks deserialization when
 * the codegen JDK differs from the runtime JDK (e.g., in GraalVM native-image).
 * </p>
 *
 * @author cjbi
 */
public class ObjectUtils {

  private ObjectUtils() {
  }

  /**
   * Serialize an object to a Base64-encoded JSON string.
   * <p>
   * The serialized format embeds the class name as the first line, followed by
   * the JSON representation. This allows deserialization to reconstruct the
   * exact object type without requiring an explicit type parameter.
   * </p>
   */
  public static String serialize(Object obj) throws IOException {
    String json = JsonUtils.toJsonString(obj);
    String className = obj.getClass().getName();
    String combined = className + "\n" + json;
    return Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Deserialize a Base64-encoded JSON string back to an object.
   * <p>
   * The first line of the decoded data is the fully qualified class name,
   * which is used to determine the target type for Jackson deserialization.
   * </p>
   *
   * @param str Base64-encoded serialized data
   * @return the deserialized object
   * @throws IOException            if I/O error occurs
   * @throws ClassNotFoundException if the target class cannot be found
   */
  public static Object deserialize(String str) throws IOException, ClassNotFoundException {
    byte[] data = Base64.getDecoder().decode(str);
    String combined = new String(data, StandardCharsets.UTF_8);
    int newlineIdx = combined.indexOf('\n');
    if (newlineIdx < 0) {
      throw new IOException("Invalid serialized format: missing class name header");
    }
    String className = combined.substring(0, newlineIdx);
    String json = combined.substring(newlineIdx + 1);
    @SuppressWarnings("unchecked")
    Class<?> clazz = Class.forName(className);
    return JsonUtils.parseToObject(json, clazz);
  }

}
