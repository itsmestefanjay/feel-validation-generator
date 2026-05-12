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
    public Map<String, FieldDescriptor> extract(Schema<?> schema) {
        Map<String, FieldDescriptor> requiredFields = new LinkedHashMap<>();
        Set<Schema<?>> activeStack = Collections.newSetFromMap(new IdentityHashMap<>());
        collectRequiredFields(schema, requiredFields, "", activeStack);
        return requiredFields;
    }

    /**
     * Recursively collects required fields from a schema.
     * {@code activeStack} tracks schemas currently on the recursion path so that
     * a self-referential schema terminates while a component reused at multiple
     * field paths is still expanded each time.
     */
    private void collectRequiredFields(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                       String pathPrefix, Set<Schema<?>> activeStack) {
        if (schema == null) {
            return;
        }

        schema = typeResolver.resolveSchemaReference(schema);
        if (schema == null) {
            return;
        }

        if (!activeStack.add(schema)) {
            return;
        }
        try {
            processDirectRequiredFields(schema, requiredFields, pathPrefix);
            processComposition(schema.getAllOf(), requiredFields, pathPrefix, activeStack);
            processComposition(schema.getOneOf(), requiredFields, pathPrefix, activeStack);
            processComposition(schema.getAnyOf(), requiredFields, pathPrefix, activeStack);
            processNestedProperties(schema, requiredFields, pathPrefix, activeStack);
        } finally {
            activeStack.remove(schema);
        }
    }

    private void processDirectRequiredFields(Schema<?> schema, Map<String, FieldDescriptor> requiredFields, String pathPrefix) {
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
                FieldDescriptor descriptor = typeResolver.resolve(propertySchema);
                requiredFields.put(fullFieldPath, descriptor);
            }
        }
    }

    private void processComposition(List<?> schemas, Map<String, FieldDescriptor> requiredFields,
                                    String pathPrefix, Set<Schema<?>> activeStack) {
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        for (Object element : schemas) {
            if (element instanceof Schema<?> composedSchema) {
                collectRequiredFields(composedSchema, requiredFields, pathPrefix, activeStack);
            }
        }
    }

    @SuppressWarnings("rawtypes") // Schema's API exposes Map<String, Schema> raw.
    private void processNestedProperties(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                         String pathPrefix, Set<Schema<?>> activeStack) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }

        List<String> propertyNames = new ArrayList<>(properties.keySet());
        Collections.sort(propertyNames);

        for (String propName : propertyNames) {
            Schema<?> propSchema = properties.get(propName);
            String newPath = buildFieldPath(pathPrefix, propName);

            FieldDescriptor descriptor = typeResolver.resolve(propSchema);
            if (descriptor.type() == FieldType.OBJECT) {
                collectRequiredFields(propSchema, requiredFields, newPath, activeStack);
            }
        }
    }

    private String buildFieldPath(String pathPrefix, String fieldName) {
        return pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
    }
}