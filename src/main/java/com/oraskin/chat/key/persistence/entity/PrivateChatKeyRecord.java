package com.oraskin.chat.key.persistence.entity;

import com.oraskin.chat.key.value.PrivateChatKeyStatus;

public record PrivateChatKeyRecord(
        String userId,
        String keyId,
        String publicKey,
        String algorithm,
        String format,
        PrivateChatKeyStatus status,
        String createdAt,
        String updatedAt
) {
}
