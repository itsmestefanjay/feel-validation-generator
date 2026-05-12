package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.Map;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RequiredFieldsExtractor.
 * Tests the extraction of required fields from OpenAPI schemas.
 */
class RequiredFieldsExtractorTest {

    private RequiredFieldsExtractor extractor;
    private OpenAPI openAPI;
    private FieldTypeResolver typeResolver;

    @BeforeEach
    void setUp() {
        openAPI = new OpenAPI();
        typeResolver = new FieldTypeResolver(openAPI);
        extractor = new RequiredFieldsExtractor(typeResolver);
    }

    @Test
    void test_extract_simple_required_field_does_capture_entry_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username"));
        schema.addProperty("username", new Schema<>().type("string"));

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
                .as("Should extract simple required field")
                .containsEntry("username", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_multiple_required_fields_does_capture_each_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username", "email"));
        schema.addProperty("username", new Schema<>().type("string"));
        schema.addProperty("email", new Schema<>().type("string"));

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
                .as("Should extract multiple required fields")
                .containsEntry("username", FieldDescriptor.of(FieldType.STRING))
                .containsEntry("email", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_required_fields_does_ignore_optional_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username"));
        schema.addProperty("username", new Schema<>().type("string"));
        schema.addProperty("optional", new Schema<>().type("string"));

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
                .as("Should only extract required fields")
                .hasSize(1)
                .containsEntry("username", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_nested_fields_does_include_full_path_as_expected() {
        Schema<?> nestedSchema = new Schema<>();
        nestedSchema.setRequired(Arrays.asList("content"));
        nestedSchema.addProperty("content", new Schema<>().type("string"));

        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("data"));
        schema.addProperty("data", nestedSchema);

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
                .as("Should extract nested required fields with full path")
                .containsEntry("data", FieldDescriptor.of(FieldType.OBJECT))
                .containsEntry("data.content", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_allof_composition_does_merge_required_fields_as_expected() {
        // BaseObject schema
        Schema<?> baseSchema = new Schema<>();
        baseSchema.setRequired(Arrays.asList("id"));
        baseSchema.addProperty("id", new Schema<>().type("string"));

        // DataObject schema
        Schema<?> dataSchema = new Schema<>();
        dataSchema.setRequired(Arrays.asList("content"));
        dataSchema.addProperty("content", new Schema<>().type("string"));

        // Composed schema using allOf
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setAllOf(Arrays.asList(baseSchema, dataSchema));

        Map<String, FieldDescriptor> result = extractor.extract(composedSchema);

        assertThat(result)
                .as("Should extract required fields from all allOf schemas")
                .containsEntry("id", FieldDescriptor.of(FieldType.STRING))
                .containsEntry("content", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_oneof_composition_does_collect_each_required_as_expected() {
        // FirstOption schema
        Schema<?> firstOption = new Schema<>();
        firstOption.setRequired(Arrays.asList("name"));
        firstOption.addProperty("name", new Schema<>().type("string"));

        // SecondOption schema
        Schema<?> secondOption = new Schema<>();
        secondOption.setRequired(Arrays.asList("email"));
        secondOption.addProperty("email", new Schema<>().type("string"));

        // Composed schema using oneOf
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setOneOf(Arrays.asList(firstOption, secondOption));

        Map<String, FieldDescriptor> result = extractor.extract(composedSchema);

        assertThat(result)
                .as("Should extract required fields from oneOf schemas")
                .containsEntry("name", FieldDescriptor.of(FieldType.STRING))
                .containsEntry("email", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_anyof_composition_does_collect_each_required_as_expected() {
        // FirstOption schema
        Schema<?> firstOption = new Schema<>();
        firstOption.setRequired(Arrays.asList("username"));
        firstOption.addProperty("username", new Schema<>().type("string"));

        // SecondOption schema
        Schema<?> secondOption = new Schema<>();
        secondOption.setRequired(Arrays.asList("userId"));
        secondOption.addProperty("userId", new Schema<>().type("string"));

        // Composed schema using anyOf
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setAnyOf(Arrays.asList(firstOption, secondOption));

        Map<String, FieldDescriptor> result = extractor.extract(composedSchema);

        assertThat(result)
                .as("Should extract required fields from anyOf schemas")
                .containsEntry("username", FieldDescriptor.of(FieldType.STRING))
                .containsEntry("userId", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_from_empty_schema_does_return_empty_as_expected() {
        Schema<?> schema = new Schema<>();

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
                .as("Empty schema should return empty map")
                .isEmpty();
    }

    @Test
    void test_extract_from_null_schema_does_return_empty_as_expected() {
        Map<String, FieldDescriptor> result = extractor.extract(null);

        assertThat(result)
                .as("Null schema should return empty map")
                .isEmpty();
    }

    @Test
    void test_extract_shared_component_does_expand_at_every_reference_as_expected() {
        Schema<?> sharedAddress = new Schema<>();
        sharedAddress.setRequired(Arrays.asList("city", "street"));
        sharedAddress.addProperty("city", new Schema<>().type("string"));
        sharedAddress.addProperty("street", new Schema<>().type("string"));

        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("billingAddress", "shippingAddress"));
        schema.addProperty("billingAddress", sharedAddress);
        schema.addProperty("shippingAddress", sharedAddress);

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
            .as("Same schema referenced from two siblings must be expanded at both paths")
            .containsKeys(
                "billingAddress",
                "shippingAddress",
                "billingAddress.city",
                "billingAddress.street",
                "shippingAddress.city",
                "shippingAddress.street"
            );
    }

    @Test
    void test_extract_deeply_nested_fields_does_include_all_paths_as_expected() {
        Schema<?> level3 = new Schema<>();
        level3.setRequired(Arrays.asList("value"));
        level3.addProperty("value", new Schema<>().type("string"));

        Schema<?> level2 = new Schema<>();
        level2.setRequired(Arrays.asList("deep"));
        level2.addProperty("deep", level3);

        Schema<?> level1 = new Schema<>();
        level1.setRequired(Arrays.asList("nested"));
        level1.addProperty("nested", level2);

        Map<String, FieldDescriptor> result = extractor.extract(level1);

        assertThat(result)
                .as("Should extract deeply nested fields")
                .containsEntry("nested", FieldDescriptor.of(FieldType.OBJECT))
                .containsEntry("nested.deep", FieldDescriptor.of(FieldType.OBJECT))
                .containsEntry("nested.deep.value", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_mixed_types_does_preserve_field_types_as_expected() {
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("name", "age", "active", "tags"));
        schema.addProperty("name", new Schema<>().type("string"));
        schema.addProperty("age", new Schema<>().type("integer"));
        schema.addProperty("active", new Schema<>().type("boolean"));
        schema.addProperty("tags", new Schema<>().type("array"));

        Map<String, FieldDescriptor> result = extractor.extract(schema);

        assertThat(result)
                .as("Should extract and correctly type all field types")
                .containsEntry("name", FieldDescriptor.of(FieldType.STRING))
                .containsEntry("age", FieldDescriptor.of(FieldType.NUMBER))
                .containsEntry("active", FieldDescriptor.of(FieldType.BOOLEAN))
                .containsEntry("tags", FieldDescriptor.of(FieldType.ARRAY));
    }
}
