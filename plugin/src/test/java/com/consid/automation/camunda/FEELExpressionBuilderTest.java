package com.consid.automation.camunda;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FEELExpressionBuilderTest {

    private final FEELExpressionBuilder builder = new FEELExpressionBuilder();

    @Test
    void test_string_expression_without_constraints_does_only_check_type_as_expected() {
        // when
        String result = builder.build("username", FieldDescriptor.of(FieldType.STRING));

        // then — required string with no minLength may be "" per JSON Schema; the type check is enough.
        assertThat(result).isEqualTo("username=null or not(username instance of string)");
    }

    @Test
    void test_string_expression_with_min_length_does_emit_length_lower_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(3, null, null));

        // when
        String result = builder.build("username", descriptor);

        // then
        assertThat(result).isEqualTo(
            "username=null or not(username instance of string) or string length(username)<3");
    }

    @Test
    void test_string_expression_with_max_length_does_emit_length_upper_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(null, 10, null));

        // when
        String result = builder.build("username", descriptor);

        // then
        assertThat(result).isEqualTo(
            "username=null or not(username instance of string) or string length(username)>10");
    }

    @Test
    void test_string_expression_with_min_length_zero_does_omit_lower_bound_as_expected() {
        // given — explicit "may be empty" should not emit a redundant length < 0 check
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(0, null, null));

        // when
        String result = builder.build("note", descriptor);

        // then
        assertThat(result).isEqualTo("note=null or not(note instance of string)");
    }

    @Test
    void test_string_expression_with_pattern_does_emit_matches_check_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(null, null, "^[A-Z]{3}$"));

        // when
        String result = builder.build("code", descriptor);

        // then
        assertThat(result).isEqualTo(
            "code=null or not(code instance of string) or not(matches(code, \"^[A-Z]{3}$\"))");
    }

    @Test
    void test_string_expression_with_pattern_containing_backslash_does_escape_as_expected() {
        // given — \d is common in regex; emitted FEEL must keep the backslash literal
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(null, null, "^\\d{5}$"));

        // when
        String result = builder.build("zip", descriptor);

        // then
        assertThat(result).isEqualTo(
            "zip=null or not(zip instance of string) or not(matches(zip, \"^\\\\d{5}$\"))");
    }

    @Test
    void test_string_expression_with_combined_constraints_does_chain_all_checks_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(2, 8, "^[a-z]+$"));

        // when
        String result = builder.build("handle", descriptor);

        // then
        assertThat(result).isEqualTo(
            "handle=null or not(handle instance of string)"
                + " or string length(handle)<2"
                + " or string length(handle)>8"
                + " or not(matches(handle, \"^[a-z]+$\"))");
    }

    @Test
    void test_nullable_string_with_min_length_does_only_check_when_present_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, true, List.of(), List.of(), ArrayConstraints.NONE,
            new StringConstraints(1, null, null));

        // when
        String result = builder.build("nickname", descriptor);

        // then
        assertThat(result).isEqualTo(
            "nickname!=null and (not(nickname instance of string) or string length(nickname)<1)");
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
    void test_array_expression_without_constraints_does_only_check_type_as_expected() {
        // when
        String result = builder.build("tags", FieldDescriptor.of(FieldType.ARRAY));

        // then — required array with no minItems may be empty per JSON Schema; the type check is enough.
        assertThat(result).isEqualTo("tags=null or not(tags instance of list)");
    }

    @Test
    void test_array_expression_with_min_items_does_emit_count_lower_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), new ArrayConstraints(2, null), StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then
        assertThat(result).isEqualTo(
            "tags=null or not(tags instance of list) or count(tags)<2");
    }

    @Test
    void test_array_expression_with_max_items_does_emit_count_upper_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), new ArrayConstraints(null, 5), StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then
        assertThat(result).isEqualTo(
            "tags=null or not(tags instance of list) or count(tags)>5");
    }

    @Test
    void test_array_expression_with_min_and_max_items_does_emit_both_bounds_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), new ArrayConstraints(1, 3), StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then
        assertThat(result).isEqualTo(
            "tags=null or not(tags instance of list) or count(tags)<1 or count(tags)>3");
    }

    @Test
    void test_array_expression_with_min_items_zero_does_omit_lower_bound_as_expected() {
        // given — explicit "may be empty" should not emit a redundant count(x) < 0 check
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), new ArrayConstraints(0, null), StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then
        assertThat(result).isEqualTo("tags=null or not(tags instance of list)");
    }

    @Test
    void test_nullable_array_with_min_items_does_only_check_when_present_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, true, List.of(), List.of(), new ArrayConstraints(1, null), StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then
        assertThat(result).isEqualTo(
            "tags!=null and (not(tags instance of list) or count(tags)<1)");
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
            "color=null or not(color instance of string)"
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
            .isEqualTo("nickname!=null and (not(nickname instance of string))");
    }

    @Test
    void test_nullable_enum_does_combine_present_and_in_checks_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, true, List.of("a", "b"));

        // when
        String result = builder.build("status", descriptor);

        // then
        assertThat(result).isEqualTo(
            "status!=null and (not(status instance of string) "
                + "or not(status in (\"a\", \"b\")))");
    }

    @Test
    void test_conditional_required_does_wrap_body_with_trigger_check_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(Trigger.presence("req.shippingAddress")));

        // when
        String result = builder.build("shippingCarrier", descriptor);

        // then
        assertThat(result).isEqualTo(
            "req.shippingAddress!=null and ("
                + "shippingCarrier=null or not(shippingCarrier instance of string))");
    }

    @Test
    void test_conditional_required_with_multiple_triggers_does_or_them_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(),
            List.of(Trigger.presence("req.a"), Trigger.presence("req.b")));

        // when
        String result = builder.build("c", descriptor);

        // then
        assertThat(result).startsWith("(req.a!=null or req.b!=null) and (");
    }

    @Test
    void test_conditional_required_with_value_trigger_does_compare_to_literal_as_expected() {
        // given — string value trigger
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(),
            List.of(Trigger.value("req.paymentMethod", List.of("card"))));

        // when
        String result = builder.build("cardNumber", descriptor);

        // then
        assertThat(result).isEqualTo(
            "req.paymentMethod=\"card\" and ("
                + "cardNumber=null or not(cardNumber instance of string))");
    }

    @Test
    void test_conditional_required_with_boolean_true_trigger_does_render_as_bare_path_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(),
            List.of(Trigger.value("req.flagged", List.of(true))));

        // when
        String result = builder.build("reason", descriptor);

        // then — bare path is shorter than req.flagged=true and equivalent under all inputs
        assertThat(result).startsWith("req.flagged and (");
    }

    @Test
    void test_conditional_required_with_boolean_false_trigger_does_render_as_not_path_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(),
            List.of(Trigger.value("req.flagged", List.of(false))));

        // when
        String result = builder.build("reason", descriptor);

        // then
        assertThat(result).startsWith("not(req.flagged) and (");
    }

    @Test
    void test_conditional_required_with_enum_value_trigger_does_use_in_check_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.STRING, false, List.of(),
            List.of(Trigger.value("req.tier", List.of("gold", "platinum"))));

        // when
        String result = builder.build("discountCode", descriptor);

        // then
        assertThat(result).startsWith("req.tier in (\"gold\", \"platinum\") and (");
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
