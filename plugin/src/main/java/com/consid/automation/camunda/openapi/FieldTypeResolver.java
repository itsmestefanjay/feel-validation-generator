package com.consid.automation.camunda.openapi;

import com.consid.automation.camunda.model.*;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
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

    /**
     * Canonical regexes injected when the schema declares {@code format: <name>}
     * but no explicit {@code pattern}. Deliberately permissive — author intent is
     * "looks like a UUID / email / URI", not RFC-perfect validation.
     */
    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String UUID_PATTERN =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static final String URI_PATTERN = "^[a-zA-Z][a-zA-Z0-9+.-]*:.+$";

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
        List<FeelLiteral> enumValues = enumValuesFrom(resolved);
        return new FieldDescriptor(
            type, nullable, enumValues, List.of(),
            arrayConstraints(type, resolved),
            stringConstraints(type, resolved),
            numberConstraints(type, resolved),
            objectConstraints(type, resolved));
    }

    /**
     * Captures {@code additionalProperties: false} as a closed allowed-key set.
     * The boolean {@code true} (or absence) leaves the object open. Schema-form
     * {@code additionalProperties} (a sub-schema specifying value types) is not
     * supported here — only the strict boolean-false case is currently honored.
     */
    private ObjectConstraints objectConstraints(FieldType type, Schema<?> schema) {
        if (type != FieldType.OBJECT) {
            return ObjectConstraints.NONE;
        }
        Object additionalProperties = schema.getAdditionalProperties();
        if (!Boolean.FALSE.equals(additionalProperties)) {
            return ObjectConstraints.NONE;
        }
        var properties = schema.getProperties();
        Set<String> allowed = properties == null
            ? Set.of()
            : new java.util.LinkedHashSet<>(properties.keySet());
        return new ObjectConstraints(allowed);
    }

    private ArrayConstraints arrayConstraints(FieldType type, Schema<?> schema) {
        if (type != FieldType.ARRAY) {
            return ArrayConstraints.NONE;
        }
        Integer minItems = schema.getMinItems();
        Integer maxItems = schema.getMaxItems();
        // `items` may itself be a $ref or use composition. resolve() handles both;
        // an unresolved items schema leaves the descriptor null (no element check).
        FieldDescriptor items = schema.getItems() == null ? null : resolve(schema.getItems());
        if (minItems == null && maxItems == null && items == null) {
            return ArrayConstraints.NONE;
        }
        return new ArrayConstraints(minItems, maxItems, items, Map.of());
    }

    private StringConstraints stringConstraints(FieldType type, Schema<?> schema) {
        // String subtypes (DATE / DATE_TIME / TIME) still carry length / pattern semantics.
        if (!isStringFamily(type)) {
            return StringConstraints.NONE;
        }
        Integer minLength = schema.getMinLength();
        Integer maxLength = schema.getMaxLength();
        String pattern = schema.getPattern();
        if (pattern == null || pattern.isEmpty()) {
            pattern = formatPattern(schema.getFormat());
        }
        if (minLength == null && maxLength == null && (pattern == null || pattern.isEmpty())) {
            return StringConstraints.NONE;
        }
        return new StringConstraints(minLength, maxLength, pattern);
    }

    /**
     * Returns the canonical regex for known string formats, or {@code null} if
     * the format isn't recognized. Authors who write an explicit {@code pattern}
     * always win over the format default.
     */
    private String formatPattern(String format) {
        if (format == null) {
            return null;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "email" -> EMAIL_PATTERN;
            case "uuid" -> UUID_PATTERN;
            case "uri", "url" -> URI_PATTERN;
            default -> null;
        };
    }

    private boolean isStringFamily(FieldType type) {
        return type == FieldType.STRING
            || type == FieldType.DATE
            || type == FieldType.DATE_TIME
            || type == FieldType.TIME;
    }

    /**
     * Captures numeric range and divisibility, normalizing the OpenAPI 3.0
     * boolean exclusive form (where {@code exclusiveMinimum: true} promotes
     * {@code minimum} to exclusive) into the OpenAPI 3.1 numeric form so the
     * expression builder sees a single representation.
     */
    private NumberConstraints numberConstraints(FieldType type, Schema<?> schema) {
        if (type != FieldType.NUMBER) {
            return NumberConstraints.NONE;
        }
        BigDecimal inclusiveMin = schema.getMinimum();
        BigDecimal exclusiveMin = schema.getExclusiveMinimumValue();
        if (exclusiveMin == null && Boolean.TRUE.equals(schema.getExclusiveMinimum()) && inclusiveMin != null) {
            exclusiveMin = inclusiveMin;
            inclusiveMin = null;
        }
        BigDecimal inclusiveMax = schema.getMaximum();
        BigDecimal exclusiveMax = schema.getExclusiveMaximumValue();
        if (exclusiveMax == null && Boolean.TRUE.equals(schema.getExclusiveMaximum()) && inclusiveMax != null) {
            exclusiveMax = inclusiveMax;
            inclusiveMax = null;
        }
        BigDecimal multipleOf = schema.getMultipleOf();
        if (inclusiveMin == null && exclusiveMin == null
            && inclusiveMax == null && exclusiveMax == null
            && multipleOf == null) {
            return NumberConstraints.NONE;
        }
        return new NumberConstraints(inclusiveMin, exclusiveMin, inclusiveMax, exclusiveMax, multipleOf);
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

    /**
     * Reads the field's allowed-value set. {@code enum} takes precedence; if absent,
     * {@code const} is promoted to a single-element enum so the existing in-check
     * renders the pinning constraint without any builder-side change.
     */
    private List<FeelLiteral> enumValuesFrom(Schema<?> schema) {
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return FeelLiteral.listOf(schema.getEnum());
        }
        if (schema.getConst() != null) {
            return List.of(FeelLiteral.of(schema.getConst()));
        }
        return List.of();
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
        return schema.getProperties() != null
            || schema.getRequired() != null
            || schema.getAllOf() != null
            || schema.getOneOf() != null
            || schema.getAnyOf() != null;
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
