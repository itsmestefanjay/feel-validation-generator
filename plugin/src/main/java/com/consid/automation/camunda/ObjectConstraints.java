package com.consid.automation.camunda;

import java.util.Set;

/**
 * JSON Schema {@code additionalProperties: false} constraint for an object-typed
 * field. When {@link #allowedKeys()} is null the object is open (the default);
 * when non-null it carries the closed set of property names declared on the
 * schema. The expression builder emits an {@code every k in get keys(X) satisfies k in (…)}
 * check only when the set is closed.
 */
public record ObjectConstraints(Set<String> allowedKeys) {

    public static final ObjectConstraints NONE = new ObjectConstraints(null);

    public ObjectConstraints {
        allowedKeys = allowedKeys == null ? null : Set.copyOf(allowedKeys);
    }

    public boolean isClosed() {
        return allowedKeys != null;
    }
}
