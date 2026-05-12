package com.consid.automation.camunda;

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
    public void test_customers_direct_activation_does_evaluate_true_for_valid_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-direct-valid",
            "openapi/customers-direct-api.json",
            "payloads/customers-direct-variables.json",
            true
        );
    }

    @Test
    public void test_customers_direct_activation_does_evaluate_false_for_empty_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-direct-invalid",
            "openapi/customers-direct-api.json",
            "payloads/customers-direct-invalid-variables.json",
            false
        );
    }

    @Test
    public void test_customers_referenced_activation_does_evaluate_true_for_valid_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-referenced-valid",
            "openapi/customers-referenced-api.json",
            "payloads/customers-referenced-variables.json",
            true
        );
    }

    @Test
    public void test_customers_allOf_activation_does_evaluate_true_for_valid_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-allOf-valid",
            "openapi/customers-allOf-api.json",
            "payloads/customers-allOf-variables.json",
            true
        );
    }

    @Test
    public void test_customers_oneOf_activation_does_evaluate_true_for_valid_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-oneOf-valid",
            "openapi/customers-oneOf-api.json",
            "payloads/customers-oneOf-variables.json",
            true
        );
    }

    @Test
    public void test_customers_anyOf_activation_does_evaluate_true_for_valid_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-anyOf-valid",
            "openapi/customers-anyOf-api.json",
            "payloads/customers-anyOf-variables.json",
            true
        );
    }

    @Test
    public void test_customers_shared_component_activation_does_evaluate_true_for_valid_payload_as_expected() throws IOException {
        runActivationScenario(
            "customers-shared-valid",
            "openapi/customers-shared-api.json",
            "payloads/customers-shared-variables.json",
            true
        );
    }

    private void runActivationScenario(String scenarioId,
                                       String openApiResource,
                                       String payloadResource,
                                       boolean expectedValid) throws IOException {
        Path specFile = resolveResourcePath(openApiResource);
        Path outputFile = tempDir.resolve(scenarioId + ".feel");

        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(false)
            .build();

        generator.generate();

        String actualOutput = Files.readString(outputFile).stripTrailing();
        Map<String, Object> payload = loadJsonResource(payloadResource);
        Map<String, Object> context = buildEvaluationContext(payload);
        List<String> expressions = extractFeelExpressions(actualOutput);
        assertThat(expressions)
            .as("FEEL expressions should exist for %s", scenarioId)
            .isNotEmpty();

        for (String expression : expressions) {
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
