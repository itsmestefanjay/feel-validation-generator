package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OpenApiOperationScanner.
 * Tests the filtering by HTTP method and media type and the heading format.
 */
class OpenApiOperationScannerTest {

    private static final List<String> DEFAULT_METHODS = List.of("POST", "PUT", "PATCH");
    private static final String JSON = "application/json";

    @Test
    void test_scan_does_return_empty_when_paths_are_missing_as_expected() {
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        Map<String, Schema<?>> result = scanner.scan(new OpenAPI());

        assertThat(result).as("Scanner should return empty map for spec without paths").isEmpty();
    }

    @Test
    void test_scan_does_collect_matching_operation_as_expected() {
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.POST, JSON, bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result)
            .as("Scanner should expose POST /customers schema under a FEEL heading")
            .containsExactly(Map.entry("# POST /customers", bodySchema));
    }

    @Test
    void test_scan_does_skip_operations_outside_configured_methods_as_expected() {
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.DELETE, JSON, bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result)
            .as("DELETE operations must not contribute when only POST/PUT/PATCH are configured")
            .isEmpty();
    }

    @Test
    void test_scan_does_skip_operations_without_request_body_as_expected() {
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(new Operation())));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result).as("Operations without a request body should be skipped").isEmpty();
    }

    @Test
    void test_scan_does_skip_operations_without_matching_media_type_as_expected() {
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.POST, "application/xml", bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result)
            .as("Body declared only for application/xml should be skipped when scanning JSON")
            .isEmpty();
    }

    @Test
    void test_scan_does_pick_configured_media_type_among_many_as_expected() {
        Schema<?> jsonSchema = new Schema<>().type("object").addProperty("json", new Schema<>().type("string"));
        Schema<?> xmlSchema = new Schema<>().type("object").addProperty("xml", new Schema<>().type("string"));
        Content content = new Content()
            .addMediaType(JSON, new MediaType().schema(jsonSchema))
            .addMediaType("application/xml", new MediaType().schema(xmlSchema));
        Operation operation = new Operation().requestBody(new RequestBody().content(content));
        OpenAPI openAPI = new OpenAPI().paths(new Paths().addPathItem(
            "/customers", new PathItem().post(operation)));
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, "application/xml");

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result)
            .as("Scanner should return the schema attached to the configured media type")
            .containsExactly(Map.entry("# POST /customers", xmlSchema));
    }

    @Test
    void test_scan_does_silently_skip_invalid_http_method_names_as_expected() {
        Schema<?> bodySchema = new Schema<>().type("object");
        OpenAPI openAPI = openApiWith("/customers", PathItem.HttpMethod.POST, JSON, bodySchema);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(List.of("POST", "BREW"), JSON);

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result)
            .as("Unknown HTTP method names should be ignored, valid ones still processed")
            .containsExactly(Map.entry("# POST /customers", bodySchema));
    }

    @Test
    void test_scan_does_preserve_path_order_as_expected() {
        Schema<?> first = new Schema<>().type("object").addProperty("a", new Schema<>().type("string"));
        Schema<?> second = new Schema<>().type("object").addProperty("b", new Schema<>().type("string"));
        Paths paths = new Paths()
            .addPathItem("/first", new PathItem().post(operationWithBody(JSON, first)))
            .addPathItem("/second", new PathItem().post(operationWithBody(JSON, second)));
        OpenAPI openAPI = new OpenAPI().paths(paths);
        OpenApiOperationScanner scanner = new OpenApiOperationScanner(DEFAULT_METHODS, JSON);

        Map<String, Schema<?>> result = scanner.scan(openAPI);

        assertThat(result.keySet())
            .as("Scanner should preserve insertion order of paths")
            .containsExactly("# POST /first", "# POST /second");
    }

    @Test
    void test_constructor_does_reject_null_arguments_as_expected() {
        assertThatThrownBy(() -> new OpenApiOperationScanner(null, JSON))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("httpMethods");
        assertThatThrownBy(() -> new OpenApiOperationScanner(DEFAULT_METHODS, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("mediaType");
    }

    private static OpenAPI openApiWith(String path, PathItem.HttpMethod method, String mediaType, Schema<?> schema) {
        PathItem pathItem = new PathItem();
        Operation operation = operationWithBody(mediaType, schema);
        switch (method) {
            case POST -> pathItem.post(operation);
            case PUT -> pathItem.put(operation);
            case PATCH -> pathItem.patch(operation);
            case DELETE -> pathItem.delete(operation);
            case GET -> pathItem.get(operation);
            default -> throw new IllegalArgumentException("Unsupported method in test fixture: " + method);
        }
        return new OpenAPI().paths(new Paths().addPathItem(path, pathItem));
    }

    private static Operation operationWithBody(String mediaType, Schema<?> schema) {
        Content content = new Content().addMediaType(mediaType, new MediaType().schema(schema));
        return new Operation().requestBody(new RequestBody().content(content));
    }
}
