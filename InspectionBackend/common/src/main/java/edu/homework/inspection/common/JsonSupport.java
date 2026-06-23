package edu.homework.inspection.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class JsonSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize JSON", e);
        }
    }

    public static <T> T fromJson(String value, Class<T> type) {
        try {
            return MAPPER.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse JSON: " + value, e);
        }
    }
}
