package com.oraskin.common.http;

import com.oraskin.chat.service.ChatException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TransportErrorMapperTest {

    @Test
    void mapsExpectedAndUnexpectedFailuresToClientSafeResponses() {
        ChatException chatException = new ChatException(HttpStatus.CONFLICT, "already connected");

        assertThat(TransportErrorMapper.httpStatus(chatException)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(TransportErrorMapper.clientMessage(chatException)).isEqualTo("already connected");
        assertThat(TransportErrorMapper.httpStatus(new IOException("bad request"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TransportErrorMapper.clientMessage(new IOException("bad request"))).isEqualTo("bad request");
        assertThat(TransportErrorMapper.httpStatus(new IllegalStateException("secret"))).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(TransportErrorMapper.clientMessage(new IllegalStateException("secret"))).isEqualTo("Internal server error");
    }
}
