package com.oraskin.chat.privatechat.value;

public record EncryptedPrivateMessagePayload(
        String protocolVersion,
        String encryptionAlgorithm,
        String keyWrapAlgorithm,
        String ciphertext,
        String nonce,
        String senderKeyId,
        String senderMessageKeyEnvelope,
        String recipientKeyId,
        String recipientMessageKeyEnvelope
) {
}
