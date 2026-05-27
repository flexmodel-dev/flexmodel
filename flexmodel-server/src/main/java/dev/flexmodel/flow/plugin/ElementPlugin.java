package dev.flexmodel.flow.plugin;

import dev.flexmodel.flow.executor.ElementExecutor;
import dev.flexmodel.flow.validator.ElementValidator;

public interface ElementPlugin extends Plugin {
  String ELEMENT_TYPE_PREFIX = "turbo.plugin.element_type.";

  ElementExecutor getElementExecutor();

  ElementValidator getElementValidator();

  Integer getFlowElementType();
}
