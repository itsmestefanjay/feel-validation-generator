package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RequiredFieldsExtractor.
 */
class RequiredFieldsExtractorTest {

    private RequiredFieldsExtractor extractor;

    @BeforeEach
    void setUp() {
        OpenAPI openAPI = new OpenAPI();
        FieldTypeResolver typeResolver = new FieldTypeResolver(openAPI);
        extractor = new RequiredFieldsExtractor(typeResolver);
    }

    @Test
    void test_extract_simple_required_field_does_capture_entry_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username"));
        schema.addProperty("username", new Schema<>().type("string"));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        assertThat(result).containsEntry("username", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_required_fields_does_ignore_optional_fields_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("username"));
        schema.addProperty("username", new Schema<>().type("string"));
        schema.addProperty("optional", new Schema<>().type("string"));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        assertThat(result)
            .hasSize(1)
            .containsEntry("username", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_does_emit_full_dot_path_for_nested_fields_as_expected() {
        // given
        Schema<?> nestedSchema = new Schema<>();
        nestedSchema.setRequired(Arrays.asList("content"));
        nestedSchema.addProperty("content", new Schema<>().type("string"));
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("data"));
        schema.addProperty("data", nestedSchema);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        assertThat(result)
            .containsEntry("data", FieldDescriptor.of(FieldType.OBJECT))
            .containsEntry("data.content", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_allof_does_merge_required_fields_from_each_branch_as_expected() {
        // given
        Schema<?> baseSchema = new Schema<>();
        baseSchema.setRequired(Arrays.asList("id"));
        baseSchema.addProperty("id", new Schema<>().type("string"));
        Schema<?> dataSchema = new Schema<>();
        dataSchema.setRequired(Arrays.asList("content"));
        dataSchema.addProperty("content", new Schema<>().type("string"));
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setAllOf(Arrays.asList(baseSchema, dataSchema));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(composedSchema);

        // then
        assertThat(result)
            .containsEntry("id", FieldDescriptor.of(FieldType.STRING))
            .containsEntry("content", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_oneof_does_collect_required_fields_from_each_branch_as_expected() {
        // given
        Schema<?> firstOption = new Schema<>();
        firstOption.setRequired(Arrays.asList("name"));
        firstOption.addProperty("name", new Schema<>().type("string"));
        Schema<?> secondOption = new Schema<>();
        secondOption.setRequired(Arrays.asList("email"));
        secondOption.addProperty("email", new Schema<>().type("string"));
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setOneOf(Arrays.asList(firstOption, secondOption));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(composedSchema);

        // then
        assertThat(result)
            .containsEntry("name", FieldDescriptor.of(FieldType.STRING))
            .containsEntry("email", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_anyof_does_collect_required_fields_from_each_branch_as_expected() {
        // given
        Schema<?> firstOption = new Schema<>();
        firstOption.setRequired(Arrays.asList("username"));
        firstOption.addProperty("username", new Schema<>().type("string"));
        Schema<?> secondOption = new Schema<>();
        secondOption.setRequired(Arrays.asList("userId"));
        secondOption.addProperty("userId", new Schema<>().type("string"));
        Schema<?> composedSchema = new Schema<>();
        composedSchema.setAnyOf(Arrays.asList(firstOption, secondOption));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(composedSchema);

        // then
        assertThat(result)
            .containsEntry("username", FieldDescriptor.of(FieldType.STRING))
            .containsEntry("userId", FieldDescriptor.of(FieldType.STRING));
    }

    @Test
    void test_extract_empty_schema_does_return_empty_map_as_expected() {
        // when
        Map<String, FieldDescriptor> result = extractor.extract(new Schema<>());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_extract_null_schema_does_return_empty_map_as_expected() {
        // when
        Map<String, FieldDescriptor> result = extractor.extract(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void test_extract_dependent_required_does_mark_field_as_conditional_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.addProperty("shippingAddress", new Schema<>().type("object"));
        schema.addProperty("shippingCarrier", new Schema<>().type("string"));
        schema.setDependentRequired(Map.of("shippingAddress", List.of("shippingCarrier")));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        FieldDescriptor carrier = result.get("shippingCarrier");
        assertThat(carrier).isNotNull();
        assertThat(carrier.type()).isEqualTo(FieldType.STRING);
        assertThat(carrier.isConditional()).isTrue();
        assertThat(carrier.dependsOn()).containsExactly("shippingAddress");
    }

    @Test
    void test_extract_dependent_required_does_not_downgrade_unconditional_required_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.setRequired(List.of("shippingCarrier"));
        schema.addProperty("shippingAddress", new Schema<>().type("object"));
        schema.addProperty("shippingCarrier", new Schema<>().type("string"));
        schema.setDependentRequired(Map.of("shippingAddress", List.of("shippingCarrier")));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        assertThat(result.get("shippingCarrier").isConditional())
            .as("Field already required unconditionally should not become conditional")
            .isFalse();
    }

    @Test
    void test_extract_dependent_required_does_or_merge_multiple_triggers_as_expected() {
        // given
        Schema<?> schema = new Schema<>();
        schema.addProperty("a", new Schema<>().type("string"));
        schema.addProperty("b", new Schema<>().type("string"));
        schema.addProperty("c", new Schema<>().type("string"));
        schema.setDependentRequired(Map.of(
            "a", List.of("c"),
            "b", List.of("c")
        ));

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        assertThat(result.get("c").dependsOn())
            .as("c should be triggered by either a or b being present")
            .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void test_extract_self_referential_schema_does_terminate_at_cycle_as_expected() {
        // given — a node that references itself; without cycle detection this would recurse forever
        Schema<?> node = new Schema<>();
        node.type("object");
        node.setRequired(List.of("value", "next"));
        node.addProperty("value", new Schema<>().type("string"));
        node.addProperty("next", node);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(node);

        // then
        assertThat(result)
            .as("Cycle detection should yield the top-level fields and stop before recursing into the self-reference")
            .containsOnlyKeys("value", "next");
    }

    @Test
    void test_extract_shared_component_does_expand_at_every_reference_as_expected() {
        // given
        Schema<?> sharedAddress = new Schema<>();
        sharedAddress.setRequired(Arrays.asList("city", "street"));
        sharedAddress.addProperty("city", new Schema<>().type("string"));
        sharedAddress.addProperty("street", new Schema<>().type("string"));
        Schema<?> schema = new Schema<>();
        schema.setRequired(Arrays.asList("billingAddress", "shippingAddress"));
        schema.addProperty("billingAddress", sharedAddress);
        schema.addProperty("shippingAddress", sharedAddress);

        // when
        Map<String, FieldDescriptor> result = extractor.extract(schema);

        // then
        assertThat(result).containsKeys(
            "billingAddress",
            "shippingAddress",
            "billingAddress.city",
            "billingAddress.street",
            "shippingAddress.city",
            "shippingAddress.street"
        );
    }
}
