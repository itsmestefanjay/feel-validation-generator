package com.consid.automation.camunda.model;

public record FeelBoolean(boolean value) implements FeelLiteral {

    @Override
    public String render() {
        return Boolean.toString(value);
    }
}
