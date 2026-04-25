package com.oraskin.common.mvc;

import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.http.QueryParams;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.RequestBody;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.user.session.ClientSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonMvcTest {

    @Test
    void routeMatcherMatchesVariablesAndRejectsMismatches() {
        assertEquals(Map.of("chatId", "42"), RouteMatcher.match("/private-chats/{chatId}", "/private-chats/42"));
        assertEquals(Map.of(), RouteMatcher.match("/users", "/users/"));
        assertNull(RouteMatcher.match("/users/{userId}", "/messages/1"));
    }

    @Test
    void routeMatcherHandlesRootAndMultipleVariables() {
        assertEquals(Map.of(), RouteMatcher.match("/", "/"));
        assertEquals(
                Map.of("userId", "alice", "chatId", "42"),
                RouteMatcher.match("/users/{userId}/chats/{chatId}", "/users/alice/chats/42")
        );
    }

    @Test
    void controllerMethodInvokerBindsSupportedArgumentsAndWrapsReturnValues() throws Exception {
        ControllerMethodInvoker controllerMethodInvoker = new ControllerMethodInvoker();
        TestController controller = new TestController();
        Socket socket = new Socket();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpRequest request = new HttpRequest(
                "POST",
                "/private-chats/42",
                QueryParams.fromTarget("/private-chats/42?count=7"),
                Map.of(),
                "{\"text\":\"hello\"}",
                new AuthenticatedUser("alice-id", "alice@example.com", null, null, null, null, null, null, null, "hash")
        );
        ControllerMethod controllerMethod = new ControllerMethod(
                controller,
                TestController.class.getDeclaredMethod(
                        "invoke",
                        HttpRequest.class,
                        QueryParams.class,
                        AuthenticatedUser.class,
                        long.class,
                        long.class,
                        Body.class,
                        Socket.class,
                        OutputStream.class
                ),
                "POST",
                "/private-chats/{chatId}",
                Map.of("chatId", "42"),
                false
        );

        ControllerResult result = controllerMethodInvoker.invoke(controllerMethod, request, null, request.body(), socket, output);

        InvocationResult body = (InvocationResult) result.body();
        assertEquals(HttpStatus.OK, result.status());
        assertEquals("/private-chats/42", body.path());
        assertEquals("alice-id", body.userId());
        assertEquals(7L, body.queryValue());
        assertEquals(42L, body.pathValue());
        assertEquals("hello", body.bodyText());
        assertEquals(socket, body.socket());
        assertEquals(output, body.output());
    }

    @Test
    void controllerMethodInvokerUnwrapsControllerExceptions() throws Exception {
        ControllerMethodInvoker controllerMethodInvoker = new ControllerMethodInvoker();
        FailingController controller = new FailingController();
        ControllerMethod controllerMethod = new ControllerMethod(
                controller,
                FailingController.class.getDeclaredMethod("fail"),
                "GET",
                "/fail",
                Map.of(),
                false
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> controllerMethodInvoker.invoke(
                        controllerMethod,
                        new HttpRequest("GET", "/fail", QueryParams.fromTarget("/fail"), Map.of(), null, null),
                        null,
                        null,
                        new Socket(),
                        new ByteArrayOutputStream()
                )
        );

        assertEquals("boom", exception.getMessage());
    }

    @Test
    void controllerMethodInvokerRejectsUnsupportedSimpleParameterTypes() throws Exception {
        ControllerMethodInvoker controllerMethodInvoker = new ControllerMethodInvoker();
        UnsupportedSimpleController controller = new UnsupportedSimpleController();
        ControllerMethod controllerMethod = new ControllerMethod(
                controller,
                UnsupportedSimpleController.class.getDeclaredMethod("query", int.class),
                "GET",
                "/query",
                Map.of(),
                false
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> controllerMethodInvoker.invoke(
                        controllerMethod,
                        new HttpRequest("GET", "/query", QueryParams.fromTarget("/query?count=7"), Map.of(), null, null),
                        null,
                        null,
                        new Socket(),
                        new ByteArrayOutputStream()
                )
        );

        assertEquals("Unsupported simple parameter type: int", exception.getMessage());
    }

    @Test
    void controllerResultWriterWritesHttpAndWebSocketPayloads() throws Exception {
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173")));
        ControllerResultWriter controllerResultWriter = new ControllerResultWriter(httpResponseWriter);
        ByteArrayOutputStream httpOutput = new ByteArrayOutputStream();

        controllerResultWriter.writeHttp(
                httpOutput,
                ControllerResult.withHeaders(HttpStatus.CREATED, Map.of("status", "created"), Map.of("X-Test", List.of("1")))
        );

        String httpResponse = httpOutput.toString(StandardCharsets.UTF_8);
        assertEquals(true, httpResponse.startsWith("HTTP/1.1 201 Created"));
        assertEquals(true, httpResponse.contains("X-Test: 1"));
        assertEquals(true, httpResponse.contains("{\"status\":\"created\"}"));

        ByteArrayOutputStream webSocketOutput = new ByteArrayOutputStream();
        ClientSession session = new ClientSession("alice-id", new Socket(), webSocketOutput);
        controllerResultWriter.writeWebSocket(session, ControllerResult.body("CONNECTED:alice"));
        assertEquals("CONNECTED:alice", firstTextFrame(webSocketOutput));
    }

    @Test
    void controllerResultWriterSkipsWebSocketFramesForEmptyResults() throws Exception {
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173")));
        ControllerResultWriter controllerResultWriter = new ControllerResultWriter(httpResponseWriter);
        ByteArrayOutputStream webSocketOutput = new ByteArrayOutputStream();
        ClientSession session = new ClientSession("alice-id", new Socket(), webSocketOutput);

        controllerResultWriter.writeWebSocket(session, ControllerResult.empty());

        assertTrue(webSocketOutput.size() == 0);
    }

    private String firstTextFrame(ByteArrayOutputStream output) {
        byte[] frame = output.toByteArray();
        int payloadLength = frame[1] & 0x7F;
        return new String(frame, 2, payloadLength, StandardCharsets.UTF_8);
    }

    private static final class TestController {

        public InvocationResult invoke(
                HttpRequest request,
                QueryParams queryParams,
                AuthenticatedUser authenticatedUser,
                @RequestParam("count") long count,
                @PathVariable("chatId") long chatId,
                @RequestBody Body body,
                Socket socket,
                OutputStream output
        ) {
            return new InvocationResult(request.path(), authenticatedUser.userId(), count, chatId, body.text(), socket, output);
        }
    }

    private static final class FailingController {

        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    private static final class UnsupportedSimpleController {

        public String query(@RequestParam("count") int count) {
            return String.valueOf(count);
        }
    }

    private record Body(String text) {
    }

    private record InvocationResult(
            String path,
            String userId,
            long queryValue,
            long pathValue,
            String bodyText,
            Socket socket,
            OutputStream output
    ) {
    }
}
