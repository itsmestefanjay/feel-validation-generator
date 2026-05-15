package com.consid.automation.camunda.model;

public record FeelNull() implements FeelLiteral {

    @Override
    public String render() {
        return "null";
    }
}
