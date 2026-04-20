package com.oraskin.chat.key.service;

import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.key.value.PrivateChatKeyStatus;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicKeyServiceTest {

    @Test
    void registeringPublicKeysKeepsMultipleBrowserKeysAvailable() {
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService service = new PublicKeyService(keyStore);

        service.registerCurrentKey(
                "alice-id",
                new RegisterPrivateChatKeyRequest("browser-key-1", "spki-key-1", "RSA-OAEP", "spki")
        );
        var current = service.registerCurrentKey(
                "alice-id",
                new RegisterPrivateChatKeyRequest("browser-key-2", "spki-key-2", "RSA-OAEP", "spki")
        );

        assertEquals("browser-key-2", current.keyId());
        assertEquals(PrivateChatKeyStatus.ACTIVE, current.status());
        assertNotNull(service.findKey("alice-id", "browser-key-1"));
        assertEquals("browser-key-2", service.findLatestKey("alice-id").keyId());
    }
}
