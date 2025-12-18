package com.consid.automation.camunda;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Locale;
import java.util.Optional;

/**
 * Determines the type of a field from an OpenAPI schema.
 * Single responsibility: map schema types to FieldType enum.
 */
public class FieldTypeResolver {

    private static final String SCHEMA_PATH_PREFIX = "#/components/schemas/";
    private final OpenAPI openAPI;

    public FieldTypeResolver(OpenAPI openAPI) {
        this.openAPI = openAPI;
    }

    /**
     * Determines the field type from a schema.
     * Resolves schema references before type determination.
     */
    public FieldType resolve(Schema<?> schema) {
        if (schema == null) {
            return FieldType.UNKNOWN;
        }

        // Resolve schema reference if needed
        schema = resolveSchemaReference(schema);
        if (schema == null) {
            return FieldType.UNKNOWN;
        }

        return mapTypeToFieldType(schema.getType(), schema);
    }

    /**
     * Maps OpenAPI type string to FieldType enum.
     */
    private FieldType mapTypeToFieldType(String type, Schema<?> schema) {
        if (type == null) {
            return schemaIndicatesObject(schema) ? FieldType.OBJECT : FieldType.UNKNOWN;
        }

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "string" -> FieldType.STRING;
            case "number", "integer" -> FieldType.NUMBER;
            case "boolean" -> FieldType.BOOLEAN;
            case "array" -> FieldType.ARRAY;
            case "object" -> FieldType.OBJECT;
            default -> FieldType.UNKNOWN;
        };
    }

    private boolean schemaIndicatesObject(Schema<?> schema) {
        return schema.getProperties() != null || schema.getRequired() != null;
    }

    /**
     * Resolves a schema reference to the actual schema definition.
     */
    public Schema<?> resolveSchemaReference(Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        
        String ref = schema.get$ref();
        if (ref != null && ref.startsWith(SCHEMA_PATH_PREFIX)) {
            String schemaName = ref.substring(SCHEMA_PATH_PREFIX.length());
            return Optional.ofNullable(openAPI.getComponents())
                .map(components -> components.getSchemas())
                .map(schemas -> schemas.get(schemaName))
                .orElse(schema);
        }
        
        return schema;
    }
}
