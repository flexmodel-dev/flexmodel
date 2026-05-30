package dev.flexmodel.flow.plugin;

import dev.flexmodel.flow.common.util.IdGenerator;

public interface IdGeneratorPlugin extends Plugin {
  IdGenerator getIdGenerator();
}
