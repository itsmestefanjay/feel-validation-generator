package com.consid.automation.camunda;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     * Determines the field descriptor from a schema, resolving references first.
     */
    public FieldDescriptor resolve(Schema<?> schema) {
        if (schema == null) {
            return FieldDescriptor.of(FieldType.UNKNOWN);
        }

        Schema<?> resolved = resolveSchemaReference(schema);
        if (resolved == null) {
            return FieldDescriptor.of(FieldType.UNKNOWN);
        }

        FieldType type = mapTypeToFieldType(resolved.getType(), resolved);
        List<Object> enumValues = resolved.getEnum() == null
            ? List.of()
            : List.copyOf(resolved.getEnum());
        return new FieldDescriptor(type, false, enumValues);
    }

    /**
     * Maps OpenAPI type string to FieldType enum.
     */
    private FieldType mapTypeToFieldType(String type, Schema<?> schema) {
        if (type == null) {
            return schemaIndicatesObject(schema) ? FieldType.OBJECT : FieldType.UNKNOWN;
        }

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "string" -> stringSubtype(schema.getFormat());
            case "number", "integer" -> FieldType.NUMBER;
            case "boolean" -> FieldType.BOOLEAN;
            case "array" -> FieldType.ARRAY;
            case "object" -> FieldType.OBJECT;
            default -> FieldType.UNKNOWN;
        };
    }

    private FieldType stringSubtype(String format) {
        if (format == null) {
            return FieldType.STRING;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "date" -> FieldType.DATE;
            case "date-time" -> FieldType.DATE_TIME;
            case "time" -> FieldType.TIME;
            default -> FieldType.STRING;
        };
    }

    private boolean schemaIndicatesObject(Schema<?> schema) {
        return schema.getProperties() != null || schema.getRequired() != null;
    }

    /**
     * Resolves a schema reference to the actual schema definition.
     * Throws {@link IllegalStateException} when the reference cannot be resolved
     * — a broken spec should fail loud rather than emit UNKNOWN rules.
     */
    public Schema<?> resolveSchemaReference(Schema<?> schema) {
        if (schema == null) {
            return null;
        }

        String ref = schema.get$ref();
        if (ref == null || !ref.startsWith(SCHEMA_PATH_PREFIX)) {
            return schema;
        }

        String schemaName = ref.substring(SCHEMA_PATH_PREFIX.length());
        Components components = openAPI.getComponents();
        Map<String, Schema> schemas = components == null ? null : components.getSchemas();
        Schema<?> resolved = schemas == null ? null : schemas.get(schemaName);
        if (resolved == null) {
            throw new IllegalStateException("Unresolved $ref: " + ref);
        }
        return resolved;
    }
}
