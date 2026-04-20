package com.oraskin.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

public final class JsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private JsonCodec() {
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public static <T> T read(String payload, Class<T> type) throws IOException {
        return OBJECT_MAPPER.readValue(payload, type);
    }

    public static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize JSON payload.", e);
        }
    }
}
