package com.consid.automation.camunda;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralizes all FEEL-specific rule building and rendering logic so that the rest
 * of the generator remains focused on OpenAPI traversal.
 */
class FEELRuleGenerator implements ValidationRuleBuilder {

    private static final String ACTIVATION_TEMPLATE = """
            {
              req: request.body,
              rules: [
            %s
              ],
              isValid: count(rules[invalid=true])=0
            }.isValid""";

    private static final String RESPONSE_TEMPLATE = """
            {
              req: request.body,
              rules: [
            %s
              ],
              isValid: count(rules[invalid=true])=0,
              body: {
                message: if isValid then "Process successfully started." else "Process creation failed.",
                processInstanceKey: if isValid then correlation.processInstanceKey else null,
                details: rules[invalid=true]
              }, statusCode: if isValid then %d else %d
            }""";

    private final boolean addResponse;
    private final FEELExpressionBuilder expressionBuilder;
    private final int successStatusCode;
    private final int failureStatusCode;

    FEELRuleGenerator(boolean addResponse) {
        this(addResponse, 201, 400);
    }

    FEELRuleGenerator(boolean addResponse, int successStatusCode, int failureStatusCode) {
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
    public ValidationRule createRule(String fieldPath, FieldDescriptor descriptor) {
        if (fieldPath.isEmpty()) {
            // Synthetic root entry from RequiredFieldsExtractor — emit against `req`
            // directly with a descriptive id/field, no dotted prefix.
            String rootCondition = expressionBuilder.build("req", qualifyDependsOn(descriptor));
            return ValidationRule.create("rootObject-invalid", rootCondition, "(root)");
        }
        String ruleId = fieldPath + "-invalid";
        String condition = expressionBuilder.build("req." + fieldPath, qualifyDependsOn(descriptor));
        return ValidationRule.create(ruleId, condition, fieldPath);
    }

    private FieldDescriptor qualifyDependsOn(FieldDescriptor descriptor) {
        if (!descriptor.isConditional()) {
            return descriptor;
        }
        List<Trigger> qualified = descriptor.dependsOn().stream()
            .map(t -> new Trigger("req." + t.path(), t.allowedValues()))
            .toList();
        return new FieldDescriptor(
            descriptor.type(), descriptor.nullable(), descriptor.enumValues(), qualified);
    }

    @Override
    public String render(Map<String, List<ValidationRule>> rulesByEndpoint) {
        return rulesByEndpoint.entrySet().stream()
            .map(entry -> entry.getKey() + "\n" + buildRulesBlock(entry.getValue()))
            .collect(Collectors.joining("\n\n"));
    }

    private String buildRulesBlock(List<ValidationRule> rules) {
        String renderedRules = rules.stream()
            .map(rule -> "    " + formatRuleLine(rule))
            .collect(Collectors.joining(",\n"));
        return addResponse
            ? RESPONSE_TEMPLATE.formatted(renderedRules, successStatusCode, failureStatusCode)
            : ACTIVATION_TEMPLATE.formatted(renderedRules);
    }

    private String formatRuleLine(ValidationRule rule) {
        if (addResponse) {
            return "{ id: \"" + rule.id()
                + "\", field: \"" + rule.fieldPath()
                + "\", invalid: " + rule.invalidExpression() + " }";
        }
        return "{invalid: " + rule.invalidExpression() + "}";
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
