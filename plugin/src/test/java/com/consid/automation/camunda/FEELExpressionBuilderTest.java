package com.consid.automation.camunda;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FEELExpressionBuilderTest {

    private final FEELExpressionBuilder builder = new FEELExpressionBuilder();

    @Test
    void test_string_expression_does_build_as_expected() {
        // when
        String result = builder.build("username", FieldDescriptor.of(FieldType.STRING));

        // then
        assertThat(result)
            .isEqualTo("username=null or not(username instance of string) or is blank(username)");
    }

    @Test
    void test_number_expression_does_build_as_expected() {
        // when
        String result = builder.build("age", FieldDescriptor.of(FieldType.NUMBER));

        // then
        assertThat(result).isEqualTo("age=null or not(age instance of number)");
    }

    @Test
    void test_boolean_expression_does_build_as_expected() {
        // when
        String result = builder.build("isActive", FieldDescriptor.of(FieldType.BOOLEAN));

        // then
        assertThat(result).isEqualTo("isActive=null or not(isActive instance of boolean)");
    }

    @Test
    void test_array_expression_does_build_as_expected() {
        // when
        String result = builder.build("tags", FieldDescriptor.of(FieldType.ARRAY));

        // then
        assertThat(result).isEqualTo("tags=null or not(tags instance of list) or is empty(tags)");
    }

    @Test
    void test_object_expression_does_build_as_expected() {
        // when
        String result = builder.build("metadata", FieldDescriptor.of(FieldType.OBJECT));

        // then
        assertThat(result).isEqualTo("metadata=null or not(metadata instance of context)");
    }

    @Test
    void test_date_expression_does_build_as_expected() {
        // when
        String result = builder.build("birthDate", FieldDescriptor.of(FieldType.DATE));

        // then
        assertThat(result).isEqualTo("birthDate=null or date(birthDate)=null");
    }

    @Test
    void test_date_time_expression_does_build_as_expected() {
        // when
        String result = builder.build("createdAt", FieldDescriptor.of(FieldType.DATE_TIME));

        // then
        assertThat(result).isEqualTo("createdAt=null or date and time(createdAt)=null");
    }

    @Test
    void test_time_expression_does_build_as_expected() {
        // when
        String result = builder.build("startTime", FieldDescriptor.of(FieldType.TIME));

        // then
        assertThat(result).isEqualTo("startTime=null or time(startTime)=null");
    }

    @Test
    void test_unknown_expression_does_only_check_null_as_expected() {
        // when
        String result = builder.build("unknown", FieldDescriptor.of(FieldType.UNKNOWN));

        // then
        assertThat(result).isEqualTo("unknown=null");
    }

    @Test
    void test_string_enum_does_append_in_check_after_type_check_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, false, List.of("red", "green", "blue"));

        // when
        String result = builder.build("color", descriptor);

        // then
        assertThat(result).isEqualTo(
            "color=null or not(color instance of string) or is blank(color)"
                + " or not(color in (\"red\", \"green\", \"blue\"))");
    }

    @Test
    void test_number_enum_does_render_numeric_literals_unquoted_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.NUMBER, false, List.of(1, 2, 3));

        // when
        String result = builder.build("rank", descriptor);

        // then
        assertThat(result).endsWith("or not(rank in (1, 2, 3))");
    }

    @Test
    void test_enum_string_with_quotes_does_escape_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, false, List.of("say \"hi\""));

        // when
        String result = builder.build("greeting", descriptor);

        // then
        assertThat(result).endsWith("or not(greeting in (\"say \\\"hi\\\"\"))");
    }

    @Test
    void test_nullable_string_does_check_only_when_present_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, true, List.of());

        // when
        String result = builder.build("nickname", descriptor);

        // then
        assertThat(result)
            .isEqualTo("nickname!=null and (not(nickname instance of string) or is blank(nickname))");
    }

    @Test
    void test_nullable_enum_does_combine_present_and_in_checks_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, true, List.of("a", "b"));

        // when
        String result = builder.build("status", descriptor);

        // then
        assertThat(result).isEqualTo(
            "status!=null and (not(status instance of string) or is blank(status) "
                + "or not(status in (\"a\", \"b\")))");
    }

    @Test
    void test_nullable_unknown_does_never_fail_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.UNKNOWN, true, List.of());

        // when
        String result = builder.build("anything", descriptor);

        // then
        assertThat(result).isEqualTo("false");
    }
}
