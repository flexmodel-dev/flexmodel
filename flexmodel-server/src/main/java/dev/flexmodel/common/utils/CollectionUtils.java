package dev.flexmodel.common.utils;

import java.util.*;

/**
 * @author cjbi
 */
public class CollectionUtils {

  /**
   * Return {@code true} if the supplied Collection is {@code null} or empty.
   * Otherwise, return {@code false}.
   *
   * @param collection the Collection to check
   * @return whether the given Collection is empty
   */
  public static boolean isEmpty(Collection<?> collection) {
    return (collection == null || collection.isEmpty());
  }


  @SafeVarargs
  public static <E> Set<E> asSet(E... elements) {
    if (elements == null || elements.length == 0) {
      return Collections.emptySet();
    }

    if (elements.length == 1) {
      return Collections.singleton(elements[0]);
    }

    LinkedHashSet<E> set = new LinkedHashSet<E>(elements.length * 4 / 3 + 1);
    Collections.addAll(set, elements);
    return set;
  }


  /**
   * Return {@code true} if the supplied Map is {@code null} or empty.
   * Otherwise, return {@code false}.
   *
   * @param map the Map to check
   * @return whether the given Map is empty
   */
  public static boolean isEmpty(Map<?, ?> map) {
    return (map == null || map.isEmpty());
  }

  @SafeVarargs
  public static <E> List<E> asList(E... elements) {
    if (elements == null || elements.length == 0) {
      return Collections.emptyList();
    }

    // Integer overflow does not occur when a large array is passed in because the list array already exists
    return Arrays.asList(elements);
  }

}

