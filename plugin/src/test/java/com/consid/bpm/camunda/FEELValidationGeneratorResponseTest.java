package com.consid.bpm.camunda;

import com.consid.automation.camunda.FEELValidationGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering response-expression generation.
 */
public class FEELValidationGeneratorResponseTest extends AbstractFEELValidationGeneratorIntegrationTest {

    private static final Comparator<Number> NUMBER_COMPARATOR =
        (a, b) -> Double.compare(a.doubleValue(), b.doubleValue());

    @Test
    public void test_responses_direct_response_does_generate_valid_feel_as_expected() throws IOException {
        runResponseScenario(
            "responses-direct-valid",
            "openapi/responses-direct-api.json",
            "feel/responses-direct-expected-feel.txt",
            "payloads/responses-direct-variables.json",
            "response/responses-direct-valid-body.json",
            true
        );
    }

    @Test
    public void test_responses_direct_response_does_fail_for_invalid_payload_as_expected() throws IOException {
        runResponseScenario(
            "responses-direct-invalid",
            "openapi/responses-direct-api.json",
            "feel/responses-direct-expected-feel.txt",
            "payloads/responses-direct-invalid-variables.json",
            "response/responses-direct-invalid-body.json",
            false
        );
    }

    private void runResponseScenario(String scenarioId,
                                     String openApiResource,
                                     String expectedFeelResource,
                                     String payloadResource,
                                     String expectedBodyResource,
                                     boolean expectedValid) throws IOException {
        Path specFile = resolveResourcePath(openApiResource);
        Path outputFile = tempDir.resolve(scenarioId + ".feel");

        var generator = FEELValidationGenerator.builder()
            .withOpenApiPath(specFile.toAbsolutePath().toString())
            .withOutputFilePath(outputFile.toAbsolutePath().toString())
            .withResponse(true)
            .build();

        generator.generate();

        String actualOutput = Files.readString(outputFile).stripTrailing();
        String expectedOutput = readResourceFile(expectedFeelResource).stripTrailing();
        assertThat(actualOutput)
            .as("Generated FEEL output should match the expected snapshot for %s", scenarioId)
            .isEqualTo(expectedOutput);

        Map<String, Object> payload = loadJsonResource(payloadResource);
        Map<String, Object> expectedBody = loadJsonResource(expectedBodyResource);
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

            Map<String, Object> feelContext = toJavaMap(evaluation.getOrElse(null));
            Boolean valid = (Boolean) feelContext.get("isValid");
            assertThat(valid)
                .as("Response validity should be %s for %s", expectedValid, scenarioId)
                .isEqualTo(expectedValid);

            Object status = feelContext.get("statusCode");
            int statusCode = status instanceof Number number
                ? number.intValue()
                : Integer.parseInt(status.toString());
            assertThat(statusCode)
                .as("Status code should reflect validity for %s", scenarioId)
                .isEqualTo(expectedValid ? 201 : 400);

            Map<String, Object> actualBody = castToMap(normalizeValue(feelContext.get("body")));
            Map<String, Object> normalizedExpected = castToMap(normalizeValue(expectedBody));
            assertThat(actualBody)
                .as("Response body should match expected snapshot for %s", scenarioId)
                .usingRecursiveComparison()
                .withComparatorForType(NUMBER_COMPARATOR, Number.class)
                .isEqualTo(normalizedExpected);
        }
    }
}
