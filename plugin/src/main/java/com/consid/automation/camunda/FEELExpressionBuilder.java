package com.consid.automation.camunda;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates FEEL expressions for validating fields.
 * Composition: a field is invalid when it is missing (and required) or present
 * in a form that violates its type / enum constraints. The two halves are kept
 * separate here so nullable fields can substitute the missing-check for an
 * is-present check.
 */
public class FEELExpressionBuilder {

    public String build(String fieldName, FieldDescriptor descriptor) {
        String body = buildBody(fieldName, descriptor);
        if (!descriptor.isConditional()) {
            return body;
        }
        return guardExpression(descriptor.dependsOn()) + " and (" + body + ")";
    }

    private String buildBody(String fieldName, FieldDescriptor descriptor) {
        String violation = buildViolation(fieldName, descriptor);
        if (descriptor.nullable()) {
            return violation == null ? "false" : fieldName + "!=null and (" + violation + ")";
        }
        return violation == null ? fieldName + "=null" : fieldName + "=null or " + violation;
    }

    private String buildViolation(String fieldName, FieldDescriptor descriptor) {
        String typeViolation = typeViolation(fieldName, descriptor.type());
        if (!descriptor.hasEnum()) {
            return typeViolation;
        }
        String enumViolation = "not(" + fieldName + " in (" + renderLiterals(descriptor.enumValues()) + "))";
        return typeViolation == null ? enumViolation : typeViolation + " or " + enumViolation;
    }

    private String typeViolation(String fieldName, FieldType type) {
        return switch (type) {
            case STRING -> "not(" + fieldName + " instance of string) or is blank(" + fieldName + ")";
            case NUMBER -> "not(" + fieldName + " instance of number)";
            case BOOLEAN -> "not(" + fieldName + " instance of boolean)";
            case ARRAY -> "not(" + fieldName + " instance of list) or is empty(" + fieldName + ")";
            case OBJECT -> "not(" + fieldName + " instance of context)";
            case DATE -> "date(" + fieldName + ")=null";
            case DATE_TIME -> "date and time(" + fieldName + ")=null";
            case TIME -> "time(" + fieldName + ")=null";
            case UNKNOWN -> null;
        };
    }

    private String guardExpression(List<Trigger> dependsOn) {
        String parts = dependsOn.stream()
            .map(this::renderTrigger)
            .collect(Collectors.joining(" or "));
        return dependsOn.size() == 1 ? parts : "(" + parts + ")";
    }

    private String renderTrigger(Trigger trigger) {
        if (trigger.isPresenceCheck()) {
            return trigger.path() + "!=null";
        }
        if (trigger.allowedValues().size() == 1) {
            return trigger.path() + "=" + renderLiteral(trigger.allowedValues().get(0));
        }
        return trigger.path() + " in (" + renderLiterals(trigger.allowedValues()) + ")";
    }

    private String renderLiterals(List<Object> values) {
        return values.stream()
            .map(FEELExpressionBuilder::renderLiteral)
            .collect(Collectors.joining(", "));
    }

    private static String renderLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
