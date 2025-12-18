package com.consid.automation.camunda;

/**
 * Generates FEEL expressions for validating fields.
 */
public class FEELExpressionBuilder {

    /**
     * Generates a FEEL expression for validating a field based on its type.
     */
    public String build(String fieldName, FieldType fieldType) {
        return switch (fieldType) {
            case STRING -> buildStringExpression(fieldName);
            case NUMBER -> buildNumberExpression(fieldName);
            case BOOLEAN -> buildBooleanExpression(fieldName);
            case ARRAY -> buildArrayExpression(fieldName);
            case OBJECT -> buildObjectExpression(fieldName);
            case UNKNOWN -> buildUnknownExpression(fieldName);
        };
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

    private String buildUnknownExpression(String fieldName) {
        return fieldName + "=null";
    }
}
