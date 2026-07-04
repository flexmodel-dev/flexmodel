package dev.flexmodel.query;

import dev.flexmodel.annotation.ModelField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * @author cjbi
 */
public class Expressions {

  private static final Logger log = LoggerFactory.getLogger(Expressions.class);

  public static Predicate TRUE = new Predicate(null, null, null) {
    @Override
    public Map<String, Object> toMap() {
      return Collections.emptyMap(); // 返回空 JSON，表示默认条件
    }
  };

  public static <T> FilterExpression<T> field(String fieldName) {
    return new FilterExpression<>(fieldName);
  }

  /**
   * 通过方法引用获取字段表达式
   * 例如：Expressions.field(User::getName)
   */
  public static <T, R> FilterExpression<R> field(SFunction<T, R> getter) {
    String fieldName = getFieldNameFromGetter(getter);
    Class<?> targetClass = getTargetClass(getter);

    // 如果能够获取到目标类，尝试获取注解中的字段名
    if (targetClass != null) {
      String annotatedFieldName = getAnnotatedFieldName(targetClass, fieldName);
      if (annotatedFieldName != null) {
        fieldName = annotatedFieldName;
      }
    } else {
      // 如果无法获取目标类，记录警告信息但继续使用提取的字段名
      log.error("警告: 无法获取目标类，将使用提取的字段名: " + fieldName);
    }

    return new FilterExpression<>(fieldName);
  }

  public static <T, R> String getFieldName(SFunction<T, R> getter) {
    String fieldName = getFieldNameFromGetter(getter);
    Class<?> targetClass = getTargetClass(getter);
    // 如果能够获取到目标类，尝试获取注解中的字段名
    if (targetClass != null) {
      String annotatedFieldName = getAnnotatedFieldName(targetClass, fieldName);
      if (annotatedFieldName != null) {
        fieldName = annotatedFieldName;
      }
    } else {
      // 如果无法获取目标类，记录警告信息但继续使用提取的字段名
      log.error("警告: 无法获取目标类，将使用提取的字段名: " + fieldName);
    }
    return fieldName;
  }

  /**
   * 从getter方法中提取字段名
   * 例如：User::getName -> "name"
   */
  private static <T, R> String getFieldNameFromGetter(SFunction<T, R> getter) {
    String methodName = getMethodName(getter);
    // 简单的字段名提取逻辑，假设getter方法遵循JavaBean规范
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
    } else if (methodName.startsWith("is") && methodName.length() > 2) {
      return methodName.substring(2, 3).toLowerCase() + methodName.substring(3);
    }
    return methodName;
  }

  @FunctionalInterface
  public interface SFunction<T, R> extends java.io.Serializable {
    R apply(T source);
  }

  /**
   * 获取带注解的字段名
   */
  private static <T, R> String getAnnotatedFieldName(Class<T> targetClass, String fieldName) {
    if (targetClass == null || fieldName == null) {
      return null;
    }

    try {
      // 查找字段并获取 @ModelField 注解
      Field field = findFieldByName(targetClass, fieldName);
      if (field != null) {
        // 首先尝试直接获取注解
        ModelField modelField = field.getAnnotation(ModelField.class);
        if (modelField != null) {
          return modelField.value();
        }

        // 如果直接获取失败，可能是由于动态代理或字节码增强导致的
        // 遍历所有注解，查找 ModelField 注解
        Annotation[] annotations = field.getAnnotations();
        for (Annotation annotation : annotations) {
          // 检查注解是否为 ModelField 或其代理类
          if (isModelFieldAnnotation(annotation)) {
            // 通过反射获取注解的值
            try {
              Method valueMethod = annotation.getClass().getMethod("value");
              Object value = valueMethod.invoke(annotation);
              if (value instanceof String) {
                return (String) value;
              }
            } catch (Exception e) {
              log.warn("获取注解值失败: " + e.getMessage());
            }
          }
        }
      }

      return null;
    } catch (Exception e) {
      // 如果获取注解失败，记录错误信息并返回原始字段名
      log.error("获取字段注解失败: " + e.getMessage() +
                "，类: " + targetClass.getName() +
                "，字段: " + fieldName);
      return null;
    }
  }

  /**
   * 检查注解是否为 ModelField 注解
   */
  private static boolean isModelFieldAnnotation(Annotation annotation) {
    if (annotation == null) {
      return false;
    }

    // 直接检查注解类型
    if (annotation.annotationType() == ModelField.class) {
      return true;
    }

    // 检查注解的类型名称（处理代理类的情况）
    String annotationClassName = annotation.annotationType().getName();
    if (ModelField.class.getName().equals(annotationClassName)) {
      return true;
    }

    // 检查注解类是否实现了 ModelField 接口（代理类可能实现原始注解接口）
    try {
      // 通过反射检查注解类是否包含 value 方法
      Method valueMethod = annotation.getClass().getMethod("value");
      return valueMethod != null;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * 尝试从 serializable lambda 中提取 SerializedLambda。
   * <p>
   * 策略：
   * <ol>
   *   <li>先尝试 {@code getDeclaredMethod("writeReplace")} — JVM 模式正常路径</li>
   *   <li>失败则遍历 {@code getDeclaredMethods()} — GraalVM native image 中
   *       lambda 类的 writeReplace 可能无法按名称查找，但遍历所有方法可找到</li>
   *   <li>尝试 {@code getMethod}（查找 public 继承方法）</li>
   * </ol>
   */
  private static SerializedLambda tryGetSerializedLambda(SFunction<?, ?> fn) {
    Class<?> lambdaClass = fn.getClass();
    Method writeReplace = null;

    // 策略 1: getDeclaredMethod（JVM 模式正常路径）
    try {
      writeReplace = lambdaClass.getDeclaredMethod("writeReplace");
    } catch (NoSuchMethodException e) {
      // GraalVM native image: getDeclaredMethod 找不到，尝试其他方式
    }

    // 策略 2: 遍历 getDeclaredMethods()（GraalVM 中按名查找可能失败但遍历可行）
    if (writeReplace == null) {
      try {
        for (Method m : lambdaClass.getDeclaredMethods()) {
          if ("writeReplace".equals(m.getName()) && m.getParameterCount() == 0) {
            writeReplace = m;
            break;
          }
        }
      } catch (Exception ignored) {
        // 如果 getDeclaredMethods() 也失败，继续尝试
      }
    }

    // 策略 3: 从父类/接口查找（writeReplace 可能是继承来的）
    if (writeReplace == null) {
      try {
        writeReplace = lambdaClass.getMethod("writeReplace");
      } catch (NoSuchMethodException ignored) {
        // writeReplace 不是 public 方法，这很正常
      }
    }

    if (writeReplace == null) {
      log.warn("无法获取 lambda 的 writeReplace 方法 (native image 限制): {}",
        lambdaClass.getName());
      return null;
    }

    try {
      writeReplace.setAccessible(true);
      Object result = writeReplace.invoke(fn);
      if (result instanceof SerializedLambda serializedLambda) {
        return serializedLambda;
      }
    } catch (Exception e) {
      log.error("提取 SerializedLambda 失败: {}", e.getMessage());
    }
    return null;
  }

  /**
   * 获取目标类（从 serializable lambda 中提取）
   */
  private static <T, R> Class<?> getTargetClass(SFunction<T, R> fn) {
    SerializedLambda serializedLambda = tryGetSerializedLambda(fn);
    if (serializedLambda == null) {
      return null;
    }
    try {
      String implClass = serializedLambda.getImplClass();
      String className = implClass.replace('/', '.');

      try {
        return Class.forName(className);
      } catch (ClassNotFoundException e1) {
        try {
          return Class.forName(className, false, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e2) {
          try {
            return Class.forName(className, false, fn.getClass().getClassLoader());
          } catch (ClassNotFoundException e3) {
            ClassLoader ctxLoader = Thread.currentThread().getContextClassLoader();
            if (ctxLoader != null) {
              try {
                return Class.forName(className, false, ctxLoader);
              } catch (ClassNotFoundException e4) {
                log.error("无法加载类: {}，错误: {}", className, e4.getMessage());
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("获取目标类失败: {}", e.getMessage());
    }
    return null;
  }

  /**
   * 根据字段名查找字段
   */
  private static Field findFieldByName(Class<?> clazz, String fieldName) {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Field[] fields = clazz.getDeclaredFields();
      for (Field field : fields) {
        if (field.getName().equals(fieldName)) {
          return field;
        }
      }
      return null;
    }
  }

  /**
   * 从 serializable lambda 方法引用中提取方法名。
   * 例如：User::getName -> "getName"
   * <p>
   * GraalVM native image 兼容：当 writeReplace 不可用时返回 null。
   */
  public static String getMethodName(SFunction<?, ?> fn) {
    SerializedLambda serializedLambda = tryGetSerializedLambda(fn);
    if (serializedLambda != null) {
      return serializedLambda.getImplMethodName();
    }
    throw new RuntimeException(
      "Failed to extract method name from lambda. " +
        "java.lang.invoke.SerializedLambda.writeReplace() is not available in native image. " +
        "Ensure reachability-metadata.json includes SerializedLambda with unsafeAllocated=true. " +
        "Lambda class: " + fn.getClass().getName());
  }

}
