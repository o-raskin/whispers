package com.oraskin.chat.key.service;

import com.oraskin.chat.key.persistence.PrivateChatKeyStore;
import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;
import com.oraskin.chat.key.value.PrivateChatKeyView;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;

import java.util.Objects;

public final class PublicKeyService {

    private final PrivateChatKeyStore privateChatKeyStore;

    public PublicKeyService(PrivateChatKeyStore privateChatKeyStore) {
        this.privateChatKeyStore = Objects.requireNonNull(privateChatKeyStore);
    }

    public PrivateChatKeyView registerCurrentKey(String userId, RegisterPrivateChatKeyRequest request) {
        if (request == null
                || isBlank(request.keyId())
                || isBlank(request.publicKey())
                || isBlank(request.algorithm())
                || isBlank(request.format())) {
            throw new ChatException(
                    HttpStatus.BAD_REQUEST,
                    "Use {\"keyId\":\"browser-key-1\",\"publicKey\":\"...\",\"algorithm\":\"RSA-OAEP\",\"format\":\"spki\"}"
            );
        }

        PrivateChatKeyRecord storedKey = privateChatKeyStore.upsertKey(
                userId,
                request.keyId().trim(),
                request.publicKey().trim(),
                request.algorithm().trim(),
                request.format().trim()
        );
        return toView(storedKey);
    }

    public PrivateChatKeyView findKey(String userId, String keyId) {
        if (isBlank(keyId)) {
            return null;
        }
        return toView(privateChatKeyStore.findKey(userId, keyId.trim()));
    }

    public PrivateChatKeyView requireKey(String userId, String keyId, String missingKeyMessage) {
        PrivateChatKeyView key = findKey(userId, keyId);
        if (key == null) {
            throw new ChatException(HttpStatus.CONFLICT, missingKeyMessage);
        }
        return key;
    }

    public PrivateChatKeyView findLatestKey(String userId) {
        return toView(privateChatKeyStore.findLatestKey(userId));
    }

    public PrivateChatKeyView requireLatestKey(String userId, String missingKeyMessage) {
        PrivateChatKeyView key = findLatestKey(userId);
        if (key == null) {
            throw new ChatException(HttpStatus.CONFLICT, missingKeyMessage);
        }
        return key;
    }

    private PrivateChatKeyView toView(PrivateChatKeyRecord record) {
        if (record == null) {
            return null;
        }
        return new PrivateChatKeyView(
                record.keyId(),
                record.publicKey(),
                record.algorithm(),
                record.format(),
                record.status(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
