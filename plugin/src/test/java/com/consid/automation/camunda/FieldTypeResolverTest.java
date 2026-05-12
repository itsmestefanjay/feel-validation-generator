package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FieldTypeResolver.
 * Tests the resolution of field types from OpenAPI schemas.
 */
class FieldTypeResolverTest {

    private FieldTypeResolver resolver;
    private OpenAPI openAPI;

    @BeforeEach
    void setUp() {
        openAPI = new OpenAPI();
    }

    @Test
    void test_resolve_string_schema_does_return_string_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("String type should be resolved correctly").isEqualTo(FieldType.STRING);
    }

    @Test
    void test_resolve_string_with_date_format_does_return_date_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string").format("date");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("format: date should map to DATE").isEqualTo(FieldType.DATE);
    }

    @Test
    void test_resolve_string_with_date_time_format_does_return_date_time_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string").format("date-time");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("format: date-time should map to DATE_TIME").isEqualTo(FieldType.DATE_TIME);
    }

    @Test
    void test_resolve_string_with_time_format_does_return_time_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string").format("time");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("format: time should map to TIME").isEqualTo(FieldType.TIME);
    }

    @Test
    void test_resolve_string_with_unrecognised_format_does_fall_back_to_string_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string").format("uuid");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type())
            .as("Unknown string formats should still resolve to STRING, not UNKNOWN")
            .isEqualTo(FieldType.STRING);
    }

    @Test
    void test_resolve_number_schema_does_return_number_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("number");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Number type should be resolved correctly").isEqualTo(FieldType.NUMBER);
    }

    @Test
    void test_resolve_integer_schema_does_return_number_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("integer");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Integer type should be resolved as number").isEqualTo(FieldType.NUMBER);
    }

    @Test
    void test_resolve_boolean_schema_does_return_boolean_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("boolean");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Boolean type should be resolved correctly").isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void test_resolve_array_schema_does_return_array_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("array");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Array type should be resolved correctly").isEqualTo(FieldType.ARRAY);
    }

    @Test
    void test_resolve_object_schema_does_return_object_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("object");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Object type should be resolved correctly").isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_null_schema_does_return_unknown_as_expected() {
        resolver = new FieldTypeResolver(openAPI);

        FieldDescriptor result = resolver.resolve(null);

        assertThat(result.type()).as("Null schema should resolve to UNKNOWN").isEqualTo(FieldType.UNKNOWN);
    }

    @Test
    void test_resolve_unknown_schema_does_return_unknown_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("unsupported");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Unsupported type should resolve to UNKNOWN").isEqualTo(FieldType.UNKNOWN);
    }

    @Test
    void test_resolve_property_only_schema_does_return_object_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>();
        schema.addProperty("field1", new Schema<>().type("string"));

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Schema with properties but no type should resolve to OBJECT").isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_required_only_schema_does_return_object_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("field1"));

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Schema with required fields but no type should resolve to OBJECT").isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_reference_schema_does_return_referenced_type_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> referenced = new Schema<>().type("boolean");
        openAPI.components(new io.swagger.v3.oas.models.Components().addSchemas("RefSchema", referenced));

        Schema<?> schema = new Schema<>();
        schema.set$ref("#/components/schemas/RefSchema");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Referenced schema should resolve to referenced type").isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void test_resolve_reference_without_components_does_throw_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string");
        schema.set$ref("#/components/schemas/Missing");

        assertThatThrownBy(() -> resolver.resolveSchemaReference(schema))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("#/components/schemas/Missing");
    }

    @Test
    void test_resolve_enum_does_capture_values_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<String> schema = new Schema<>();
        schema.type("string");
        schema.setEnum(Arrays.asList("red", "green", "blue"));

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.type()).as("Type should still be STRING").isEqualTo(FieldType.STRING);
        assertThat(result.enumValues())
            .as("Enum values should be exposed via the descriptor")
            .containsExactly("red", "green", "blue");
    }

    @Test
    void test_resolve_does_return_empty_enum_when_schema_has_none_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string");

        FieldDescriptor result = resolver.resolve(schema);

        assertThat(result.hasEnum()).as("Schema without enum should produce hasEnum() = false").isFalse();
    }

    @Test
    void test_resolve_null_reference_does_return_null_as_expected() {
        resolver = new FieldTypeResolver(openAPI);

        Schema<?> resolved = resolver.resolveSchemaReference(null);

        assertThat(resolved).as("Null schema reference should resolve to null").isNull();
    }
}
