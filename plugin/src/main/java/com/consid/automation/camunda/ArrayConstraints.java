package com.consid.automation.camunda;

/**
 * JSON Schema sizing constraints for an array-typed field.
 *
 * <p>Both bounds are optional. Per JSON Schema, {@code required} on an array
 * only mandates the property's presence — it does not imply non-empty. So the
 * generator emits a {@code count(x) &lt; minItems} / {@code count(x) &gt; maxItems}
 * check only when the schema explicitly says so. An unconstrained array stays
 * as just a type check.
 *
 * <p>{@code minItems == 0} is preserved as the explicit "may be empty" signal
 * (distinct from "unset"); the builder treats it as redundant and skips the
 * lower-bound check.
 */
public record ArrayConstraints(Integer minItems, Integer maxItems) {

    public static final ArrayConstraints NONE = new ArrayConstraints(null, null);

    public boolean hasMinItems() {
        return minItems != null && minItems > 0;
    }

    public boolean hasMaxItems() {
        return maxItems != null;
    }
}
