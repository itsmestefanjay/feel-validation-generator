package com.consid.automation.camunda.model;

import java.math.BigDecimal;

/**
 * JSON Schema range and divisibility constraints for a numeric field.
 *
 * <p>Lower and upper bounds carry both an inclusive form ({@code minimum} /
 * {@code maximum}) and an exclusive form ({@code exclusiveMinimum} /
 * {@code exclusiveMaximum}). At most one of each pair is populated: the
 * resolver normalizes the OpenAPI 3.0 boolean form (where
 * {@code exclusiveMinimum: true} promotes {@code minimum} to exclusive) into
 * the 3.1 numeric form so the builder sees a single representation.
 *
 * <p>{@code multipleOf} is the raw divisor; the builder emits
 * {@code modulo(x, N)!=0}.
 */
public record NumberConstraints(BigDecimal minimum,
                                BigDecimal exclusiveMinimum,
                                BigDecimal maximum,
                                BigDecimal exclusiveMaximum,
                                BigDecimal multipleOf) {

    public static final NumberConstraints NONE = new NumberConstraints(null, null, null, null, null);

    public boolean hasMinimum() {
        return minimum != null;
    }

    public boolean hasExclusiveMinimum() {
        return exclusiveMinimum != null;
    }

    public boolean hasMaximum() {
        return maximum != null;
    }

    public boolean hasExclusiveMaximum() {
        return exclusiveMaximum != null;
    }

    public boolean hasMultipleOf() {
        return multipleOf != null;
    }
}
