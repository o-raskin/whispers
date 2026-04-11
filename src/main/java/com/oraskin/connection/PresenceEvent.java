package com.oraskin.connection;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record PresenceEvent(
        String type,
        String username,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime lastPingTime
) {
}
