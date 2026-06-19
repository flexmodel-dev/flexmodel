package dev.flexmodel.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author cjbi
 */
@AllArgsConstructor
@Getter
public class NativeQueryResult {
  private long time;
  private Object result;
}
