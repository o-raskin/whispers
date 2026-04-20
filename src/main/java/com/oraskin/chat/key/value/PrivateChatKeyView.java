package com.oraskin.chat.key.value;

public record PrivateChatKeyView(
        String keyId,
        String publicKey,
        String algorithm,
        String format,
        PrivateChatKeyStatus status,
        String createdAt,
        String updatedAt
) {
}
