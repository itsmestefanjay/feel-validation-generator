package com.consid.automation.camunda.openapi;

import com.consid.automation.camunda.model.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FieldTypeResolver.
 */
class FieldTypeResolverTest {

    private FieldTypeResolver resolver;
    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        openAPI = new OpenAPI();
        resolver = new FieldTypeResolver(openAPI);
    }

    @ParameterizedTest(name = "type={0} → {1}")
    @CsvSource({
        "string,  STRING",
        "number,  NUMBER",
        "integer, NUMBER",
        "boolean, BOOLEAN",
        "array,   ARRAY",
        "object,  OBJECT",
        "unsupported, UNKNOWN"
    })
    void test_resolve_does_map_openapi_type_to_field_type_as_expected(String openApiType, FieldType expected) {
        // given
        Schema<?> schema = new Schema<>().type(openApiType);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "format={0} → {1}")
    @CsvSource({
        "date,      DATE",
        "date-time, DATE_TIME",
        "time,      TIME",
        "uuid,      STRING"
    })
    void test_resolve_does_map_string_format_to_date_subtype_as_expected(String format, FieldType expected) {
        // given
        Schema<?> schema = new Schema<>().type("string").format(format);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(expected);
    }

    @Test
    void test_resolve_null_schema_does_return_unknown_as_expected() {
        // when
        FieldDescriptor result = resolver.resolve(null);

        // then
        assertThat(result.type()).isEqualTo(FieldType.UNKNOWN);
    }

    @Test
    void test_resolve_schema_without_type_but_with_properties_does_imply_object_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.addProperty("field1", new Schema<>().type("string"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type())
            .as("A schema with properties but no explicit type should resolve to OBJECT")
            .isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_schema_without_type_but_with_allOf_does_imply_object_as_expected() {
        // given — composition keywords on a property schema (no explicit type) describe an object
        Schema<?> schema = new Schema<>();
        schema.setAllOf(java.util.List.of(new Schema<>().type("object")));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type())
            .as("A schema using allOf is implicitly an object — the workaround `type: object` next to allOf should not be needed")
            .isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_schema_without_type_but_with_oneOf_does_imply_object_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setOneOf(java.util.List.of(new Schema<>().type("object")));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type())
            .as("A schema using oneOf is implicitly an object")
            .isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_schema_without_type_but_with_anyOf_does_imply_object_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setAnyOf(java.util.List.of(new Schema<>().type("object")));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type())
            .as("A schema using anyOf is implicitly an object")
            .isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_schema_without_type_but_with_required_does_imply_object_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("field1"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type())
            .as("A schema with only `required` should still resolve to OBJECT")
            .isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_reference_does_follow_to_referenced_type_as_expected() {
        // given
        Schema<?> referenced = new Schema<>().type("boolean");
        openAPI.components(new io.swagger.v3.oas.models.Components().addSchemas("RefSchema", referenced));
        Schema<?> schema = new Schema<>();
        schema.set$ref("#/components/schemas/RefSchema");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void test_resolve_reference_without_components_does_throw_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");
        schema.set$ref("#/components/schemas/Missing");

        // when // then
        assertThatThrownBy(() -> resolver.resolveSchemaReference(schema))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("#/components/schemas/Missing");
    }

    @Test
    void test_resolve_null_reference_does_return_null_as_expected() {
        // when
        Schema<?> resolved = resolver.resolveSchemaReference(null);

        // then
        assertThat(resolved).isNull();
    }

    @Test
    void test_resolve_enum_does_capture_values_as_expected() {
        // given
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema.setEnum(Arrays.asList("red", "green", "blue"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.enumValues()).containsExactly(
            new FeelString("red"), new FeelString("green"), new FeelString("blue"));
    }

    @Test
    void test_resolve_const_does_capture_as_single_value_enum_as_expected() {
        // given — `const: "v1"` is the single-value pinning equivalent of `enum: ["v1"]`
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema._const("v1");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.enumValues())
            .as("const should surface in the same channel as enum so the existing in-check renders it")
            .containsExactly(new FeelString("v1"));
    }

    @Test
    void test_resolve_enum_overrides_const_when_both_present_as_expected() {
        // given — author wrote both; enum is the more general form so it wins
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema.setEnum(Arrays.asList("a", "b"));
        schema._const("ignored");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.enumValues()).containsExactly(new FeelString("a"), new FeelString("b"));
    }

    @Test
    void test_resolve_schema_without_enum_does_return_empty_enum_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.hasEnum()).isFalse();
    }

    @Test
    void test_resolve_openapi_3_0_nullable_flag_does_set_descriptor_nullable_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");
        schema.setNullable(Boolean.TRUE);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.nullable()).isTrue();
    }

    @Test
    void test_resolve_openapi_3_1_type_array_with_null_does_set_descriptor_nullable_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setTypes(new java.util.LinkedHashSet<>(Arrays.asList("string", "null")));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.nullable()).isTrue();
        assertThat(result.type())
            .as("Primary type should be picked from the non-null entry")
            .isEqualTo(FieldType.STRING);
    }

    @Test
    void test_resolve_schema_without_nullable_does_default_to_non_nullable_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.nullable()).isFalse();
    }

    @Test
    void test_resolve_array_without_constraints_does_default_to_none_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("array");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.arrayConstraints())
            .as("Array without minItems/maxItems should carry the NONE sentinel — required no longer implies non-empty")
            .isEqualTo(ArrayConstraints.NONE);
    }

    @Test
    void test_resolve_array_min_items_does_capture_value_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("array");
        schema.setMinItems(2);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.arrayConstraints().minItems()).isEqualTo(2);
        assertThat(result.arrayConstraints().maxItems()).isNull();
    }

    @Test
    void test_resolve_array_min_items_zero_does_capture_value_as_expected() {
        // given — explicit minItems: 0 is the canonical "present but may be empty" signal
        Schema<?> schema = new Schema<>().type("array");
        schema.setMinItems(0);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.arrayConstraints().minItems())
            .as("Explicit minItems: 0 should round-trip — distinguishes 'unset' from 'allow empty'")
            .isZero();
    }

    @Test
    void test_resolve_array_max_items_does_capture_value_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("array");
        schema.setMaxItems(5);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.arrayConstraints().maxItems()).isEqualTo(5);
        assertThat(result.arrayConstraints().minItems()).isNull();
    }

    @Test
    void test_resolve_array_min_and_max_items_does_capture_both_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("array");
        schema.setMinItems(1);
        schema.setMaxItems(3);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.arrayConstraints().minItems()).isEqualTo(1);
        assertThat(result.arrayConstraints().maxItems()).isEqualTo(3);
    }

    @Test
    void test_resolve_non_array_with_min_items_does_ignore_constraint_as_expected() {
        // given — minItems is array-only; on a string it has no meaning
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinItems(2);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.arrayConstraints())
            .as("Array constraints should only attach to array-typed schemas")
            .isEqualTo(ArrayConstraints.NONE);
    }

    @Test
    void test_resolve_string_without_constraints_does_default_to_none_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints())
            .as("String without length/pattern should carry the NONE sentinel — required no longer implies non-blank")
            .isEqualTo(StringConstraints.NONE);
    }

    @Test
    void test_resolve_string_min_length_does_capture_value_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinLength(3);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().minLength()).isEqualTo(3);
        assertThat(result.stringConstraints().maxLength()).isNull();
        assertThat(result.stringConstraints().pattern()).isNull();
    }

    @Test
    void test_resolve_string_min_length_zero_does_capture_value_as_expected() {
        // given — explicit minLength: 0 means "may be empty", distinct from "unset"
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinLength(0);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().minLength()).isZero();
    }

    @Test
    void test_resolve_string_max_length_does_capture_value_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");
        schema.setMaxLength(255);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().maxLength()).isEqualTo(255);
        assertThat(result.stringConstraints().minLength()).isNull();
    }

    @Test
    void test_resolve_string_pattern_does_capture_value_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");
        schema.setPattern("^[A-Z]{3}$");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().pattern()).isEqualTo("^[A-Z]{3}$");
    }

    @Test
    void test_resolve_string_combined_constraints_does_capture_all_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinLength(2);
        schema.setMaxLength(10);
        schema.setPattern("^[a-z]+$");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().minLength()).isEqualTo(2);
        assertThat(result.stringConstraints().maxLength()).isEqualTo(10);
        assertThat(result.stringConstraints().pattern()).isEqualTo("^[a-z]+$");
    }

    @Test
    void test_resolve_string_format_email_does_inject_canonical_pattern_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string").format("email");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(FieldType.STRING);
        assertThat(result.stringConstraints().pattern())
            .as("format: email should inject a default regex so the existing pattern path validates the value")
            .isNotNull()
            .contains("@");
    }

    @Test
    void test_resolve_string_format_uuid_does_inject_canonical_pattern_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string").format("uuid");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(FieldType.STRING);
        assertThat(result.stringConstraints().pattern())
            .as("format: uuid should inject a default regex matching the canonical 8-4-4-4-12 hex form")
            .isNotNull()
            .startsWith("^[0-9a-fA-F]{8}");
    }

    @Test
    void test_resolve_string_format_uri_does_inject_canonical_pattern_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("string").format("uri");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(FieldType.STRING);
        assertThat(result.stringConstraints().pattern())
            .as("format: uri should inject a default scheme:something regex")
            .isNotNull();
    }

    @Test
    void test_resolve_string_format_url_alias_does_inject_uri_pattern_as_expected() {
        // given — `format: url` is a common spelling; treat it like `uri`
        Schema<?> schema = new Schema<>().type("string").format("url");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().pattern())
            .as("format: url should be aliased to the uri pattern so authors who write either get the same check")
            .isNotNull();
    }

    @Test
    void test_resolve_author_pattern_overrides_format_default_as_expected() {
        // given — author wrote both format:email AND a custom pattern. Author wins.
        Schema<?> schema = new Schema<>().type("string").format("email");
        schema.setPattern("^custom$");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints().pattern())
            .as("Author-supplied pattern should beat the format default — most specific intent wins")
            .isEqualTo("^custom$");
    }

    @Test
    void test_resolve_non_string_with_pattern_does_ignore_constraint_as_expected() {
        // given — pattern is string-only; on a number it has no meaning
        Schema<?> schema = new Schema<>().type("number");
        schema.setPattern("^[0-9]+$");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.stringConstraints())
            .as("String constraints should only attach to string-typed schemas")
            .isEqualTo(StringConstraints.NONE);
    }

    @Test
    void test_resolve_date_string_with_min_length_does_attach_to_string_constraints_as_expected() {
        // given — DATE is a string subtype; length constraints still apply
        Schema<?> schema = new Schema<>().type("string").format("date");
        schema.setMinLength(10);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(FieldType.DATE);
        assertThat(result.stringConstraints().minLength())
            .as("Date-formatted strings still carry length constraints — the format check is layered on top")
            .isEqualTo(10);
    }

    @Test
    void test_resolve_number_without_constraints_does_default_to_none_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("number");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints())
            .as("Number without range/multipleOf should carry the NONE sentinel")
            .isEqualTo(NumberConstraints.NONE);
    }

    @Test
    void test_resolve_number_minimum_does_capture_as_inclusive_lower_bound_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("number");
        schema.setMinimum(new BigDecimal("0"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().minimum()).isEqualByComparingTo("0");
        assertThat(result.numberConstraints().exclusiveMinimum()).isNull();
    }

    @Test
    void test_resolve_number_maximum_does_capture_as_inclusive_upper_bound_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("number");
        schema.setMaximum(new BigDecimal("100"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().maximum()).isEqualByComparingTo("100");
        assertThat(result.numberConstraints().exclusiveMaximum()).isNull();
    }

    @Test
    void test_resolve_number_exclusive_minimum_3_1_form_does_capture_as_exclusive_bound_as_expected() {
        // given — OpenAPI 3.1 / JSON Schema 2020-12: exclusiveMinimum is a number
        Schema<?> schema = new Schema<>().type("number");
        schema.setExclusiveMinimumValue(new BigDecimal("0"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().exclusiveMinimum()).isEqualByComparingTo("0");
        assertThat(result.numberConstraints().minimum())
            .as("3.1-style exclusive lower should not also populate the inclusive lower")
            .isNull();
    }

    @Test
    void test_resolve_number_exclusive_minimum_3_0_form_does_normalize_to_exclusive_bound_as_expected() {
        // given — OpenAPI 3.0: exclusiveMinimum is a boolean that promotes `minimum` to exclusive
        Schema<?> schema = new Schema<>().type("number");
        schema.setMinimum(new BigDecimal("5"));
        schema.setExclusiveMinimum(Boolean.TRUE);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().exclusiveMinimum())
            .as("3.0-style boolean exclusiveMinimum should be normalized into the exclusive slot")
            .isEqualByComparingTo("5");
        assertThat(result.numberConstraints().minimum())
            .as("Once promoted to exclusive, the inclusive slot is cleared so the builder sees one form")
            .isNull();
    }

    @Test
    void test_resolve_number_exclusive_maximum_3_1_form_does_capture_as_exclusive_bound_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("number");
        schema.setExclusiveMaximumValue(new BigDecimal("1"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().exclusiveMaximum()).isEqualByComparingTo("1");
        assertThat(result.numberConstraints().maximum()).isNull();
    }

    @Test
    void test_resolve_number_exclusive_maximum_3_0_form_does_normalize_to_exclusive_bound_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("number");
        schema.setMaximum(new BigDecimal("100"));
        schema.setExclusiveMaximum(Boolean.TRUE);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().exclusiveMaximum()).isEqualByComparingTo("100");
        assertThat(result.numberConstraints().maximum()).isNull();
    }

    @Test
    void test_resolve_number_multiple_of_does_capture_value_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("number");
        schema.setMultipleOf(new BigDecimal("100"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints().multipleOf()).isEqualByComparingTo("100");
    }

    @Test
    void test_resolve_integer_with_number_constraints_does_still_capture_them_as_expected() {
        // given — `integer` collapses to FieldType.NUMBER; range/multipleOf constraints still apply
        Schema<?> schema = new Schema<>().type("integer");
        schema.setMinimum(new BigDecimal("18"));
        schema.setMaximum(new BigDecimal("120"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.type()).isEqualTo(FieldType.NUMBER);
        assertThat(result.numberConstraints().minimum()).isEqualByComparingTo("18");
        assertThat(result.numberConstraints().maximum()).isEqualByComparingTo("120");
    }

    @Test
    void test_resolve_object_without_additional_properties_does_default_to_open_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("object");

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.objectConstraints().isClosed())
            .as("Default OpenAPI semantics: additionalProperties is open unless declared false")
            .isFalse();
    }

    @Test
    void test_resolve_object_with_additional_properties_false_does_capture_allowed_keys_as_expected() {
        // given
        Schema<?> schema = new Schema<>().type("object");
        schema.addProperty("id", new Schema<>().type("string"));
        schema.addProperty("name", new Schema<>().type("string"));
        schema.setAdditionalProperties(Boolean.FALSE);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.objectConstraints().isClosed()).isTrue();
        assertThat(result.objectConstraints().allowedKeys()).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void test_resolve_object_with_additional_properties_true_does_stay_open_as_expected() {
        // given — explicit `true` is the same as the default
        Schema<?> schema = new Schema<>().type("object");
        schema.addProperty("id", new Schema<>().type("string"));
        schema.setAdditionalProperties(Boolean.TRUE);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.objectConstraints().isClosed())
            .as("additionalProperties: true is the open default — no key check should be emitted")
            .isFalse();
    }

    @Test
    void test_resolve_non_object_with_additional_properties_does_ignore_as_expected() {
        // given — additionalProperties is object-only; on a string it's meaningless
        Schema<?> schema = new Schema<>().type("string");
        schema.setAdditionalProperties(Boolean.FALSE);

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.objectConstraints())
            .as("Object constraints should only attach to object-typed schemas")
            .isEqualTo(ObjectConstraints.NONE);
    }

    @Test
    void test_resolve_non_number_with_minimum_does_ignore_constraint_as_expected() {
        // given — `minimum` is number-only; on a string it has no meaning
        Schema<?> schema = new Schema<>().type("string");
        schema.setMinimum(new BigDecimal("5"));

        // when
        FieldDescriptor result = resolver.resolve(schema);

        // then
        assertThat(result.numberConstraints())
            .as("Number constraints should only attach to number/integer schemas")
            .isEqualTo(NumberConstraints.NONE);
    }
}
