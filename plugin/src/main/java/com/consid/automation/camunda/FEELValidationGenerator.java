package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Main generator class for FEEL validation rules from OpenAPI specifications.
 * Orchestrates the overall flow: parse, extract, generate, write.
 */
public class FEELValidationGenerator {

    private final String openApiSpecPath;
    private final String outputFilePath;
    private final ValidationRuleBuilder ruleBuilder;
    private final List<String> httpMethods;

    private FEELValidationGenerator(Builder builder) {
        this.openApiSpecPath = builder.openApiSpecPath;
        this.outputFilePath = builder.outputFilePath;
        this.ruleBuilder = builder.customRuleBuilder != null
            ? builder.customRuleBuilder
            : new FEELRuleGenerator(builder.addResponse, builder.successStatusCode, builder.failureStatusCode);
        this.httpMethods = builder.httpMethods;
    }

    /**
     * Generates FEEL validation rules from an OpenAPI specification.
     */
    public void generate() throws IOException {
        OpenAPI openAPI = parseOpenAPI();
        Map<String, List<ValidationRule>> rulesByEndpoint = extractValidationRules(openAPI);
        writeOutput(rulesByEndpoint);
    }

    /**
     * Parses the OpenAPI specification file.
     */
    private OpenAPI parseOpenAPI() throws IOException {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        OpenAPI openAPI = parser.read(openApiSpecPath);

        if (openAPI == null) {
            throw new IOException("Failed to parse OpenAPI specification: " + openApiSpecPath);
        }

        return openAPI;
    }

    /**
     * Extracts validation rules from the OpenAPI specification.
     */
    private Map<String, List<ValidationRule>> extractValidationRules(OpenAPI openAPI) {
        Map<String, List<ValidationRule>> rulesByEndpoint = new LinkedHashMap<>();
        FieldTypeResolver typeResolver = new FieldTypeResolver(openAPI);
        RequiredFieldsExtractor fieldsExtractor = new RequiredFieldsExtractor(typeResolver);

        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();
                processPaths(path, pathItem, rulesByEndpoint, fieldsExtractor);
            }
        }

        return rulesByEndpoint;
    }

    /**
     * Processes all HTTP operations for a given path.
     */
    private void processPaths(String path, PathItem pathItem, Map<String, List<ValidationRule>> rulesByEndpoint,
                              RequiredFieldsExtractor fieldsExtractor) {
        Map<String, Operation> operations = extractOperations(pathItem);

        for (Map.Entry<String, Operation> operationEntry : operations.entrySet()) {
            String method = operationEntry.getKey();
            Operation operation = operationEntry.getValue();
            List<ValidationRule> endpointRules = new ArrayList<>();
            processOperation(operation, endpointRules, fieldsExtractor);

            if (!endpointRules.isEmpty()) {
                String heading = formatEndpointHeading(method, path);
                rulesByEndpoint.put(heading, endpointRules);
            }
        }
    }

    private String formatEndpointHeading(String method, String path) {
        return "# " + method + " " + path;
    }

    /**
     * Extracts HTTP operations from a path item.
     */
    private Map<String, Operation> extractOperations(PathItem pathItem) {
        Map<String, Operation> operations = new LinkedHashMap<>();
        Map<PathItem.HttpMethod, Operation> availableOperations = pathItem.readOperationsMap();
        if (availableOperations == null || availableOperations.isEmpty()) {
            return operations;
        }

        for (String method : httpMethods) {
            String normalizedMethod = method.toUpperCase(Locale.ROOT);
            PathItem.HttpMethod httpMethod;
            try {
                httpMethod = PathItem.HttpMethod.valueOf(normalizedMethod);
            } catch (IllegalArgumentException ex) {
                continue; // skip unsupported methods instead of failing
            }
            Operation operation = availableOperations.get(httpMethod);
            if (operation != null) {
                operations.put(normalizedMethod, operation);
            }
        }

        return operations;
    }

    /**
     * Processes a single operation and generates validation rules.
     */
    private void processOperation(Operation operation, List<ValidationRule> rules,
                                 RequiredFieldsExtractor fieldsExtractor) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }

        operation.getRequestBody().getContent().forEach((mediaType, mediaTypeObj) -> {
            if (mediaTypeObj.getSchema() != null) {
                generateRulesForSchema(mediaTypeObj.getSchema(), rules, fieldsExtractor);
            }
        });
    }

    /**
     * Generates validation rules for a schema.
     */
    private void generateRulesForSchema(io.swagger.v3.oas.models.media.Schema<?> schema,
                                       List<ValidationRule> rules,
                                       RequiredFieldsExtractor fieldsExtractor) {
        Map<String, FieldType> requiredFields = fieldsExtractor.extract(schema);

        for (Map.Entry<String, FieldType> entry : requiredFields.entrySet()) {
            String fieldPath = entry.getKey();
            FieldType fieldType = entry.getValue();

            ValidationRule rule = ruleBuilder.createRule(fieldPath, fieldType);
            rules.add(rule);
        }
    }

    /**
     * Writes the validation rules to an output file.
     */
    private void writeOutput(Map<String, List<ValidationRule>> rulesByEndpoint) throws IOException {
        String renderedOutput = ruleBuilder.render(rulesByEndpoint);
        var outputPath = Paths.get(outputFilePath);
        var parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, renderedOutput);
    }

    public String getOpenApiSpecPath() {
        return openApiSpecPath;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String openApiSpecPath;
        private String outputFilePath;
        private boolean addResponse = false;
        private int successStatusCode = 201;
        private int failureStatusCode = 400;
        private List<String> httpMethods = List.of("POST", "PUT", "PATCH");
        private ValidationRuleBuilder customRuleBuilder;

        private Builder() {
        }

        public Builder withOpenApiPath(String openApiSpecPath) {
            this.openApiSpecPath = Objects.requireNonNull(openApiSpecPath, "openApiSpecPath");
            return this;
        }

        public Builder withOutputFilePath(String outputFilePath) {
            this.outputFilePath = Objects.requireNonNull(outputFilePath, "outputFilePath");
            return this;
        }

        public Builder withResponse(boolean addResponse) {
            this.addResponse = addResponse;
            return this;
        }

        public Builder withSuccessCode(int statusCode) {
            this.successStatusCode = statusCode;
            return this;
        }

        public Builder withFailCode(int statusCode) {
            this.failureStatusCode = statusCode;
            return this;
        }

        public Builder withHttpMethods(List<String> httpMethods) {
            this.httpMethods = httpMethods == null ? List.of() : List.copyOf(httpMethods);
            return this;
        }

        public Builder withRuleBuilder(ValidationRuleBuilder ruleBuilder) {
            this.customRuleBuilder = ruleBuilder;
            return this;
        }

        public FEELValidationGenerator build() {
            return new FEELValidationGenerator(this);
        }
    }
}
