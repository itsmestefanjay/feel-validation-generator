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
            processDependentRequired(schema, requiredFields, pathPrefix);
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

    @SuppressWarnings("rawtypes")
    private void processDependentRequired(Schema<?> schema,
                                          Map<String, FieldDescriptor> requiredFields,
                                          String pathPrefix) {
        Map<String, List<String>> dependentRequired = schema.getDependentRequired();
        if (dependentRequired == null || dependentRequired.isEmpty()) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        List<String> triggers = new ArrayList<>(dependentRequired.keySet());
        Collections.sort(triggers);
        for (String trigger : triggers) {
            String triggerPath = buildFieldPath(pathPrefix, trigger);
            List<String> dependents = new ArrayList<>(dependentRequired.get(trigger));
            Collections.sort(dependents);
            for (String dependent : dependents) {
                addConditional(properties, dependent, triggerPath, pathPrefix, requiredFields);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void addConditional(Map<String, Schema> properties,
                                String fieldName,
                                String triggerPath,
                                String pathPrefix,
                                Map<String, FieldDescriptor> requiredFields) {
        String fieldPath = buildFieldPath(pathPrefix, fieldName);
        FieldDescriptor existing = requiredFields.get(fieldPath);
        if (existing != null && !existing.isConditional()) {
            // Already unconditionally required — the stricter constraint wins.
            return;
        }
        if (existing != null) {
            // Multiple triggers for the same field: OR-merge them.
            if (existing.dependsOn().contains(triggerPath)) {
                return;
            }
            List<String> merged = new ArrayList<>(existing.dependsOn());
            merged.add(triggerPath);
            requiredFields.put(fieldPath, new FieldDescriptor(
                existing.type(), existing.nullable(), existing.enumValues(), merged));
            return;
        }
        Schema<?> propertySchema = properties.get(fieldName);
        FieldDescriptor base = typeResolver.resolve(propertySchema);
        requiredFields.put(fieldPath, new FieldDescriptor(
            base.type(), base.nullable(), base.enumValues(), List.of(triggerPath)));
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