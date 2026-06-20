package dev.flexmodel.reflect;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.field.RelationField;
import dev.flexmodel.session.Session;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author cjbi
 */
public class LazyObjProxy {

  private static final Logger log = LoggerFactory.getLogger(LazyObjProxy.class);

  // 缓存生成的代理类，key 为 "原始类名@模型名"，避免批量查询时 Metaspace OOM
  private static final Map<String, Class<?>> proxyClassCache = new ConcurrentHashMap<>();

  @SuppressWarnings("unchecked")
  public static <T> T createProxy(T obj, String modelName, Session session) {
    try {
      EntityDefinition entity = (EntityDefinition) session.schema().getModel(modelName);
      String cacheKey = obj.getClass().getName() + "@" + modelName;
      Class<?> subClazz = proxyClassCache.computeIfAbsent(cacheKey, k -> buildProxyClass(obj.getClass(), entity));

      T proxy = (T) JsonUtils.convertValue(obj, subClazz);
      // 通过反射设置实例级别的拦截器和元数据
      subClazz.getField("lazyInterceptor").set(proxy,
        new LazyLoadInterceptor(modelName, JsonUtils.convertValue(obj, Map.class), session));
      subClazz.getField("entityInfoValue").set(proxy, entity);
      subClazz.getField("originClassValue").set(proxy, obj.getClass());
      return proxy;
    } catch (Throwable e) {
      log.trace("Failed to create lazy class, message: {}", e.toString());
      return obj;
    }
  }

  /**
   * 构建代理类（带缓存），使用字段存储实例级数据而非固化在类定义中
   */
  private static Class<?> buildProxyClass(Class<?> originClass, EntityDefinition entity) {
    return new ByteBuddy()
      .subclass(originClass)
      .implement(ProxyInterface.class)
      .defineField("lazyInterceptor", LazyLoadInterceptor.class, Modifier.PUBLIC)
      .defineField("entityInfoValue", EntityDefinition.class, Modifier.PUBLIC)
      .defineField("originClassValue", Class.class, Modifier.PUBLIC)
      .method(ElementMatchers.namedOneOf(getLazyMethods(entity)))
      .intercept(MethodDelegation.toField("lazyInterceptor"))
      .method(ElementMatchers.named("entityInfo"))
      .intercept(FieldAccessor.ofField("entityInfoValue"))
      .method(ElementMatchers.named("originClass"))
      .intercept(FieldAccessor.ofField("originClassValue"))
      .make()
      .load(originClass.getClassLoader())
      .getLoaded();
  }

  public static <T> List<T> createProxyList(List<T> list, String modelName, Session session) {
    List<T> result = new ArrayList<>();
    for (T o : list) {
      result.add(createProxy(o, modelName, session));
    }
    return result;
  }

  private static String[] getLazyMethods(EntityDefinition entity) {
    List<String> methodNames = new ArrayList<>();
    entity.getFields().forEach(field -> {
      if (field instanceof RelationField) {
        methodNames.add("get" + ReflectionUtils.toUpperCamelCase(field.getName()));
      }
    });
    return methodNames.toArray(new String[]{});
  }


}
