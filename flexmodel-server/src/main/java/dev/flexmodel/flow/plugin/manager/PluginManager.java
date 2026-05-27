package dev.flexmodel.flow.plugin.manager;

import dev.flexmodel.flow.plugin.Plugin;

import java.util.List;

public interface PluginManager {
  List<Plugin> getPlugins();

  <T extends Plugin> List<T> getPluginsFor(Class<T> pluginInterface);

  Integer countPlugins();

  <T extends Plugin> Boolean containsPlugin(Class<T> pluginInterface, String pluginName);
}
