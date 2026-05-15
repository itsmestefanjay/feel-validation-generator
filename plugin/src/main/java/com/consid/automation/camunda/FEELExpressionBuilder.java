package com.consid.automation.camunda;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates FEEL expressions for validating fields.
 * Composition: a field is invalid when it is missing (and required) or present
 * in a form that violates its type / enum / size / pattern constraints. The
 * two halves are kept separate here so nullable fields can substitute the
 * missing-check for an is-present check.
 */
public class FEELExpressionBuilder {

    public String build(String fieldName, FieldDescriptor descriptor) {
        String body = buildBody(fieldName, descriptor);
        if (!descriptor.isConditional()) {
            return body;
        }
        return guardExpression(descriptor.dependsOn()) + " and (" + body + ")";
    }

    private String buildBody(String fieldName, FieldDescriptor descriptor) {
        String violation = buildViolation(fieldName, descriptor);
        if (descriptor.nullable()) {
            return violation == null ? "false" : fieldName + "!=null and (" + violation + ")";
        }
        return violation == null ? fieldName + "=null" : fieldName + "=null or " + violation;
    }

    private String buildViolation(String fieldName, FieldDescriptor descriptor) {
        List<String> parts = new ArrayList<>();
        addIfNotNull(parts, typeViolation(fieldName, descriptor.type()));
        addAll(parts, sizeViolations(fieldName, descriptor));
        if (descriptor.hasEnum()) {
            parts.add("not(" + fieldName + " in (" + renderLiterals(descriptor.enumValues()) + "))");
        }
        return parts.isEmpty() ? null : String.join(" or ", parts);
    }

    private String typeViolation(String fieldName, FieldType type) {
        return switch (type) {
            case STRING -> "not(" + fieldName + " instance of string)";
            case NUMBER -> "not(" + fieldName + " instance of number)";
            case BOOLEAN -> "not(" + fieldName + " instance of boolean)";
            case ARRAY -> "not(" + fieldName + " instance of list)";
            case OBJECT -> "not(" + fieldName + " instance of context)";
            case DATE -> "date(" + fieldName + ")=null";
            case DATE_TIME -> "date and time(" + fieldName + ")=null";
            case TIME -> "time(" + fieldName + ")=null";
            case UNKNOWN -> null;
        };
    }

    /**
     * Emits the optional size / shape checks tied to a field's type family.
     * Returned in the order the violations should appear in the final OR-chain
     * so the rendered FEEL reads min-first, then max, then pattern.
     */
    private List<String> sizeViolations(String fieldName, FieldDescriptor descriptor) {
        return switch (descriptor.type()) {
            case ARRAY -> arrayViolations(fieldName, descriptor.arrayConstraints());
            case STRING, DATE, DATE_TIME, TIME -> stringViolations(fieldName, descriptor.stringConstraints());
            case NUMBER -> numberViolations(fieldName, descriptor.numberConstraints());
            case OBJECT -> objectViolations(fieldName, descriptor.objectConstraints());
            default -> List.of();
        };
    }

    private List<String> objectViolations(String fieldName, ObjectConstraints constraints) {
        if (!constraints.isClosed()) {
            return List.of();
        }
        String list = constraints.allowedKeys().stream()
            .sorted()
            .map(k -> "\"" + escapeLiteral(k) + "\"")
            .collect(Collectors.joining(", "));
        // Outer parens (same reason as `some`): FEEL's `every ... satisfies <expr>`
        // is greedy and would consume tokens past the intended end of the clause.
        // `get entries(ctx).key` projects out the list of keys — Camunda FEEL has
        // no direct `get keys(...)` function. Outer parens defend against the
        // greedy `every ... satisfies <expr>` operator consuming surrounding tokens.
        return List.of("(not(every k in get entries(" + fieldName
            + ").key satisfies (k in (" + list + "))))");
    }

    private List<String> arrayViolations(String fieldName, ArrayConstraints constraints) {
        List<String> parts = new ArrayList<>();
        if (constraints.hasMinItems()) {
            parts.add("count(" + fieldName + ")<" + constraints.minItems());
        }
        if (constraints.hasMaxItems()) {
            parts.add("count(" + fieldName + ")>" + constraints.maxItems());
        }
        if (constraints.hasItems()) {
            // Outer parens around the whole `some ... satisfies ...` clause: FEEL's
            // quantifiedOp body is greedy and would otherwise consume tokens past the
            // intended end, breaking the surrounding OR-chain and the rules array.
            parts.add("(some e in " + fieldName
                + " satisfies (" + elementViolation(constraints.items(), constraints.itemRequiredFields()) + "))");
        }
        return parts;
    }

    /**
     * Builds the per-element violation chain used inside a {@code some e in X
     * satisfies (...)} clause. The element binding is always {@code e}; nested
     * arrays rely on FEEL's lexical scoping to shadow correctly.
     */
    private String elementViolation(FieldDescriptor items, Map<String, FieldDescriptor> itemRequiredFields) {
        List<String> parts = new ArrayList<>();
        parts.add(build("e", items));
        itemRequiredFields.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> parts.add(build("e." + entry.getKey(), entry.getValue())));
        return String.join(" or ", parts);
    }

    private List<String> stringViolations(String fieldName, StringConstraints constraints) {
        List<String> parts = new ArrayList<>();
        if (constraints.hasMinLength()) {
            parts.add("string length(" + fieldName + ")<" + constraints.minLength());
        }
        if (constraints.hasMaxLength()) {
            parts.add("string length(" + fieldName + ")>" + constraints.maxLength());
        }
        if (constraints.hasPattern()) {
            parts.add("not(matches(" + fieldName + ", \"" + escapeLiteral(constraints.pattern()) + "\"))");
        }
        return parts;
    }

    private List<String> numberViolations(String fieldName, NumberConstraints constraints) {
        List<String> parts = new ArrayList<>();
        if (constraints.hasMinimum()) {
            parts.add(fieldName + "<" + renderNumber(constraints.minimum()));
        }
        if (constraints.hasExclusiveMinimum()) {
            parts.add(fieldName + "<=" + renderNumber(constraints.exclusiveMinimum()));
        }
        if (constraints.hasMaximum()) {
            parts.add(fieldName + ">" + renderNumber(constraints.maximum()));
        }
        if (constraints.hasExclusiveMaximum()) {
            parts.add(fieldName + ">=" + renderNumber(constraints.exclusiveMaximum()));
        }
        if (constraints.hasMultipleOf()) {
            parts.add("modulo(" + fieldName + ", " + renderNumber(constraints.multipleOf()) + ")!=0");
        }
        return parts;
    }

    /**
     * Render a BigDecimal as a FEEL number literal. {@link BigDecimal#toString()}
     * can emit scientific notation ({@code 1E+2}) which FEEL would not parse;
     * {@link BigDecimal#toPlainString()} keeps the literal numeric.
     */
    private static String renderNumber(BigDecimal value) {
        return value.toPlainString();
    }

    private String guardExpression(List<Trigger> dependsOn) {
        String parts = dependsOn.stream()
            .map(this::renderTrigger)
            .collect(Collectors.joining(" or "));
        return dependsOn.size() == 1 ? parts : "(" + parts + ")";
    }

    private String renderTrigger(Trigger trigger) {
        if (trigger.isPresenceCheck()) {
            return trigger.path() + "!=null";
        }
        if (trigger.allowedValues().size() == 1) {
            Object value = trigger.allowedValues().get(0);
            // Booleans render as bare path / not(path) since FEEL treats them identically to the
            // explicit =true / =false comparison, including under null inputs.
            if (Boolean.TRUE.equals(value)) {
                return trigger.path();
            }
            if (Boolean.FALSE.equals(value)) {
                return "not(" + trigger.path() + ")";
            }
            return trigger.path() + "=" + renderLiteral(value);
        }
        return trigger.path() + " in (" + renderLiterals(trigger.allowedValues()) + ")";
    }

    private String renderLiterals(List<Object> values) {
        return values.stream()
            .map(FEELExpressionBuilder::renderLiteral)
            .collect(Collectors.joining(", "));
    }

    private static String renderLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "\"" + escapeLiteral(value.toString()) + "\"";
    }

    private static String escapeLiteral(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void addIfNotNull(List<String> parts, String part) {
        if (part != null) {
            parts.add(part);
        }
    }

    private static void addAll(List<String> parts, List<String> additions) {
        if (!additions.isEmpty()) {
            parts.addAll(additions);
        }
    }
}
