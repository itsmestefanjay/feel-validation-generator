package com.consid.bpm.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.consid.automation.camunda.FieldType;
import com.consid.automation.camunda.FieldTypeResolver;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

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

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("String type should be resolved correctly").isEqualTo(FieldType.STRING);
    }

    @Test
    void test_resolve_number_schema_does_return_number_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("number");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Number type should be resolved correctly").isEqualTo(FieldType.NUMBER);
    }

    @Test
    void test_resolve_integer_schema_does_return_number_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("integer");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Integer type should be resolved as number").isEqualTo(FieldType.NUMBER);
    }

    @Test
    void test_resolve_boolean_schema_does_return_boolean_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("boolean");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Boolean type should be resolved correctly").isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void test_resolve_array_schema_does_return_array_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("array");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Array type should be resolved correctly").isEqualTo(FieldType.ARRAY);
    }

    @Test
    void test_resolve_object_schema_does_return_object_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("object");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Object type should be resolved correctly").isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_null_schema_does_return_unknown_as_expected() {
        resolver = new FieldTypeResolver(openAPI);

        FieldType result = resolver.resolve(null);

        assertThat(result).as("Null schema should resolve to UNKNOWN").isEqualTo(FieldType.UNKNOWN);
    }

    @Test
    void test_resolve_unknown_schema_does_return_unknown_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("unsupported");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Unsupported type should resolve to UNKNOWN").isEqualTo(FieldType.UNKNOWN);
    }

    @Test
    void test_resolve_property_only_schema_does_return_object_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>();
        schema.addProperty("field1", new Schema<>().type("string"));

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Schema with properties but no type should resolve to OBJECT").isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_required_only_schema_does_return_object_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("field1"));

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Schema with required fields but no type should resolve to OBJECT").isEqualTo(FieldType.OBJECT);
    }

    @Test
    void test_resolve_reference_schema_does_return_referenced_type_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> referenced = new Schema<>().type("boolean");
        openAPI.components(new io.swagger.v3.oas.models.Components().addSchemas("RefSchema", referenced));

        Schema<?> schema = new Schema<>();
        schema.set$ref("#/components/schemas/RefSchema");

        FieldType result = resolver.resolve(schema);

        assertThat(result).as("Referenced schema should resolve to referenced type").isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void test_resolve_reference_without_components_does_return_original_schema_as_expected() {
        resolver = new FieldTypeResolver(openAPI);
        Schema<?> schema = new Schema<>().type("string");
        schema.set$ref("#/components/schemas/Missing");

        Schema<?> resolved = resolver.resolveSchemaReference(schema);

        assertThat(resolved).as("Schema should remain unchanged when components are missing").isSameAs(schema);
    }

    @Test
    void test_resolve_null_reference_does_return_null_as_expected() {
        resolver = new FieldTypeResolver(openAPI);

        Schema<?> resolved = resolver.resolveSchemaReference(null);

        assertThat(resolved).as("Null schema reference should resolve to null").isNull();
    }
}
