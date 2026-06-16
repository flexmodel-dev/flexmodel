package dev.flexmodel.codegen;

import dev.flexmodel.BuildItem;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the SPI service file for BuildItem implementations.
 *
 * @author cjbi
 */
public class BuildItemSPIFileGenerator extends AbstractGenerator {

    @Override
    public String getTargetFile(GenerationContext context, String targetDirectory) {
        return Path.of(
            targetDirectory,
            "target/classes/META-INF/services",
            BuildItem.class.getName()
        ).toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(PrintWriter out, GenerationContext context) {
        List<String> buildItems = context.getVariable("buildItems");
        if (buildItems != null) {
            for (String item : buildItems) {
                out.println(item);
            }
        }
    }
}
