package dev.flexmodel.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Abstract base class for code generators.
 * Subclasses override write/writeModel/writeEnum to produce output.
 *
 * @author cjbi
 */
public abstract class AbstractGenerator implements Generator {

    private static final Logger log = LoggerFactory.getLogger(AbstractGenerator.class);

    /**
     * Subclasses should override to return the target file path, or null to skip file writing.
     */
    public String getTargetFile(GenerationContext context, String targetDirectory) {
        return null;
    }

    @Override
    public List<File> generate(GenerationContext context, String dir) {
        List<File> files = new ArrayList<>();
        // Generate and write main content
        files.addAll(processAndWrite(context, dir, this::write));
        while (context.nextModel()) {
            files.addAll(processAndWrite(context, dir, this::writeModel));
        }
        while (context.nextEnum()) {
            files.addAll(processAndWrite(context, dir, this::writeEnum));
        }
        return files;
    }

    @Override
    public List<String> generate(GenerationContext context) {
        List<String> outputs = new ArrayList<>();
        outputs.add(collect(context, this::write));
        while (context.nextModel()) {
            outputs.add(collect(context, this::writeModel));
        }
        while (context.nextEnum()) {
            outputs.add(collect(context, this::writeEnum));
        }
        // Filter out empty strings
        return outputs.stream().filter(s -> s != null && !s.isEmpty()).toList();
    }

    /**
     * Execute the writer function, write content to file, and return the file list.
     */
    private List<File> processAndWrite(GenerationContext context, String dir,
                                       BiConsumer<PrintWriter, GenerationContext> writerFunc) {
        String content = collect(context, writerFunc);
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        String targetPath = getTargetFile(context, dir);
        if (targetPath == null || targetPath.isEmpty()) {
            return List.of();
        }
        File file = new File(targetPath);
        File parentDir = file.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        } catch (IOException e) {
            log.error("Failed to write file: {}", targetPath, e);
        }
        return List.of(file);
    }

    /**
     * Execute the writer function and return the collected output as a string.
     */
    private String collect(GenerationContext context, BiConsumer<PrintWriter, GenerationContext> writerFunc) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            writerFunc.accept(pw, context);
        }
        return sw.toString();
    }

    /**
     * Subclasses may override: write general (non-model, non-enum) content.
     */
    public void write(PrintWriter out, GenerationContext context) {
        // default no-op
    }

    /**
     * Subclasses may override: write model-specific content.
     */
    public void writeModel(PrintWriter out, GenerationContext context) {
        // default no-op
    }

    /**
     * Subclasses may override: write enum-specific content.
     */
    public void writeEnum(PrintWriter out, GenerationContext context) {
        // default no-op
    }
}
