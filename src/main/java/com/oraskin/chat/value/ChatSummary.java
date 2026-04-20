package com.oraskin.chat.value;

public record ChatSummary(
        long chatId,
        String username,
        String type
) {
}
