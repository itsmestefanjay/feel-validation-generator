package com.consid.automation.camunda.model;

/**
 * JSON Schema length and regex constraints for a string-typed field.
 *
 * <p>Mirrors {@link ArrayConstraints}: {@code required} on a string only
 * mandates key presence, not non-emptiness. The generator emits length and
 * pattern checks only when the schema declares them. {@code minLength == 0}
 * is preserved as the explicit "may be empty" signal and the builder skips
 * the redundant lower-bound check.
 *
 * <p>{@code pattern} is the raw ECMA-262 regex from the spec; the builder is
 * responsible for FEEL string-literal escaping when rendering.
 */
public record StringConstraints(Integer minLength, Integer maxLength, String pattern) {

    public static final StringConstraints NONE = new StringConstraints(null, null, null);

    public boolean hasMinLength() {
        return minLength != null && minLength > 0;
    }

    public boolean hasMaxLength() {
        return maxLength != null;
    }

    public boolean hasPattern() {
        return pattern != null && !pattern.isEmpty();
    }
}
