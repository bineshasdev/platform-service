package com.flowbinary.platformservice.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.TreeMap;

/**
 * Plain-Jackson conversion for stored menu state. Hibernate's jsonb mapping
 * runs through an ObjectMapper that has the Scala module registered (it comes
 * in with the Kafka/Confluent dependencies) and hands back
 * {@code scala.collection.immutable.HashMap} for a {@code Map} field, so
 * snapshot state is stored as a {@link JsonNode} and converted here.
 */
public final class MenuJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MenuJson() {
    }

    public static JsonNode toNode(Map<String, Object> state) {
        return MAPPER.valueToTree(state);
    }

    public static Map<String, Object> toMap(JsonNode node) {
        return MAPPER.convertValue(node, new TypeReference<TreeMap<String, Object>>() { });
    }
}
