package com.consid.automation.camunda;

import java.util.List;
import java.util.Objects;

/**
 * Resolved description of a single OpenAPI field. Carries the base type plus the
 * orthogonal axes the FEEL generator needs to emit a complete rule: nullability,
 * the enum set the value must belong to, and the trigger fields that make the
 * field conditionally required.
 *
 * <p>{@code dependsOn} entries describe the triggers that make this field
 * required. The field is required when at least one trigger fires; an empty
 * list means unconditionally required.
 */
public record FieldDescriptor(FieldType type,
                              boolean nullable,
                              List<Object> enumValues,
                              List<Trigger> dependsOn) {

    public FieldDescriptor {
        Objects.requireNonNull(type, "type");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public FieldDescriptor(FieldType type, boolean nullable, List<Object> enumValues) {
        this(type, nullable, enumValues, List.of());
    }

    public static FieldDescriptor of(FieldType type) {
        return new FieldDescriptor(type, false, List.of(), List.of());
    }

    public boolean hasEnum() {
        return !enumValues.isEmpty();
    }

    public boolean isConditional() {
        return !dependsOn.isEmpty();
    }
}
