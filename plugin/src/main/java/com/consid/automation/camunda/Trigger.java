package com.consid.automation.camunda;

import java.util.List;
import java.util.Objects;

/**
 * A conditional-requirement trigger. A field is required when at least one
 * of its triggers fires.
 *
 * <ul>
 *   <li>A presence trigger ({@code allowedValues} is empty) fires when the
 *       referenced path is non-null. Used for {@code dependentRequired}.</li>
 *   <li>A value trigger ({@code allowedValues} non-empty) fires when the
 *       referenced path equals one of the listed literals. Used for the
 *       supported subset of JSON Schema {@code if}/{@code then}: single-property
 *       predicates with {@code const} or {@code enum}.</li>
 * </ul>
 *
 * {@code path} is a dot-path from the request body root. The FEEL renderer
 * prepends {@code req.} when emitting the expression.
 */
public record Trigger(String path, List<Object> allowedValues) {

    public Trigger {
        Objects.requireNonNull(path, "path");
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }

    public static Trigger presence(String path) {
        return new Trigger(path, List.of());
    }

    public static Trigger value(String path, List<Object> allowedValues) {
        return new Trigger(path, allowedValues);
    }

    public boolean isPresenceCheck() {
        return allowedValues.isEmpty();
    }
}
