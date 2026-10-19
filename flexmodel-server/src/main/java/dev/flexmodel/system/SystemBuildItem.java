package dev.flexmodel.system;

import java.util.Set;

public class SystemBuildItem extends dev.flexmodel.codegen.System {

  @Override
  public Set<String> getPreservedMetadataModels() {
    return SystemFormService.SYSTEM_FORM_NAMES;
  }
}
