package com.consid.automation.camunda;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts required fields from OpenAPI schemas.
 */
public class RequiredFieldsExtractor {

    private final FieldTypeResolver typeResolver;

    public RequiredFieldsExtractor(FieldTypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    /**
     * Extracts all required fields from a schema.
     * Returns a map of field paths to their types.
     */
    public Map<String, FieldType> extract(Schema<?> schema) {
        Map<String, FieldType> requiredFields = new LinkedHashMap<>();
        // Track visited schema instances by identity to break reference cycles safely.
        Set<Schema<?>> processedSchemas = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRequiredFields(schema, requiredFields, "", processedSchemas);
        return requiredFields;
    }

    /**
     * Recursively collects required fields from a schema.
     */
    private void collectRequiredFields(Schema<?> schema, Map<String, FieldType> requiredFields,
                                       String pathPrefix, Set<Schema<?>> processedSchemas) {
        if (schema == null) {
            return;
        }

        // Resolve schema reference first (handles $ref at any level)
        schema = typeResolver.resolveSchemaReference(schema);
        if (schema == null) {
            return;
        }

        if (!processedSchemas.add(schema)) {
            return;
        }

        // Process direct required fields
        processDirectRequiredFields(schema, requiredFields, pathPrefix);

        processComposition(schema.getAllOf(), requiredFields, pathPrefix, processedSchemas);
        processComposition(schema.getOneOf(), requiredFields, pathPrefix, processedSchemas);
        processComposition(schema.getAnyOf(), requiredFields, pathPrefix, processedSchemas);

        // Process nested properties
        processNestedProperties(schema, requiredFields, pathPrefix, processedSchemas);
    }

    private void processDirectRequiredFields(Schema<?> schema, Map<String, FieldType> requiredFields, String pathPrefix) {
        if (schema.getRequired() == null || schema.getProperties() == null) {
            return;
        }

        List<String> requiredFieldsList = new ArrayList<>(schema.getRequired());
        Collections.sort(requiredFieldsList);

        var properties = schema.getProperties();
        for (String requiredField : requiredFieldsList) {
            String fullFieldPath = buildFieldPath(pathPrefix, requiredField);
            if (!requiredFields.containsKey(fullFieldPath)) {
                Schema<?> propertySchema = properties.get(requiredField);
                FieldType fieldType = typeResolver.resolve(propertySchema);
                requiredFields.put(fullFieldPath, fieldType);
            }
        }
    }

    private void processComposition(List<?> schemas, Map<String, FieldType> requiredFields,
                                    String pathPrefix, Set<Schema<?>> processedSchemas) {
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        for (Object element : schemas) {
            if (element instanceof Schema<?> composedSchema) {
                collectRequiredFields(composedSchema, requiredFields, pathPrefix, processedSchemas);
            }
        }
    }

    private void processNestedProperties(Schema<?> schema, Map<String, FieldType> requiredFields,
                                         String pathPrefix, Set<Schema<?>> processedSchemas) {
        if (schema.getProperties() == null) {
            return;
        }

        List<String> propertyNames = new ArrayList<>(schema.getProperties().keySet());
        Collections.sort(propertyNames);

        for (String propName : propertyNames) {
            Schema<?> propSchema = (Schema<?>) schema.getProperties().get(propName);
            String newPath = buildFieldPath(pathPrefix, propName);

            FieldType fieldType = typeResolver.resolve(propSchema);
            if (fieldType == FieldType.OBJECT) {
                collectRequiredFields(propSchema, requiredFields, newPath, processedSchemas);
            }
        }
    }

    private String buildFieldPath(String pathPrefix, String fieldName) {
        return pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
    }
}