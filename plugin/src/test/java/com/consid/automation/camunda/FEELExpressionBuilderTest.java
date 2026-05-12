package com.consid.automation.camunda;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FEELExpressionBuilder.
 * Tests the generation of FEEL validation expressions for different field types.
 */
class FEELExpressionBuilderTest {

    private final FEELExpressionBuilder builder = new FEELExpressionBuilder();

    @Test
    void test_string_expression_does_build_as_expected() {
        String result = builder.build("username", FieldDescriptor.of(FieldType.STRING));

        assertThat(result)
                .as("String expression should validate for null, non-string, and blank")
                .isEqualTo("username=null or not(username instance of string) or is blank(username)");
    }

    @Test
    void test_number_expression_does_build_as_expected() {
        String result = builder.build("age", FieldDescriptor.of(FieldType.NUMBER));

        assertThat(result)
                .as("Number expression should validate for null and non-numeric")
                .isEqualTo("age=null or number(age)=null");
    }

    @Test
    void test_boolean_expression_does_build_as_expected() {
        String result = builder.build("isActive", FieldDescriptor.of(FieldType.BOOLEAN));

        assertThat(result)
                .as("Boolean expression should validate for null and non-boolean")
                .isEqualTo("isActive=null or not(isActive instance of boolean)");
    }

    @Test
    void test_array_expression_does_build_as_expected() {
        String result = builder.build("tags", FieldDescriptor.of(FieldType.ARRAY));

        assertThat(result)
                .as("Array expression should validate for null and empty")
                .isEqualTo("tags=null or is empty(tags)");
    }

    @Test
    void test_object_expression_does_build_as_expected() {
        String result = builder.build("metadata", FieldDescriptor.of(FieldType.OBJECT));

        assertThat(result)
                .as("Object expression should validate for null and non-context")
                .isEqualTo("metadata=null or not(metadata instance of context)");
    }

    @Test
    void test_unknown_expression_does_build_as_expected() {
        String result = builder.build("unknown", FieldDescriptor.of(FieldType.UNKNOWN));

        assertThat(result)
                .as("Unknown type should only validate for null")
                .isEqualTo("unknown=null");
    }

    @Test
    void test_nested_field_expression_does_generate_as_expected() {
        String result = builder.build("user.email", FieldDescriptor.of(FieldType.STRING));

        assertThat(result)
                .as("Expression should work with nested field names")
                .contains("user.email");
    }

    @Test
    void test_deeply_nested_expression_does_generate_as_expected() {
        String result = builder.build("organization.department.manager.email", FieldDescriptor.of(FieldType.STRING));

        assertThat(result)
                .as("Expression should work with deeply nested field names")
                .contains("organization.department.manager.email");
    }
}
