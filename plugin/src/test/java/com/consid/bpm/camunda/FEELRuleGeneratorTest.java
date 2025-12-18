package com.consid.bpm.camunda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.consid.automation.camunda.FieldType;
import com.consid.automation.camunda.ValidationRule;
import com.consid.automation.camunda.FEELRuleGenerator;

class FEELRuleGeneratorTest {

    @Test
    void test_create_rule_does_generate_field_rule_as_expected() {
        FEELRuleGenerator generator = new FEELRuleGenerator(false);

        ValidationRule rule = generator.createRule("user.name", FieldType.STRING);

        assertThat(rule.id()).isEqualTo("user.name-invalid");
        assertThat(rule.invalidExpression())
            .contains("req.user.name=null")
            .contains("instance of string");
        assertThat(rule.fieldPath()).isEqualTo("user.name");
    }

    @Test
    void test_render_basic_format_does_emit_activation_expression_as_expected() {
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(
            "# POST /users",
            List.of(
                ValidationRule.create("user-invalid", "req.user=null", "user"),
                ValidationRule.create("email-invalid", "req.email=null", "email")
            )
        );

        String output = generator.render(rulesByEndpoint);

        assertThat(output)
            .contains("# POST /users")
            .contains("req: request.body")
            .contains("rules:")
            .contains("{id: \"user-invalid\", invalid: req.user=null}")
            .contains("{id: \"email-invalid\", invalid: req.email=null}")
            .endsWith(".isValid");
    }

    @Test
    void test_render_response_format_does_emit_response_payload_as_expected() {
        FEELRuleGenerator generator = new FEELRuleGenerator(true);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put(
            "# POST /users",
            List.of(ValidationRule.create("user-invalid", "req.user=null", "user"))
        );

        String output = generator.render(rulesByEndpoint);

        assertThat(output)
            .contains("# POST /users")
            .contains("field: \"user\"")
            .contains("body:")
            .contains("statusCode:")
            .contains("if isValid then");
    }

    @Test
    void test_render_does_include_multiple_endpoints_as_expected() {
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put("# POST /one", List.of(ValidationRule.create("a", "req.a=null", "a")));
        rulesByEndpoint.put("# PUT /two", List.of(ValidationRule.create("b", "req.b=null", "b")));

        String output = generator.render(rulesByEndpoint);

        assertThat(output)
            .contains("# POST /one")
            .contains("# PUT /two");
    }

    @Test
    void test_render_does_handle_empty_rules_as_expected() {
        FEELRuleGenerator generator = new FEELRuleGenerator(false);
        Map<String, List<ValidationRule>> rulesByEndpoint = new HashMap<>();
        rulesByEndpoint.put("# GET /empty", List.of());

        String output = generator.render(rulesByEndpoint);

        assertThat(output)
            .contains("rules: [")
            .contains("]");
    }
}
