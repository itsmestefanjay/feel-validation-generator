package com.consid.automation.camunda;

import java.util.List;
import java.util.Objects;

/**
 * Resolved description of a single OpenAPI field. Carries the base type plus the
 * orthogonal axes the FEEL generator needs to emit a complete rule: whether the
 * field is nullable and any enum the value must belong to.
 */
public record FieldDescriptor(FieldType type, boolean nullable, List<Object> enumValues) {

    public FieldDescriptor {
        Objects.requireNonNull(type, "type");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    public static FieldDescriptor of(FieldType type) {
        return new FieldDescriptor(type, false, List.of());
    }

    public boolean hasEnum() {
        return !enumValues.isEmpty();
    }
}
