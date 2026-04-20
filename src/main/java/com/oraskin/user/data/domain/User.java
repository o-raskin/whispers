package com.oraskin.user.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record User(
        @JsonIgnore String userId,
        String username,
        String firstName,
        String lastName,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime lastPingTime
) {
}
