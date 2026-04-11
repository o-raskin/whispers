package com.oraskin.user.session;

import com.oraskin.common.websocket.FrameType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.oraskin.common.websocket.HeaderUtil.buildHeader;

public final class ClientSession {

    private final String userId;
    private final Socket socket;
    private final OutputStream output;
    private final Object sendLock;

    public ClientSession(String userId, Socket socket, OutputStream output) {
        this.userId = userId;
        this.socket = socket;
        this.output = output;
        this.sendLock = new Object();
    }

    public String userId() {
        return userId;
    }

    public void sendPayload(String message) throws IOException {
        Objects.requireNonNull(message);
        sendFrame(FrameType.TEXT, message.getBytes(StandardCharsets.UTF_8));
    }

    public void close() throws IOException {
        sendFrame(FrameType.CLOSE, new byte[0]);
        socket.close();
    }

    public void sendControlFrame(FrameType frameType, byte[] payload) throws IOException {
        Objects.requireNonNull(frameType);
        if (!frameType.isControl()) {
            throw new IllegalArgumentException("Expected control frame type, but got: " + frameType);
        }
        sendFrame(frameType, payload);
    }

    public void sendFrame(FrameType frameType, byte[] payload) throws IOException {
        Objects.requireNonNull(frameType);
        Objects.requireNonNull(payload);

        byte[] header = buildHeader(frameType, payload.length);

        synchronized (sendLock) {
            output.write(header);
            output.write(payload);
            output.flush();
        }
    }
}
