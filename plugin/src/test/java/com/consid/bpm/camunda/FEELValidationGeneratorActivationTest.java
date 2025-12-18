package com.consid.bpm.camunda;

import com.consid.automation.camunda.FEELValidationGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering activation-condition generation.
 */
public class FEELValidationGeneratorActivationTest extends AbstractFEELValidationGeneratorIntegrationTest {

    @Test
    public void test_customers_direct_activation_does_generate_valid_feel_as_expected() throws IOException {
        runActivationScenario(
            "customers-direct-valid",
            "openapi/customers-direct-api.json",
            "feel/customers-direct-expected-feel.txt",
            "payloads/customers-direct-variables.json",
            true
        );
    }

    @Test
    public void test_customers_direct_activation_does_fail_validation_for_empty_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-direct-invalid",
            "openapi/customers-direct-api.json",
            "feel/customers-direct-expected-feel.txt",
            "payloads/customers-direct-invalid-variables.json",
            false
        );
    }

    @Test
    public void test_customers_referenced_activation_does_generate_valid_feel_as_expected() throws IOException {
        runActivationScenario(
            "customers-referenced-valid",
            "openapi/customers-referenced-api.json",
            "feel/customers-referenced-expected-feel.txt",
            "payloads/customers-referenced-variables.json",
            true
        );
    }

    @Test
    public void test_customers_allOf_activation_does_generate_valid_feel_as_expected() throws IOException {
        runActivationScenario(
            "customers-allOf-valid",
            "openapi/customers-allOf-api.json",
            "feel/customers-allOf-expected-feel.txt",
            "payloads/customers-allOf-variables.json",
            true
        );
    }

    @Test
    public void test_customers_oneOf_activation_does_generate_valid_feel_as_expected() throws IOException {
        runActivationScenario(
            "customers-oneOf-valid",
            "openapi/customers-oneOf-api.json",
            "feel/customers-oneOf-expected-feel.txt",
            "payloads/customers-oneOf-variables.json",
            true
        );
    }

    @Test
    public void test_customers_anyOf_activation_does_generate_valid_feel_as_expected() throws IOException {
        runActivationScenario(
            "customers-anyOf-valid",
            "openapi/customers-anyOf-api.json",
            "feel/customers-anyOf-expected-feel.txt",
            "payloads/customers-anyOf-variables.json",
            true
        );
    }

    private void runActivationScenario(String scenarioId,
                                       String openApiResource,
                                       String expectedFeelResource,
                                       String payloadResource,
                                       boolean expectedValid) throws IOException {
        Path specFile = resolveResourcePath(openApiResource);
        Path outputFile = tempDir.resolve(scenarioId + ".feel");

        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath().toString())
            .withOutputFilePath(outputFile.toAbsolutePath().toString())
            .withResponse(false)
            .build();

        generator.generate();

        String actualOutput = Files.readString(outputFile).stripTrailing();
        String expectedOutput = readResourceFile(expectedFeelResource).stripTrailing();
        assertThat(actualOutput)
            .as("Generated FEEL output should match the expected snapshot for %s", scenarioId)
            .isEqualTo(expectedOutput);

        Map<String, Object> payload = loadJsonResource(payloadResource);
        Map<String, Object> context = buildEvaluationContext(payload);
        List<String> expressions = extractFeelExpressions(actualOutput);
        assertThat(expressions)
            .as("FEEL expressions should exist for %s", scenarioId)
            .isNotEmpty();

        for (String expression : expressions) {
            var parseResult = FEEL_ENGINE.parseExpression(expression);
            assertThat(parseResult.isRight())
                .as("FEEL expression should be parsable for %s", scenarioId)
                .isTrue();

            var evaluation = FEEL_ENGINE.evalExpression(expression, context);
            assertThat(evaluation.isRight())
                .as("FEEL expression should evaluate for %s", scenarioId)
                .withFailMessage(() -> "FEEL evaluation failure: " + evaluation.left().get())
                .isTrue();

            Boolean activationValue = (Boolean) evaluation.getOrElse(null);
            assertThat(activationValue)
                .as("Activation result should be %s for %s",
                    expectedValid ? "true" : "false",
                    scenarioId)
                .isEqualTo(expectedValid);
        }
    }
}
