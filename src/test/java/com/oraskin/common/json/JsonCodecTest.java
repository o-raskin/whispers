package com.oraskin.common.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonCodecTest {

    @Test
    void writeAndReadRoundTripSimplePayloads() throws Exception {
        String payload = JsonCodec.write(Map.of("type", "presence", "count", 2));

        assertThat(payload).contains("\"type\":\"presence\"");
        assertThat(JsonCodec.read(payload, Map.class)).containsEntry("type", "presence");
    }

    @Test
    void readRejectsInvalidJsonAndWriteWrapsSerializationFailures() {
        assertThatThrownBy(() -> JsonCodec.read("{", Map.class)).isInstanceOf(IOException.class);

        Map<String, Object> recursivePayload = new LinkedHashMap<>();
        recursivePayload.put("self", recursivePayload);

        assertThatThrownBy(() -> JsonCodec.write(recursivePayload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot serialize JSON payload.");
    }
}
