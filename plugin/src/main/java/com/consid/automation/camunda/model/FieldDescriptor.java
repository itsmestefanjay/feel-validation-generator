package com.consid.automation.camunda.model;

import java.util.List;
import java.util.Objects;

/**
 * Resolved description of a single OpenAPI field. Carries the base type plus the
 * orthogonal axes the FEEL generator needs to emit a complete rule: nullability,
 * the enum set the value must belong to, conditional-required triggers, and any
 * size / shape / range constraints attached to the property.
 *
 * <p>{@code dependsOn} entries describe the triggers that make this field
 * required. The field is required when at least one trigger fires; an empty
 * list means unconditionally required.
 *
 * <p>{@code arrayConstraints} / {@code stringConstraints} / {@code numberConstraints} /
 * {@code objectConstraints} default to their NONE sentinels and are only consulted
 * by the expression builder when {@code type} is the matching family.
 */
public record FieldDescriptor(FieldType type,
                              boolean nullable,
                              List<Object> enumValues,
                              List<Trigger> dependsOn,
                              ArrayConstraints arrayConstraints,
                              StringConstraints stringConstraints,
                              NumberConstraints numberConstraints,
                              ObjectConstraints objectConstraints) {

    public FieldDescriptor {
        Objects.requireNonNull(type, "type");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        arrayConstraints = arrayConstraints == null ? ArrayConstraints.NONE : arrayConstraints;
        stringConstraints = stringConstraints == null ? StringConstraints.NONE : stringConstraints;
        numberConstraints = numberConstraints == null ? NumberConstraints.NONE : numberConstraints;
        objectConstraints = objectConstraints == null ? ObjectConstraints.NONE : objectConstraints;
    }

    public FieldDescriptor(FieldType type, boolean nullable, List<Object> enumValues) {
        this(type, nullable, enumValues, List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE, NumberConstraints.NONE, ObjectConstraints.NONE);
    }

    public FieldDescriptor(FieldType type, boolean nullable, List<Object> enumValues, List<Trigger> dependsOn) {
        this(type, nullable, enumValues, dependsOn,
            ArrayConstraints.NONE, StringConstraints.NONE, NumberConstraints.NONE, ObjectConstraints.NONE);
    }

    public FieldDescriptor(FieldType type,
                           boolean nullable,
                           List<Object> enumValues,
                           List<Trigger> dependsOn,
                           ArrayConstraints arrayConstraints,
                           StringConstraints stringConstraints) {
        this(type, nullable, enumValues, dependsOn,
            arrayConstraints, stringConstraints, NumberConstraints.NONE, ObjectConstraints.NONE);
    }

    public FieldDescriptor(FieldType type,
                           boolean nullable,
                           List<Object> enumValues,
                           List<Trigger> dependsOn,
                           ArrayConstraints arrayConstraints,
                           StringConstraints stringConstraints,
                           NumberConstraints numberConstraints) {
        this(type, nullable, enumValues, dependsOn,
            arrayConstraints, stringConstraints, numberConstraints, ObjectConstraints.NONE);
    }

    public static FieldDescriptor of(FieldType type) {
        return new FieldDescriptor(type, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE, NumberConstraints.NONE, ObjectConstraints.NONE);
    }

    public boolean hasEnum() {
        return !enumValues.isEmpty();
    }

    public boolean isConditional() {
        return !dependsOn.isEmpty();
    }
}
