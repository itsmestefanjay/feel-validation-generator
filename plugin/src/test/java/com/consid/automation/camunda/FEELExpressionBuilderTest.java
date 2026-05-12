package com.consid.automation.camunda;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void test_date_expression_does_build_as_expected() {
        String result = builder.build("birthDate", FieldDescriptor.of(FieldType.DATE));

        assertThat(result)
                .as("Date expression should validate for null and unparseable date")
                .isEqualTo("birthDate=null or date(birthDate)=null");
    }

    @Test
    void test_date_time_expression_does_build_as_expected() {
        String result = builder.build("createdAt", FieldDescriptor.of(FieldType.DATE_TIME));

        assertThat(result)
                .as("Date-time expression should validate for null and unparseable date-time")
                .isEqualTo("createdAt=null or date and time(createdAt)=null");
    }

    @Test
    void test_time_expression_does_build_as_expected() {
        String result = builder.build("startTime", FieldDescriptor.of(FieldType.TIME));

        assertThat(result)
                .as("Time expression should validate for null and unparseable time")
                .isEqualTo("startTime=null or time(startTime)=null");
    }

    @Test
    void test_string_enum_expression_does_append_in_check_as_expected() {
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, false, List.of("red", "green", "blue"));

        String result = builder.build("color", descriptor);

        assertThat(result)
            .as("Enum check should be appended after the type check")
            .isEqualTo("color=null or not(color instance of string) or is blank(color)"
                + " or not(color in (\"red\", \"green\", \"blue\"))");
    }

    @Test
    void test_number_enum_expression_does_render_numeric_literals_as_expected() {
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.NUMBER, false, List.of(1, 2, 3));

        String result = builder.build("rank", descriptor);

        assertThat(result)
            .as("Numeric enum values should be rendered unquoted")
            .endsWith("or not(rank in (1, 2, 3))");
    }

    @Test
    void test_string_enum_with_quotes_does_escape_as_expected() {
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, false, List.of("say \"hi\""));

        String result = builder.build("greeting", descriptor);

        assertThat(result)
            .as("Embedded double quotes in enum strings should be escaped")
            .endsWith("or not(greeting in (\"say \\\"hi\\\"\"))");
    }

    @Test
    void test_unknown_expression_does_build_as_expected() {
        String result = builder.build("unknown", FieldDescriptor.of(FieldType.UNKNOWN));

        assertThat(result)
                .as("Unknown type should only validate for null")
                .isEqualTo("unknown=null");
    }

    @Test
    void test_nullable_string_expression_does_use_is_present_check_as_expected() {
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, true, List.of());

        String result = builder.build("nickname", descriptor);

        assertThat(result)
            .as("Nullable fields should allow null and only validate when present")
            .isEqualTo("nickname!=null and (not(nickname instance of string) or is blank(nickname))");
    }

    @Test
    void test_nullable_with_enum_does_combine_checks_as_expected() {
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, true, List.of("a", "b"));

        String result = builder.build("status", descriptor);

        assertThat(result)
            .as("Nullable enum should enforce enum membership only when value is present")
            .isEqualTo("status!=null and (not(status instance of string) or is blank(status) "
                + "or not(status in (\"a\", \"b\")))");
    }

    @Test
    void test_nullable_unknown_does_never_fail_as_expected() {
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.UNKNOWN, true, List.of());

        String result = builder.build("anything", descriptor);

        assertThat(result)
            .as("A nullable field with no detectable constraints is never invalid")
            .isEqualTo("false");
    }

}
