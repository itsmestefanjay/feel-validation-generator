package com.consid.bpm.camunda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.feel.FeelEngine;
import org.junit.jupiter.api.io.TempDir;
import scala.jdk.javaapi.CollectionConverters;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provides shared utilities for integration-style FEEL generator tests.
 * Contains only generic helpers for reading resources, parsing FEEL output,
 * and converting FEEL evaluation results into Java collections.
 */
public abstract class AbstractFEELValidationGeneratorIntegrationTest {

    @TempDir
    protected Path tempDir;

    protected static final FeelEngine FEEL_ENGINE = new FeelEngine(
        FeelEngine.defaultFunctionProvider(),
        FeelEngine.defaultValueMapper(),
        FeelEngine.defaultConfiguration(),
        FeelEngine.defaultClock()
    );

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected Path resolveResourcePath(String resourceName) {
        URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
        assertThat(resourceUrl)
            .as(resourceName + " should exist in test resources")
            .isNotNull();
        try {
            return Path.of(resourceUrl.toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve resource path for " + resourceName, e);
        }
    }

    protected String readResourceFile(String resourceName) throws IOException {
        URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
        assertThat(resourceUrl)
            .as(resourceName + " should exist in test resources")
            .isNotNull();

        try (InputStream inputStream = resourceUrl.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    protected Map<String, Object> loadJsonResource(String resourceName) throws IOException {
        String json = readResourceFile(resourceName);
        return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    protected List<String> extractFeelExpressions(String output) {
        List<String> expressions = new ArrayList<>();
        int index = 0;
        while (index < output.length()) {
            int headerStart = output.indexOf("# ", index);
            if (headerStart == -1) {
                break;
            }
            int blockStart = output.indexOf('\n', headerStart);
            if (blockStart == -1) {
                break;
            }
            blockStart += 1;
            int nextHeader = output.indexOf("\n# ", blockStart);
            String blockExpression;
            if (nextHeader == -1) {
                blockExpression = output.substring(blockStart).trim();
                expressions.add(blockExpression);
                break;
            } else {
                blockExpression = output.substring(blockStart, nextHeader).trim();
                expressions.add(blockExpression);
                index = nextHeader + 1;
            }
        }
        return expressions;
    }

    protected Map<String, Object> buildEvaluationContext(Map<String, Object> body) {
        assertThat(body)
            .as("Sample request body should exist for scenario")
            .isNotNull();
        Map<String, Object> request = Map.of(
            "body", body,
            "headers", Map.of(),
            "query", Map.of()
        );
        return Map.of(
            "request", request,
            "correlation", Map.of("processInstanceKey", 123456789L)
        );
    }

    protected String extractPath(String headerLine) {
        String[] parts = headerLine.split(" ", 3);
        return parts.length >= 3 ? parts[2].trim() : "";
    }

    protected Map<String, Object> toJavaMap(Object value) {
        if (value instanceof Map<?, ?> javaMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) javaMap;
            return casted;
        }
        if (value instanceof scala.collection.Map<?, ?> scalaMap) {
            return (Map<String, Object>) CollectionConverters.asJava(scalaMap);
        }
        throw new IllegalArgumentException("Unsupported map value: " + value);
    }

    protected List<Object> toJavaList(Object value) {
        if (value instanceof List<?> javaList) {
            @SuppressWarnings("unchecked")
            List<Object> casted = (List<Object>) javaList;
            return casted;
        }
        if (value instanceof scala.collection.Iterable<?> scalaIterable) {
            List<Object> converted = new ArrayList<>();
            CollectionConverters.asJava(scalaIterable).forEach(converted::add);
            return converted;
        }
        throw new IllegalArgumentException("Unsupported list value: " + value);
    }

    protected Map<String, Object> castToMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) map;
            return casted;
        }
        throw new IllegalArgumentException("Value is not a map: " + value);
    }

    protected Object normalizeValue(Object value) {
        if (value instanceof scala.collection.Map<?, ?> scalaMap) {
            return normalizeValue(CollectionConverters.asJava(scalaMap));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, val) -> normalized.put(String.valueOf(key), normalizeValue(val)));
            return normalized;
        }
        if (value instanceof scala.collection.Iterable<?> scalaIterable) {
            List<Object> normalized = new ArrayList<>();
            CollectionConverters.asJava(scalaIterable).forEach(item -> normalized.add(normalizeValue(item)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        return value;
    }
}
