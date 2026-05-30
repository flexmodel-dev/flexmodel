package dev.flexmodel.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * @author cjbi
 */
@Getter
@AllArgsConstructor
@ToString
public class SettingsChanged {
  private Settings message;
}
