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
 * Each scenario builds FEEL from a fixture and evaluates it against a payload;
 * the assertion is the FEEL engine's verdict, not the textual output.
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

    @Test
    public void test_customers_conditional_activation_does_pass_when_trigger_field_is_absent_as_expected() throws IOException {
        runActivationScenario(
            "customers-conditional-no-shipping",
            "openapi/customers-conditional-api.json",
            "payloads/customers-conditional-no-shipping-variables.json",
            true
        );
    }

    @Test
    public void test_customers_conditional_activation_does_fail_when_trigger_present_and_dependent_missing_as_expected() throws IOException {
        runActivationScenario(
            "customers-conditional-missing-carrier",
            "openapi/customers-conditional-api.json",
            "payloads/customers-conditional-missing-carrier-variables.json",
            false
        );
    }

    @Test
    public void test_customers_value_conditional_activation_does_pass_when_trigger_value_does_not_match_as_expected() throws IOException {
        runActivationScenario(
            "customers-value-conditional-invoice",
            "openapi/customers-value-conditional-api.json",
            "payloads/customers-value-conditional-invoice-variables.json",
            true
        );
    }

    @Test
    public void test_customers_value_conditional_activation_does_fail_when_trigger_value_matches_and_dependent_missing_as_expected() throws IOException {
        runActivationScenario(
            "customers-value-conditional-card-without-number",
            "openapi/customers-value-conditional-api.json",
            "payloads/customers-value-conditional-card-without-number-variables.json",
            false
        );
    }

    @Test
    public void test_customers_value_conditional_activation_does_pass_when_trigger_value_matches_and_dependent_present_as_expected() throws IOException {
        runActivationScenario(
            "customers-value-conditional-card-with-number",
            "openapi/customers-value-conditional-api.json",
            "payloads/customers-value-conditional-card-with-number-variables.json",
            true
        );
    }

    @Test
    public void test_orders_nested_conditional_activation_does_pass_when_trigger_value_does_not_match_as_expected() throws IOException {
        runActivationScenario(
            "orders-no-delivery",
            "openapi/orders-conditional-nested-api.json",
            "payloads/orders-no-delivery-variables.json",
            true
        );
    }

    @Test
    public void test_orders_nested_conditional_activation_does_fail_when_trigger_matches_and_inner_field_missing_as_expected() throws IOException {
        runActivationScenario(
            "orders-needs-delivery-without-address",
            "openapi/orders-conditional-nested-api.json",
            "payloads/orders-needs-delivery-without-address-variables.json",
            false
        );
    }

    @Test
    public void test_orders_nested_conditional_activation_does_pass_when_trigger_matches_and_all_required_present_as_expected() throws IOException {
        runActivationScenario(
            "orders-needs-delivery-with-address",
            "openapi/orders-conditional-nested-api.json",
            "payloads/orders-needs-delivery-with-address-variables.json",
            true
        );
    }

    @Test
    public void test_constraints_activation_does_pass_for_valid_payload_with_empty_required_array_as_expected() throws IOException {
        // notes is required but has no minItems → empty list satisfies it (the case that motivated this feature).
        runActivationScenario(
            "customers-constraints-valid",
            "openapi/customers-constraints-api.json",
            "payloads/customers-constraints-valid-variables.json",
            true
        );
    }

    @Test
    public void test_constraints_activation_does_fail_when_array_under_min_items_as_expected() throws IOException {
        runActivationScenario(
            "customers-constraints-tags-empty",
            "openapi/customers-constraints-api.json",
            "payloads/customers-constraints-tags-empty-variables.json",
            false
        );
    }

    @Test
    public void test_constraints_activation_does_fail_when_array_over_max_items_as_expected() throws IOException {
        runActivationScenario(
            "customers-constraints-tags-too-many",
            "openapi/customers-constraints-api.json",
            "payloads/customers-constraints-tags-too-many-variables.json",
            false
        );
    }

    @Test
    public void test_constraints_activation_does_fail_when_string_under_min_length_as_expected() throws IOException {
        runActivationScenario(
            "customers-constraints-handle-too-short",
            "openapi/customers-constraints-api.json",
            "payloads/customers-constraints-handle-too-short-variables.json",
            false
        );
    }

    @Test
    public void test_constraints_activation_does_fail_when_string_over_max_length_as_expected() throws IOException {
        runActivationScenario(
            "customers-constraints-handle-too-long",
            "openapi/customers-constraints-api.json",
            "payloads/customers-constraints-handle-too-long-variables.json",
            false
        );
    }

    @Test
    public void test_constraints_activation_does_fail_when_string_pattern_does_not_match_as_expected() throws IOException {
        runActivationScenario(
            "customers-constraints-code-pattern-miss",
            "openapi/customers-constraints-api.json",
            "payloads/customers-constraints-code-pattern-miss-variables.json",
            false
        );
    }

    private void runActivationScenario(String scenarioId,
                                       String openApiResource,
                                       String payloadResource,
                                       boolean expectedValid) throws IOException {
        // given
        Path specFile = resolveResourcePath(openApiResource);
        Path outputFile = tempDir.resolve(scenarioId + ".feel");
        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(false)
            .build();
        Map<String, Object> context = buildEvaluationContext(loadJsonResource(payloadResource));

        // when
        generator.generate();
        String actualOutput = Files.readString(outputFile).stripTrailing();
        List<String> expressions = extractFeelExpressions(actualOutput);

        // then
        assertThat(expressions)
            .as("FEEL expressions should exist for %s", scenarioId)
            .isNotEmpty();
        for (String expression : expressions) {
            var evaluation = FEEL_ENGINE.evalExpression(expression, context);
            assertThat(evaluation.isRight())
                .as("FEEL expression should evaluate for %s", scenarioId)
                .withFailMessage(() -> "FEEL evaluation failure: " + evaluation.left().get())
                .isTrue();
            assertThat((Boolean) evaluation.getOrElse(null))
                .as("Activation result for %s", scenarioId)
                .isEqualTo(expectedValid);
        }
    }
}
