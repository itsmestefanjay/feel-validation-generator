package com.consid.automation.camunda;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuleFileWriter.
 * Tests that content is written, parent directories are created, and existing files are overwritten.
 */
class RuleFileWriterTest {

    @TempDir
    Path tempDir;

    private final RuleFileWriter writer = new RuleFileWriter();

    @Test
    void test_write_does_create_file_when_parent_exists_as_expected() throws IOException {
        Path target = tempDir.resolve("output.feel");

        writer.write(target, "content");

        assertThat(Files.readString(target))
            .as("Writer should write the given content to the target file")
            .isEqualTo("content");
    }

    @Test
    void test_write_does_create_missing_parent_directories_as_expected() throws IOException {
        Path target = tempDir.resolve("nested/dirs/output.feel");

        writer.write(target, "content");

        assertThat(Files.exists(target.getParent()))
            .as("Writer should create missing parent directories")
            .isTrue();
        assertThat(Files.readString(target))
            .as("Writer should write the given content after creating parent directories")
            .isEqualTo("content");
    }

    @Test
    void test_write_does_overwrite_existing_file_as_expected() throws IOException {
        Path target = tempDir.resolve("output.feel");
        Files.writeString(target, "previous");

        writer.write(target, "replacement");

        assertThat(Files.readString(target))
            .as("Writer should overwrite an existing file rather than appending")
            .isEqualTo("replacement");
    }
}
