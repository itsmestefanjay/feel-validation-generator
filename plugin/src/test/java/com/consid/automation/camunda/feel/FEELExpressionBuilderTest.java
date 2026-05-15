package com.consid.automation.camunda.feel;

import com.consid.automation.camunda.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    void test_number_expression_with_minimum_does_emit_inclusive_lower_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(new BigDecimal("18"), null, null, null, null));

        // when
        String result = builder.build("age", descriptor);

        // then
        assertThat(result).isEqualTo(
            "age=null or not(age instance of number) or age<18");
    }

    @Test
    void test_number_expression_with_maximum_does_emit_inclusive_upper_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(null, null, new BigDecimal("120"), null, null));

        // when
        String result = builder.build("age", descriptor);

        // then
        assertThat(result).isEqualTo(
            "age=null or not(age instance of number) or age>120");
    }

    @Test
    void test_number_expression_with_exclusive_minimum_does_emit_le_bound_as_expected() {
        // given — violation when x is at or below the exclusive lower
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(null, new BigDecimal("0"), null, null, null));

        // when
        String result = builder.build("discount", descriptor);

        // then
        assertThat(result).isEqualTo(
            "discount=null or not(discount instance of number) or discount<=0");
    }

    @Test
    void test_number_expression_with_exclusive_maximum_does_emit_ge_bound_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(null, null, null, new BigDecimal("1"), null));

        // when
        String result = builder.build("discount", descriptor);

        // then
        assertThat(result).isEqualTo(
            "discount=null or not(discount instance of number) or discount>=1");
    }

    @Test
    void test_number_expression_with_multiple_of_does_emit_modulo_check_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(null, null, null, null, new BigDecimal("100")));

        // when
        String result = builder.build("points", descriptor);

        // then
        assertThat(result).isEqualTo(
            "points=null or not(points instance of number) or modulo(points, 100)!=0");
    }

    @Test
    void test_number_expression_with_combined_bounds_does_chain_min_max_then_multiple_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(new BigDecimal("0"), null, new BigDecimal("1000"), null, new BigDecimal("50")));

        // when
        String result = builder.build("amount", descriptor);

        // then
        assertThat(result).isEqualTo(
            "amount=null or not(amount instance of number)"
                + " or amount<0"
                + " or amount>1000"
                + " or modulo(amount, 50)!=0");
    }

    @Test
    void test_number_expression_with_negative_bound_does_render_unary_minus_as_expected() {
        // given — FEEL parses `x<-100` as `x < (-100)`; unary minus binds tighter than comparison
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(new BigDecimal("-100"), null, null, null, null));

        // when
        String result = builder.build("delta", descriptor);

        // then
        assertThat(result).isEqualTo(
            "delta=null or not(delta instance of number) or delta<-100");
    }

    @Test
    void test_number_expression_with_big_decimal_in_scientific_notation_does_render_plain_as_expected() {
        // given — BigDecimal.toString() emits "1E+2" for some inputs; FEEL would not parse that.
        // toPlainString must be used so the rendered literal is "100".
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(null, null, new BigDecimal("1E2"), null, null));

        // when
        String result = builder.build("value", descriptor);

        // then
        assertThat(result).isEqualTo(
            "value=null or not(value instance of number) or value>100");
    }

    @Test
    void test_nullable_number_with_minimum_does_only_check_when_present_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.NUMBER, true, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE,
            new NumberConstraints(new BigDecimal("0"), null, null, null, null));

        // when
        String result = builder.build("balance", descriptor);

        // then
        assertThat(result).isEqualTo(
            "balance!=null and (not(balance instance of number) or balance<0)");
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
    void test_array_expression_with_items_does_emit_some_satisfies_clause_as_expected() {
        // given — array of strings: violation when any element isn't a string
        FieldDescriptor items = FieldDescriptor.of(FieldType.STRING);
        ArrayConstraints constraints = new ArrayConstraints(null, null, items, java.util.Map.of());
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), constraints, StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then
        assertThat(result).isEqualTo(
            "tags=null or not(tags instance of list)"
                + " or (some e in tags satisfies (e=null or not(e instance of string)))");
    }

    @Test
    void test_array_expression_with_typed_items_constraints_does_propagate_inner_checks_as_expected() {
        // given — array of strings with a minLength constraint on each element
        FieldDescriptor items = new FieldDescriptor(
            FieldType.STRING, false, List.of(), List.of(),
            ArrayConstraints.NONE,
            new StringConstraints(2, null, null));
        ArrayConstraints constraints = new ArrayConstraints(null, null, items, java.util.Map.of());
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), constraints, StringConstraints.NONE);

        // when
        String result = builder.build("codes", descriptor);

        // then — each element's own constraints fold into the satisfies body
        assertThat(result).contains("(some e in codes satisfies (e=null or not(e instance of string) or string length(e)<2))");
    }

    @Test
    void test_array_expression_with_object_items_required_children_does_emit_per_field_checks_as_expected() {
        // given — array of objects {id, name} both required; each element's required children
        // appear inside the satisfies body with `e.` paths
        FieldDescriptor items = FieldDescriptor.of(FieldType.OBJECT);
        java.util.Map<String, FieldDescriptor> itemRequired = new java.util.LinkedHashMap<>();
        itemRequired.put("id", FieldDescriptor.of(FieldType.STRING));
        itemRequired.put("name", FieldDescriptor.of(FieldType.STRING));
        ArrayConstraints constraints = new ArrayConstraints(null, null, items, itemRequired);
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), constraints, StringConstraints.NONE);

        // when
        String result = builder.build("lineItems", descriptor);

        // then
        assertThat(result).isEqualTo(
            "lineItems=null or not(lineItems instance of list)"
                + " or (some e in lineItems satisfies ("
                + "e=null or not(e instance of context)"
                + " or e.id=null or not(e.id instance of string)"
                + " or e.name=null or not(e.name instance of string)))");
    }

    @Test
    void test_array_expression_with_items_and_min_items_does_combine_bounds_and_element_check_as_expected() {
        // given — array of strings, must be non-empty, each element must be a string
        FieldDescriptor items = FieldDescriptor.of(FieldType.STRING);
        ArrayConstraints constraints = new ArrayConstraints(1, null, items, java.util.Map.of());
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.ARRAY, false, List.of(), List.of(), constraints, StringConstraints.NONE);

        // when
        String result = builder.build("tags", descriptor);

        // then — bounds first, then element check
        assertThat(result).isEqualTo(
            "tags=null or not(tags instance of list)"
                + " or count(tags)<1"
                + " or (some e in tags satisfies (e=null or not(e instance of string)))");
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
    void test_object_expression_with_additional_properties_false_does_emit_keys_check_as_expected() {
        // given
        ObjectConstraints constraints = new ObjectConstraints(java.util.Set.of("id", "name"));
        FieldDescriptor descriptor = new FieldDescriptor(
            FieldType.OBJECT, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE, NumberConstraints.NONE,
            constraints);

        // when
        String result = builder.build("profile", descriptor);

        // then — wrapped in outer parens (same defensive reason as `some`: `every` is greedy)
        assertThat(result).isEqualTo(
            "profile=null or not(profile instance of context)"
                + " or (not(every k in get entries(profile).key satisfies (k in (\"id\", \"name\"))))");
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
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, false,
            List.of(new FeelString("red"), new FeelString("green"), new FeelString("blue")));

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
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.NUMBER, false,
            List.of(new FeelNumber(new BigDecimal("1")),
                    new FeelNumber(new BigDecimal("2")),
                    new FeelNumber(new BigDecimal("3"))));

        // when
        String result = builder.build("rank", descriptor);

        // then
        assertThat(result).endsWith("or not(rank in (1, 2, 3))");
    }

    @Test
    void test_enum_string_with_quotes_does_escape_as_expected() {
        // given
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, false,
            List.of(new FeelString("say \"hi\"")));

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
        FieldDescriptor descriptor = new FieldDescriptor(FieldType.STRING, true,
            List.of(new FeelString("a"), new FeelString("b")));

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
            List.of(Trigger.value("req.paymentMethod", List.of(new FeelString("card")))));

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
            List.of(Trigger.value("req.flagged", List.of(new FeelBoolean(true)))));

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
            List.of(Trigger.value("req.flagged", List.of(new FeelBoolean(false)))));

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
            List.of(Trigger.value("req.tier", List.of(new FeelString("gold"), new FeelString("platinum")))));

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
