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
        collectRequiredFields(schema, requiredFields, "", activeStack, List.of());
        return requiredFields;
    }

    /**
     * Recursively collects required fields from a schema.
     * {@code activeStack} tracks schemas currently on the recursion path so that
     * a self-referential schema terminates while a component reused at multiple
     * field paths is still expanded each time.
     * {@code inheritedTriggers} carries the conditional triggers of an ancestor
     * down into a nested object's required fields, so they inherit the parent's
     * conditional requirement.
     */
    private void collectRequiredFields(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                       String pathPrefix, Set<Schema<?>> activeStack,
                                       List<Trigger> inheritedTriggers) {
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
            processDirectRequiredFields(schema, requiredFields, pathPrefix, inheritedTriggers);
            processDependentRequired(schema, requiredFields, pathPrefix, inheritedTriggers);
            processConditional(schema, requiredFields, pathPrefix, inheritedTriggers);
            processComposition(schema.getAllOf(), requiredFields, pathPrefix, activeStack, inheritedTriggers);
            processComposition(schema.getOneOf(), requiredFields, pathPrefix, activeStack, inheritedTriggers);
            processComposition(schema.getAnyOf(), requiredFields, pathPrefix, activeStack, inheritedTriggers);
            processNestedProperties(schema, requiredFields, pathPrefix, activeStack);
        } finally {
            activeStack.remove(schema);
        }
    }

    private void processDirectRequiredFields(Schema<?> schema, Map<String, FieldDescriptor> requiredFields,
                                             String pathPrefix, List<Trigger> inheritedTriggers) {
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
                FieldDescriptor base = typeResolver.resolve(propertySchema);
                FieldDescriptor descriptor = inheritedTriggers.isEmpty()
                    ? base
                    : new FieldDescriptor(base.type(), base.nullable(), base.enumValues(), inheritedTriggers);
                requiredFields.put(fullFieldPath, descriptor);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void processDependentRequired(Schema<?> schema,
                                          Map<String, FieldDescriptor> requiredFields,
                                          String pathPrefix,
                                          List<Trigger> inheritedTriggers) {
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
            Trigger presence = Trigger.presence(buildFieldPath(pathPrefix, trigger));
            List<String> dependents = new ArrayList<>(dependentRequired.get(trigger));
            Collections.sort(dependents);
            for (String dependent : dependents) {
                addConditional(properties, dependent, presence, pathPrefix, requiredFields, inheritedTriggers);
            }
        }
    }

    /**
     * Reads the supported subset of JSON Schema {@code if}/{@code then}:
     * a single-property predicate with {@code const} or {@code enum} plus
     * {@code required: [<that property>]}, and a {@code then} block listing
     * required field names. Shapes outside this subset are silently ignored —
     * the OpenAPI spec keeps its full semantics for tooling that supports more.
     */
    @SuppressWarnings("rawtypes")
    private void processConditional(Schema<?> schema,
                                    Map<String, FieldDescriptor> requiredFields,
                                    String pathPrefix,
                                    List<Trigger> inheritedTriggers) {
        Schema<?> ifSchema = schema.getIf();
        Schema<?> thenSchema = schema.getThen();
        if (ifSchema == null || thenSchema == null) {
            return;
        }
        Trigger trigger = extractValueTrigger(ifSchema, pathPrefix);
        if (trigger == null) {
            return;
        }
        List<String> thenRequired = thenSchema.getRequired();
        if (thenRequired == null || thenRequired.isEmpty()) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        List<String> dependents = new ArrayList<>(thenRequired);
        Collections.sort(dependents);
        for (String dependent : dependents) {
            addConditional(properties, dependent, trigger, pathPrefix, requiredFields, inheritedTriggers);
        }
    }

    /**
     * Pulls the value trigger out of a supported {@code if} subschema, or
     * returns null if the shape isn't one we handle.
     */
    @SuppressWarnings("rawtypes")
    private Trigger extractValueTrigger(Schema<?> ifSchema, String pathPrefix) {
        Map<String, Schema> ifProperties = ifSchema.getProperties();
        List<String> ifRequired = ifSchema.getRequired();
        if (ifProperties == null || ifProperties.size() != 1
            || ifRequired == null || ifRequired.size() != 1) {
            return null;
        }
        String triggerProperty = ifProperties.keySet().iterator().next();
        if (!triggerProperty.equals(ifRequired.get(0))) {
            return null;
        }
        Schema<?> predicate = ifProperties.get(triggerProperty);
        List<Object> allowedValues = literalValues(predicate);
        if (allowedValues.isEmpty()) {
            return null;
        }
        return Trigger.value(buildFieldPath(pathPrefix, triggerProperty), allowedValues);
    }

    private List<Object> literalValues(Schema<?> predicate) {
        if (predicate == null) {
            return List.of();
        }
        if (predicate.getConst() != null) {
            return List.of(predicate.getConst());
        }
        if (predicate.getEnum() != null && !predicate.getEnum().isEmpty()) {
            return List.copyOf(predicate.getEnum());
        }
        return List.of();
    }

    @SuppressWarnings("rawtypes")
    private void addConditional(Map<String, Schema> properties,
                                String fieldName,
                                Trigger trigger,
                                String pathPrefix,
                                Map<String, FieldDescriptor> requiredFields,
                                List<Trigger> inheritedTriggers) {
        String fieldPath = buildFieldPath(pathPrefix, fieldName);
        FieldDescriptor existing = requiredFields.get(fieldPath);
        if (existing != null && !existing.isConditional()) {
            // Already unconditionally required — the stricter constraint wins.
            return;
        }
        if (existing != null) {
            // Multiple triggers for the same field: OR-merge them.
            if (existing.dependsOn().contains(trigger)) {
                return;
            }
            List<Trigger> merged = new ArrayList<>(existing.dependsOn());
            merged.add(trigger);
            requiredFields.put(fieldPath, new FieldDescriptor(
                existing.type(), existing.nullable(), existing.enumValues(), merged));
            return;
        }
        Schema<?> propertySchema = properties.get(fieldName);
        FieldDescriptor base = typeResolver.resolve(propertySchema);
        List<Trigger> dependsOn = new ArrayList<>(inheritedTriggers);
        dependsOn.add(trigger);
        requiredFields.put(fieldPath, new FieldDescriptor(
            base.type(), base.nullable(), base.enumValues(), dependsOn));
    }

    private void processComposition(List<?> schemas, Map<String, FieldDescriptor> requiredFields,
                                    String pathPrefix, Set<Schema<?>> activeStack,
                                    List<Trigger> inheritedTriggers) {
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        for (Object element : schemas) {
            if (element instanceof Schema<?> composedSchema) {
                collectRequiredFields(composedSchema, requiredFields, pathPrefix, activeStack, inheritedTriggers);
            }
        }
    }

    /**
     * For each OBJECT-typed property at this level, decide whether to recurse into its schema:
     * <ul>
     *   <li>Not in {@code requiredFields} → skip (parent is plain-optional, inner required fields are dropped).</li>
     *   <li>Unconditionally required → recurse, inner fields stay unconditional.</li>
     *   <li>Conditionally required → recurse, inner fields inherit the parent's triggers.</li>
     * </ul>
     */
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
            if (descriptor.type() != FieldType.OBJECT) {
                continue;
            }

            FieldDescriptor parent = requiredFields.get(newPath);
            if (parent == null) {
                continue;
            }

            List<Trigger> downstream = parent.isConditional() ? parent.dependsOn() : List.of();
            collectRequiredFields(propSchema, requiredFields, newPath, activeStack, downstream);
        }
    }

    private String buildFieldPath(String pathPrefix, String fieldName) {
        return pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
    }
}
