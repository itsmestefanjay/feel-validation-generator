package com.consid.automation.camunda;

import java.util.Map;

/**
 * JSON Schema sizing + element constraints for an array-typed field.
 *
 * <p>{@code minItems} / {@code maxItems} bound the list size — both optional.
 * Per JSON Schema, {@code required} on an array only mandates the property's
 * presence; non-empty must be requested explicitly with {@code minItems: 1}.
 *
 * <p>{@code items} carries the element's own type-level descriptor (null when
 * the array has no items schema or the items don't resolve). When the items
 * are object-typed, {@code itemRequiredFields} maps element-relative paths to
 * the descriptors needed to validate them; the builder folds these into a
 * {@code some e in X satisfies (...)} clause so a list element with a missing
 * or malformed required child fails the array's rule.
 */
public record ArrayConstraints(Integer minItems,
                               Integer maxItems,
                               FieldDescriptor items,
                               Map<String, FieldDescriptor> itemRequiredFields) {

    public static final ArrayConstraints NONE = new ArrayConstraints(null, null, null, Map.of());

    public ArrayConstraints {
        itemRequiredFields = itemRequiredFields == null ? Map.of() : Map.copyOf(itemRequiredFields);
    }

    public ArrayConstraints(Integer minItems, Integer maxItems) {
        this(minItems, maxItems, null, Map.of());
    }

    public boolean hasMinItems() {
        return minItems != null && minItems > 0;
    }

    public boolean hasMaxItems() {
        return maxItems != null;
    }

    public boolean hasItems() {
        return items != null;
    }
}
