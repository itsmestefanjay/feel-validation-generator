package com.consid.automation.camunda;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates FEEL expressions for validating fields.
 */
public class FEELExpressionBuilder {

    /**
     * Generates a FEEL expression for validating a field based on its descriptor.
     */
    public String build(String fieldName, FieldDescriptor descriptor) {
        String typeCheck = switch (descriptor.type()) {
            case STRING -> buildStringExpression(fieldName);
            case NUMBER -> buildNumberExpression(fieldName);
            case BOOLEAN -> buildBooleanExpression(fieldName);
            case ARRAY -> buildArrayExpression(fieldName);
            case OBJECT -> buildObjectExpression(fieldName);
            case DATE -> buildDateExpression(fieldName);
            case DATE_TIME -> buildDateTimeExpression(fieldName);
            case TIME -> buildTimeExpression(fieldName);
            case UNKNOWN -> buildUnknownExpression(fieldName);
        };
        if (!descriptor.hasEnum()) {
            return typeCheck;
        }
        return typeCheck + " or not(" + fieldName + " in (" + renderEnumValues(descriptor.enumValues()) + "))";
    }

    private String renderEnumValues(List<Object> enumValues) {
        return enumValues.stream()
            .map(FEELExpressionBuilder::renderEnumValue)
            .collect(Collectors.joining(", "));
    }

    private static String renderEnumValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String buildStringExpression(String fieldName) {
        return fieldName + "=null or not(" + fieldName + " instance of string) or is blank(" + fieldName + ")";
    }

    private String buildNumberExpression(String fieldName) {
        return fieldName + "=null or number(" + fieldName + ")=null";
    }

    private String buildBooleanExpression(String fieldName) {
        return fieldName + "=null or not(" + fieldName + " instance of boolean)";
    }

    private String buildArrayExpression(String fieldName) {
        return fieldName + "=null or is empty(" + fieldName + ")";
    }

    private String buildObjectExpression(String fieldName) {
        return fieldName + "=null or not(" + fieldName + " instance of context)";
    }

    private String buildDateExpression(String fieldName) {
        return fieldName + "=null or date(" + fieldName + ")=null";
    }

    private String buildDateTimeExpression(String fieldName) {
        return fieldName + "=null or date and time(" + fieldName + ")=null";
    }

    private String buildTimeExpression(String fieldName) {
        return fieldName + "=null or time(" + fieldName + ")=null";
    }

    private String buildUnknownExpression(String fieldName) {
        return fieldName + "=null";
    }
}
