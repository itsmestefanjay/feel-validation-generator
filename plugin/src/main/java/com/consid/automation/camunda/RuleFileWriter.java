package com.consid.automation.camunda;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes rendered FEEL output to disk, creating parent directories on demand.
 */
final class RuleFileWriter {

    void write(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content);
    }
}
