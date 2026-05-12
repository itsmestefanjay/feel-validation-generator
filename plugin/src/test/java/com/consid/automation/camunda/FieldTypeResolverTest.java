package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        // given // when
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
        // given // when
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
        assertThat(result.enumValues()).containsExactly("red", "green", "blue");
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
}
