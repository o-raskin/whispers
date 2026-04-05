package com.oraskin;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.oraskin.FrameType.PONG;

public class ChatServer {

    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final SessionCache sessionCache;

    public ChatServer(int port) {
        this.port = port;
        this.sessionCache = new SessionCache();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server is listening on ws://localhost:" + port + "/connect?userId=<user>");
            while (true) {
                Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(() -> handleConnection(socket));
            }
        }
    }

    private void handleConnection(Socket socket) {
        ClientSession session = null;
        try (socket) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            HttpRequest request = readHttpRequest(input);
            validateHandshake(request);

            String userId = extractUserId(request.target());
            session = new ClientSession(userId, socket, input, output);

            ClientSession existing = sessionCache.createSession(userId, session);
            if (existing != null) {
                writeHttpError(output, 409, "User already connected");
                return;
            }

            writeHandshakeResponse(output, request.header("sec-websocket-key"));
            session.sendPayload("CONNECTED:" + userId);
            System.out.println("Connected user: " + userId);

            while (true) {
                WebSocketFrame frame = readFrame(input);
                if (frame == null) {
                    break;
                }

                switch (frame.frameType()) {
                    case TEXT -> routeMessage(session, new String(frame.payload(), StandardCharsets.UTF_8));
                    case CLOSE -> {
                        session.close();
                        return;
                    }
                    case PING -> session.sendControlFrame(PONG, frame.payload());
                    case PONG -> {
                        // Ignore pong frames.
                    }
                    default -> {
                        session.sendPayload("ERROR: unsupported frame type.");
                        return;
                    }
                }
            }
        } catch (SocketException | EOFException ignored) {
            // Client disconnected.
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            if (session != null) {
                try {
                    session.sendPayload("ERROR: " + e.getMessage());
                } catch (IOException ignored) {
                    // Connection is already broken.
                }
            }
        } finally {
            if (session != null) {
                sessionCache.terminateSession(session.userId());
                System.out.println("Disconnected user: " + session.userId());
            }
        }
    }

    private void routeMessage(ClientSession sender, String message) throws IOException {
        int separator = message.indexOf(':');
        if (separator <= 0) {
            sender.sendPayload("ERROR: use recipientUserId:message");
            return;
        }

        String recipientId = message.substring(0, separator).trim();
        String body = message.substring(separator + 1);
        if (recipientId.isEmpty()) {
            sender.sendPayload("ERROR: recipient userId is required.");
            return;
        }

        ClientSession recipient = sessionCache.findSession(recipientId);
        if (recipient == null) {
            sender.sendPayload("ERROR: user '" + recipientId + "' is not connected.");
            return;
        }

        recipient.sendPayload(sender.userId() + ":" + body);
    }

    private static HttpRequest readHttpRequest(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int next = input.read();
            if (next < 0) {
                throw new EOFException("Connection closed before handshake completed.");
            }
            buffer.write(next);
            matched = switch (matched) {
                case 0 -> next == '\r' ? 1 : 0;
                case 1 -> next == '\n' ? 2 : 0;
                case 2 -> next == '\r' ? 3 : 0;
                case 3 -> next == '\n' ? 4 : 0;
                default -> 0;
            };
        }

        String requestText = buffer.toString(StandardCharsets.ISO_8859_1);
        String[] lines = requestText.split("\r\n");
        if (lines.length == 0) {
            throw new IOException("Empty HTTP request.");
        }

        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 3) {
            throw new IOException("Invalid HTTP request line.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                break;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            headers.put(name, value);
        }

        return new HttpRequest(requestLine[0], requestLine[1], headers);
    }

    private static void validateHandshake(HttpRequest request) throws IOException {
        if (!"GET".equals(request.method())) {
            throw new IOException("WebSocket handshake must use GET.");
        }

        URI uri = URI.create(request.target());
        if (!"/connect".equals(uri.getPath())) {
            throw new IOException("Only /connect is supported.");
        }

        String upgrade = request.header("upgrade");
        if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade)) {
            throw new IOException("Missing WebSocket upgrade header.");
        }

        String connection = request.header("connection");
        if (connection == null || !connection.toLowerCase(Locale.ROOT).contains("upgrade")) {
            throw new IOException("Missing Connection: Upgrade header.");
        }

        if (request.header("sec-websocket-key") == null) {
            throw new IOException("Missing Sec-WebSocket-Key header.");
        }
    }

    private static String extractUserId(String target) throws IOException {
        URI uri = URI.create(target);
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            throw new IOException("Missing userId query parameter.");
        }

        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            if (!Objects.equals(key, "userId")) {
                continue;
            }

            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            String userId = URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (userId.isBlank()) {
                break;
            }
            return userId;
        }

        throw new IOException("Missing userId query parameter.");
    }

    private static void writeHandshakeResponse(OutputStream output, String websocketKey) throws Exception {
        String acceptValue = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((websocketKey + WEBSOCKET_MAGIC).getBytes(StandardCharsets.ISO_8859_1)));

        String response = ""
                + "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptValue + "\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    private static void writeHttpError(OutputStream output, int statusCode, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String response = ""
                + "HTTP/1.1 " + statusCode + " " + message + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private static WebSocketFrame readFrame(InputStream input) throws IOException {
        int firstByte = input.read();
        if (firstByte < 0) {
            return null;
        }

        int secondByte = readRequiredByte(input);
        boolean fin = (firstByte & 0x80) != 0;
        int opcode = firstByte & 0x0F;
        boolean masked = (secondByte & 0x80) != 0;

        if (!fin) {
            throw new IOException("Fragmented frames are not supported.");
        }
        if (!masked) {
            throw new IOException("Client frames must be masked.");
        }

        long payloadLength = secondByte & 0x7F;
        if (payloadLength == 126) {
            payloadLength = ((long) readRequiredByte(input) << 8) | readRequiredByte(input);
        } else if (payloadLength == 127) {
            payloadLength = ByteBuffer.wrap(readExactly(input, 8)).getLong();
        }

        if (payloadLength > Integer.MAX_VALUE) {
            throw new IOException("Frame payload is too large.");
        }

        byte[] mask = readExactly(input, 4);
        byte[] payload = readExactly(input, (int) payloadLength);
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i % 4];
        }

        return new WebSocketFrame(FrameType.fromCode(opcode), payload);
    }

    private static int readRequiredByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of stream.");
        }
        return value;
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of stream.");
            }
            offset += read;
        }
        return buffer;
    }

}
