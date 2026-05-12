package com.consid.automation.camunda;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        FieldType type = mapTypeToFieldType(primaryType(resolved), resolved);
        boolean nullable = isNullable(resolved);
        List<Object> enumValues = resolved.getEnum() == null
            ? List.of()
            : List.copyOf(resolved.getEnum());
        return new FieldDescriptor(type, nullable, enumValues);
    }

    /**
     * Returns the schema's primary type, preferring OpenAPI 3.0's single
     * {@code type} but falling back to the first non-"null" entry from
     * OpenAPI 3.1's {@code types} array.
     */
    private String primaryType(Schema<?> schema) {
        String singleType = schema.getType();
        if (singleType != null) {
            return singleType;
        }
        Set<String> types = schema.getTypes();
        if (types == null) {
            return null;
        }
        for (String candidate : types) {
            if (candidate != null && !"null".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isNullable(Schema<?> schema) {
        if (Boolean.TRUE.equals(schema.getNullable())) {
            return true;
        }
        Set<String> types = schema.getTypes();
        if (types == null) {
            return false;
        }
        return types.stream().anyMatch(t -> "null".equalsIgnoreCase(t));
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
