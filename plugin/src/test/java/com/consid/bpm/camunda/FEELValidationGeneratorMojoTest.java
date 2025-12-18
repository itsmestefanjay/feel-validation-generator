package com.consid.bpm.camunda;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.consid.automation.camunda.FEELValidationGeneratorMojo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for FEELValidationGeneratorMojo.
 * Tests the Mojo's configuration mapping, parameter handling, and execution logic.
 */
public class FEELValidationGeneratorMojoTest {

    @TempDir
    Path tempDir;

    private FEELValidationGeneratorMojo mojo;
    private Log mockLog;

    @BeforeEach
    void setUp() {
        mojo = new FEELValidationGeneratorMojo();
        mockLog = mock(Log.class);
        mojo.setLog(mockLog);
    }

    @Test
    public void test_mojo_does_execute_successfully_with_valid_openapi_spec_as_expected() throws Exception {
        // Setup
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output.feel");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST,PUT,PATCH"
        );

        // Execute
        mojo.execute();

        // Assert - only verify that the file is created and logging is correct
        assertThat(Files.exists(outputFile))
            .as("Output file should be created")
            .isTrue();

        verify(mockLog).info("Starting FEEL Validation Generator");
        verify(mockLog).info("FEEL validation generation completed successfully");
    }

    @Test
    public void test_mojo_does_map_openapi_spec_parameter_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output.feel");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST"
        );

        mojo.execute();

        verify(mockLog).info("Input OpenAPI spec: " + specFile.toAbsolutePath().toString());
    }

    @Test
    public void test_mojo_does_map_output_file_parameter_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output.feel");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST"
        );

        mojo.execute();

        verify(mockLog).info("Output file: " + outputFile.toAbsolutePath().toString());
        verify(mockLog).info("Output written to: " + outputFile.toAbsolutePath().toString());
    }

    @Test
    public void test_mojo_does_map_add_response_flag_false_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output-no-response.feel");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST"
        );

        mojo.execute();

        // Just verify execution completed successfully - actual output format is tested in integration tests
        assertThat(Files.exists(outputFile))
            .as("Output file should be created with addResponse=false")
            .isTrue();
    }

    @Test
    public void test_mojo_does_map_add_response_flag_true_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output-with-response.feel");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            true,
            "POST"
        );

        mojo.execute();

        // Just verify execution completed successfully - actual output format is tested in integration tests
        assertThat(Files.exists(outputFile))
            .as("Output file should be created with addResponse=true")
            .isTrue();
    }

    @Test
    public void test_mojo_does_map_methods_parameter_and_filters_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/customers-direct-api.json");
        Path outputFile = tempDir.resolve("output-methods.feel");

        // Only scan POST methods
        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST"
        );

        mojo.execute();

        String output = Files.readString(outputFile);
        assertThat(output)
            .as("Output should contain expressions for POST methods")
            .isNotEmpty();
    }

    @Test
    public void test_mojo_does_handle_comma_separated_methods_with_whitespace_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output-trimmed-methods.feel");

        // Methods with extra whitespace
        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST , PUT , PATCH"
        );

        mojo.execute();

        assertThat(Files.exists(outputFile))
            .as("Mojo should handle methods with extra whitespace")
            .isTrue();
    }

    @Test
    public void test_mojo_does_throw_failure_when_openapi_spec_missing_as_expected() {
        setMojoFields(
            "/nonexistent/api-spec.yaml",
            tempDir.resolve("output.feel").toAbsolutePath().toString(),
            false,
            "POST"
        );

        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("OpenAPI specification file not found");
    }

    @Test
    public void test_mojo_does_throw_execution_exception_on_unexpected_error_as_expected() throws Exception {
        // Use a valid OpenAPI spec but force an IO error by writing to a directory
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            tempDir.toAbsolutePath().toString(), // Directory instead of file
            false,
            "POST"
        );

        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Error generating FEEL validations");
    }

    @Test
    public void test_mojo_does_log_execution_info_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output.feel");

        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST"
        );

        mojo.execute();

        // Verify key logging calls were made
        verify(mockLog).info("Starting FEEL Validation Generator");
        verify(mockLog).info("FEEL validation generation completed successfully");
    }

    @Test
    public void test_mojo_does_include_default_methods_as_expected() throws Exception {
        Path specFile = copyResourceToTempDir("openapi/responses-direct-api.json");
        Path outputFile = tempDir.resolve("output-default-methods.feel");

        // Set methods to default (POST,PUT,PATCH)
        setMojoFields(
            specFile.toAbsolutePath().toString(),
            outputFile.toAbsolutePath().toString(),
            false,
            "POST,PUT,PATCH"
        );

        mojo.execute();

        assertThat(Files.exists(outputFile))
            .as("Should handle default methods")
            .isTrue();
    }

    @Test
    public void test_mojo_does_propagate_mojo_failure_exception_as_expected() {
        setMojoFields(
            "/nonexistent/spec.yaml",
            tempDir.resolve("output.feel").toAbsolutePath().toString(),
            false,
            "POST"
        );

        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoFailureException.class);
    }

    // Helper methods

    private void setMojoFields(String openApiSpec, String outputFile, boolean addResponse, String methods) {
        try {
            var openApiField = FEELValidationGeneratorMojo.class.getDeclaredField("openApiSpec");
            openApiField.setAccessible(true);
            openApiField.set(mojo, openApiSpec);

            var outputFileField = FEELValidationGeneratorMojo.class.getDeclaredField("outputFile");
            outputFileField.setAccessible(true);
            outputFileField.set(mojo, outputFile);

            var addResponseField = FEELValidationGeneratorMojo.class.getDeclaredField("addResponse");
            addResponseField.setAccessible(true);
            addResponseField.set(mojo, addResponse);

            var methodsField = FEELValidationGeneratorMojo.class.getDeclaredField("methods");
            methodsField.setAccessible(true);
            methodsField.set(mojo, methods);

            var successField = FEELValidationGeneratorMojo.class.getDeclaredField("successStatusCode");
            successField.setAccessible(true);
            successField.set(mojo, 201);

            var failField = FEELValidationGeneratorMojo.class.getDeclaredField("failStatusCode");
            failField.setAccessible(true);
            failField.set(mojo, 400);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set Mojo fields", e);
        }
    }

    private Path copyResourceToTempDir(String resourceName) throws IOException {
        URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
        assertThat(resourceUrl)
            .as(resourceName + " should exist in test resources")
            .isNotNull();

        Path destination = tempDir.resolve(resourceName);
        Path parentDir = destination.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
        try (InputStream inputStream = resourceUrl.openStream()) {
            Files.write(destination, inputStream.readAllBytes());
        }
        return destination;
    }
}
