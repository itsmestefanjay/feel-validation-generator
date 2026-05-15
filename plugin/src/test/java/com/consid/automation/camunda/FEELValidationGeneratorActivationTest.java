package com.consid.automation.camunda;

import com.consid.automation.camunda.internal.feel.*;
import com.consid.automation.camunda.internal.model.*;
import com.consid.automation.camunda.internal.openapi.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering activation-condition generation.
 * Drives the shared {@link AbstractFEELValidationGeneratorIntegrationTest#scenarios()
 * scenario table} — each row generates FEEL, evaluates every emitted expression
 * against the Camunda FEEL engine, and asserts the engine's verdict matches the
 * scenario's expected boolean. The assertion is the engine's result, not the
 * textual output.
 */
public class FEELValidationGeneratorActivationTest extends AbstractFEELValidationGeneratorIntegrationTest {

    @ParameterizedTest
    @MethodSource("com.consid.automation.camunda.AbstractFEELValidationGeneratorIntegrationTest#scenarios")
    public void test_activation_does_evaluate_to_expected_verdict_as_expected(Scenario scenario) throws IOException {
        // given
        Path specFile = resolveResourcePath(scenario.openApiResource());
        Path outputFile = tempDir.resolve(scenario.id() + ".feel");
        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath())
            .withOutputFilePath(outputFile.toAbsolutePath())
            .withResponse(false)
            .build();
        Map<String, Object> context = buildEvaluationContext(loadJsonResource(scenario.payloadResource()));

        // when
        generator.generate();
        String actualOutput = Files.readString(outputFile).stripTrailing();
        List<String> expressions = extractFeelExpressions(actualOutput);

        // then
        assertThat(expressions)
            .as("FEEL expressions should exist for %s", scenario.id())
            .isNotEmpty();
        for (String expression : expressions) {
            var evaluation = FEEL_ENGINE.evalExpression(expression, context);
            assertThat(evaluation.isRight())
                .as("FEEL expression should evaluate for %s", scenario.id())
                .withFailMessage(() -> "FEEL evaluation failure: " + evaluation.left().get())
                .isTrue();
            assertThat((Boolean) evaluation.getOrElse(null))
                .as("Activation result for %s", scenario.id())
                .isEqualTo(scenario.expectedValid());
        }
    }
}
