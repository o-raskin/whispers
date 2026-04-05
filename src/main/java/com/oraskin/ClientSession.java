package com.oraskin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class ClientSession {

    private final String userId;
    private final Socket socket;
    private final OutputStream output;
    private final InputStream input;
    private final Object sendLock;

    public ClientSession(String userId, Socket socket, InputStream input, OutputStream output) {
        this.userId = userId;
        this.socket = socket;
        this.output = output;
        this.input = input;
        this.sendLock = new Object();
    }

    public String userId() {
        return userId;
    }

    public void sendText(String message) throws IOException {
        sendFrame(0x1, message.getBytes(StandardCharsets.UTF_8));
    }

    public void sendCloseFrame() throws IOException {
        sendFrame(0x8, new byte[0]);
        socket.close();
    }

    public void sendControlFrame(int opcode, byte[] payload) throws IOException {
        sendFrame(opcode, payload);
    }

    public void sendFrame(int opcode, byte[] payload) throws IOException {
        synchronized (sendLock) {
            output.write(0x80 | (opcode & 0x0F));
            if (payload.length <= 125) {
                output.write(payload.length);
            } else if (payload.length <= 65535) {
                output.write(126);
                output.write((payload.length >>> 8) & 0xFF);
                output.write(payload.length & 0xFF);
            } else {
                output.write(127);
                output.write(ByteBuffer.allocate(Long.BYTES).putLong(payload.length).array());
            }
            output.write(payload);
            output.flush();
        }
    }
}
