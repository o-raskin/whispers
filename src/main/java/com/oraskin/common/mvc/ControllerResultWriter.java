package com.oraskin.common.mvc;

import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.user.session.ClientSession;

import java.io.IOException;
import java.io.OutputStream;

public final class ControllerResultWriter {

    private final HttpResponseWriter httpResponseWriter;

    public ControllerResultWriter(HttpResponseWriter httpResponseWriter) {
        this.httpResponseWriter = httpResponseWriter;
    }

    public void writeHttp(OutputStream output, ControllerResult result) throws IOException {
        httpResponseWriter.writeJson(output, HttpStatus.OK, result.body());
    }

    public void writeWebSocket(ClientSession currentSession, ControllerResult result) throws IOException {
        if (result.body() != null) {
            currentSession.sendPayload(serialize(result.body()));
        }
    }

    private String serialize(Object value) {
        if (value instanceof String text) {
            return text;
        }
        return JsonCodec.write(value);
    }
}
