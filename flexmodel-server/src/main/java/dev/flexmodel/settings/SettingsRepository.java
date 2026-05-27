package dev.flexmodel.settings;

/**
 * @author cjbi
 */
public interface SettingsRepository {

  Settings saveSettings(Settings settings);

  Settings getSettings();

}
