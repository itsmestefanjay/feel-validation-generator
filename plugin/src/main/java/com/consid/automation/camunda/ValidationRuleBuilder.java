package com.consid.automation.camunda;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over how validation rules are generated and rendered for output.
 * Keeps the main generator decoupled from FEEL-specific concerns.
 */
public interface ValidationRuleBuilder {

    /**
     * Create a validation rule for the given field path/type combination.
     */
    ValidationRule createRule(String fieldPath, FieldType fieldType);

    /**
     * Render the grouped validation rules into the final FEEL output.
     */
    String render(Map<String, List<ValidationRule>> rulesByEndpoint);
}
