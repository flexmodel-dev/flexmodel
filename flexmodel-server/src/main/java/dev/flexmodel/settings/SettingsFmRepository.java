package dev.flexmodel.settings;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.System;
import dev.flexmodel.codegen.entity.Config;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
@ApplicationScoped
public class SettingsFmRepository implements SettingsRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  @SuppressWarnings("unchecked")
  public Settings saveSettings(Settings settings) {
    try (Session session = sessionFactory.createSession()) {
      Map<String, Object> settingsMap = JsonUtils.convertValue(settings, Map.class);
      settingsMap.forEach((key, value) -> {
        if (value != null) {

          Config config = session.dsl()
            .selectFrom(Config.class)
            .where(System.config.key.eq(key))
            .executeOne();

          if (config == null) {
            config = new Config();
          }
          config.setKey(key);
          config.setValue(JsonUtils.toJsonString(value));

          session.dsl()
            .mergeInto(Config.class)
            .values(config)
            .execute();
        }
      });
    }
    return settings;
  }

  @Override
  public Settings getSettings() {
    try (Session session = sessionFactory.createSession()) {
      List<Config> list = session.dsl()
        .selectFrom(Config.class)
        .execute();

      Map<String, Object> settingsMap = new HashMap<>();
      for (Config config : list) {
        settingsMap.put(config.getKey(), JsonUtils.parseToObject(config.getValue(), Object.class));
      }
      return JsonUtils.convertValue(settingsMap, Settings.class);
    }
  }
}
