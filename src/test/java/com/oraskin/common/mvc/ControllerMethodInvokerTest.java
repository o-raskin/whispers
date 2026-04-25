package com.oraskin.common.mvc;

import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.http.QueryParams;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.RequestBody;
import com.oraskin.common.mvc.annotation.RequestParam;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControllerMethodInvokerTest {

    @Test
    void invokeBindsSupportedArgumentsAndWrapsReturnValues() throws Exception {
        ControllerMethodInvoker invoker = new ControllerMethodInvoker();
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

        ControllerResult result = invoker.invoke(controllerMethod, request, null, request.body(), socket, output);

        InvocationResult body = (InvocationResult) result.body();
        assertThat(result.status()).isEqualTo(HttpStatus.OK);
        assertThat(body.path()).isEqualTo("/private-chats/42");
        assertThat(body.userId()).isEqualTo("alice-id");
        assertThat(body.queryValue()).isEqualTo(7L);
        assertThat(body.pathValue()).isEqualTo(42L);
        assertThat(body.bodyText()).isEqualTo("hello");
        assertThat(body.socket()).isEqualTo(socket);
        assertThat(body.output()).isEqualTo(output);
    }

    @Test
    void invokeRejectsUnsupportedSimpleParameterTypes() throws Exception {
        ControllerMethodInvoker invoker = new ControllerMethodInvoker();
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
                () -> invoker.invoke(
                        controllerMethod,
                        new HttpRequest("GET", "/query", QueryParams.fromTarget("/query?count=7"), Map.of(), null, null),
                        null,
                        null,
                        new Socket(),
                        new ByteArrayOutputStream()
                )
        );

        assertThat(exception).hasMessage("Unsupported simple parameter type: int");
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
