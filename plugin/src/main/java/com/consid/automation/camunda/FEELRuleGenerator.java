package com.consid.automation.camunda;

import java.util.List;
import java.util.Map;

/**
 * Centralizes all FEEL-specific rule building and rendering logic so that the rest
 * of the generator remains focused on OpenAPI traversal.
 */
public class FEELRuleGenerator implements ValidationRuleBuilder {

    private final boolean addResponse;
    private final FEELExpressionBuilder expressionBuilder;
    private final int successStatusCode;
    private final int failureStatusCode;

    public FEELRuleGenerator(boolean addResponse) {
        this(addResponse, 201, 400);
    }

    public FEELRuleGenerator(boolean addResponse, int successStatusCode, int failureStatusCode) {
        this(addResponse, successStatusCode, failureStatusCode, new FEELExpressionBuilder());
    }

    FEELRuleGenerator(boolean addResponse,
                      int successStatusCode,
                      int failureStatusCode,
                      FEELExpressionBuilder expressionBuilder) {
        this.addResponse = addResponse;
        this.successStatusCode = validateStatusCode(successStatusCode, "successStatusCode");
        this.failureStatusCode = validateStatusCode(failureStatusCode, "failStatusCode");
        this.expressionBuilder = expressionBuilder;
    }

    @Override
    public ValidationRule createRule(String fieldPath, FieldType fieldType) {
        String ruleId = fieldPath + "-invalid";
        String condition = expressionBuilder.build("req." + fieldPath, fieldType);
        return ValidationRule.create(ruleId, condition, fieldPath);
    }

    @Override
    public String render(Map<String, List<ValidationRule>> rulesByEndpoint) {
        StringBuilder output = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<ValidationRule>> entry : rulesByEndpoint.entrySet()) {
            if (!first) {
                output.append("\n\n");
            }
            first = false;

            output.append(entry.getKey()).append("\n");
            output.append(buildRulesBlock(entry.getValue()));
        }
        return output.toString().stripTrailing();
    }

    private String buildRulesBlock(List<ValidationRule> rules) {
        StringBuilder block = new StringBuilder();
        block.append("{\n");
        block.append("  req: request.body,\n");
        block.append("  rules: [\n");
        for (int i = 0; i < rules.size(); i++) {
            block.append("    " + formatRuleLine(rules.get(i)));
            if (i < rules.size() - 1) {
                block.append(",");
            }
            block.append("\n");
        }
        block.append("  ],\n");
        block.append("  isValid: count(rules[invalid=true])=0");

        if (addResponse) {
            appendResponseBlock(block);
            block.append("\n}");
        } else {
            block.append("\n}.isValid");
        }

        return block.toString();
    }

    private String formatRuleLine(ValidationRule rule) {
        if (addResponse) {
            return String.format(
                "{ id: \"%s\", field: \"%s\", invalid: %s }",
                rule.id(),
                rule.fieldPath(),
                rule.invalidExpression()
            );
        }
        return String.format("{id: \"%s\", invalid: %s}", rule.id(), rule.invalidExpression());
    }

    private void appendResponseBlock(StringBuilder block) {
        block.append(",\n");
        block.append("  body: {\n");
        block.append("    message: if isValid then \"Process successfully started.\" else \"Process creation failed.\",\n");
        block.append("    processInstanceKey: if isValid then correlation.processInstanceKey else null,\n");
        block.append("    details: rules[invalid=true]\n");
        block.append("  }, statusCode: if isValid then ")
            .append(successStatusCode)
            .append(" else ")
            .append(failureStatusCode);
    }

    private int validateStatusCode(int statusCode, String name) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                name + " must be a valid HTTP status code (100-599): " + statusCode
            );
        }
        return statusCode;
    }
}
