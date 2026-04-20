package com.oraskin.chat.key.value;

public record RegisterPrivateChatKeyRequest(
        String keyId,
        String publicKey,
        String algorithm,
        String format
) {
}
