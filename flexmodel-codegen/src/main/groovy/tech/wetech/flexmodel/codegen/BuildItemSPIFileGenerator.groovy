package dev.flexmodel.codegen

import dev.flexmodel.codegen.GenerationContext
import groovy.util.logging.Log
import dev.flexmodel.BuildItem

import java.nio.file.Path

/**
 * @author cjbi
 */
@Log
class BuildItemSPIFileGenerator extends AbstractGenerator {

  @Override
  String getTargetFile(GenerationContext context, String targetDirectory) {
    return Path.of(
      targetDirectory,
      "target/classes/META-INF/services",
      BuildItem.class.getName()
    ).toString()
  }

  @Override
  void write(PrintWriter out, GenerationContext context) {
    context.variables.get("buildItems").each {
      out.println it
    }
  }
}
