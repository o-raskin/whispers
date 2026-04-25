package com.oraskin.common.mvc;

import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.user.session.ClientSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerResultWriterTest {

    @Test
    void writeHttpSerializesJsonResponses() throws Exception {
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173")));
        ControllerResultWriter writer = new ControllerResultWriter(httpResponseWriter);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.writeHttp(output, ControllerResult.withHeaders(HttpStatus.CREATED, Map.of("status", "created"), Map.of("X-Test", List.of("1"))));

        String response = output.toString(StandardCharsets.UTF_8);
        assertThat(response).startsWith("HTTP/1.1 201 Created");
        assertThat(response).contains("X-Test: 1");
        assertThat(response).contains("{\"status\":\"created\"}");
    }

    @Test
    void writeWebSocketWritesTextFramesAndSkipsEmptyBodies() throws Exception {
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173")));
        ControllerResultWriter writer = new ControllerResultWriter(httpResponseWriter);
        ByteArrayOutputStream webSocketOutput = new ByteArrayOutputStream();
        ClientSession session = new ClientSession("alice-id", new Socket(), webSocketOutput);

        writer.writeWebSocket(session, ControllerResult.body("CONNECTED:alice"));
        assertThat(firstTextFrame(webSocketOutput)).isEqualTo("CONNECTED:alice");

        webSocketOutput.reset();
        writer.writeWebSocket(session, ControllerResult.empty());
        assertThat(webSocketOutput.size()).isZero();
    }

    private String firstTextFrame(ByteArrayOutputStream output) {
        byte[] frame = output.toByteArray();
        int payloadLength = frame[1] & 0x7F;
        return new String(frame, 2, payloadLength, StandardCharsets.UTF_8);
    }
}
