package com.consid.automation.camunda;

import java.util.List;
import java.util.Objects;

/**
 * Resolved description of a single OpenAPI field. Carries the base type plus the
 * orthogonal axes the FEEL generator needs to emit a complete rule: nullability,
 * the enum set the value must belong to, conditional-required triggers, and any
 * size / shape constraints attached to the property.
 *
 * <p>{@code dependsOn} entries describe the triggers that make this field
 * required. The field is required when at least one trigger fires; an empty
 * list means unconditionally required.
 *
 * <p>{@code arrayConstraints} / {@code stringConstraints} default to their NONE
 * sentinels and are only consulted by the expression builder when {@code type}
 * is the matching family.
 */
public record FieldDescriptor(FieldType type,
                              boolean nullable,
                              List<Object> enumValues,
                              List<Trigger> dependsOn,
                              ArrayConstraints arrayConstraints,
                              StringConstraints stringConstraints) {

    public FieldDescriptor {
        Objects.requireNonNull(type, "type");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        arrayConstraints = arrayConstraints == null ? ArrayConstraints.NONE : arrayConstraints;
        stringConstraints = stringConstraints == null ? StringConstraints.NONE : stringConstraints;
    }

    public FieldDescriptor(FieldType type, boolean nullable, List<Object> enumValues) {
        this(type, nullable, enumValues, List.of(), ArrayConstraints.NONE, StringConstraints.NONE);
    }

    public FieldDescriptor(FieldType type, boolean nullable, List<Object> enumValues, List<Trigger> dependsOn) {
        this(type, nullable, enumValues, dependsOn, ArrayConstraints.NONE, StringConstraints.NONE);
    }

    public static FieldDescriptor of(FieldType type) {
        return new FieldDescriptor(type, false, List.of(), List.of(),
            ArrayConstraints.NONE, StringConstraints.NONE);
    }

    public boolean hasEnum() {
        return !enumValues.isEmpty();
    }

    public boolean isConditional() {
        return !dependsOn.isEmpty();
    }
}
