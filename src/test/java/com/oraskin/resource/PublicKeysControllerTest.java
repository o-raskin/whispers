package com.oraskin.resource;

import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.PrivateChatKeyView;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import org.junit.jupiter.api.Test;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static org.assertj.core.api.Assertions.assertThat;

class PublicKeysControllerTest {

    @Test
    void registerCurrentKeyPostDelegatesToPublicKeyService() {
        PublicKeyService publicKeyService = new PublicKeyService(new InMemoryPrivateChatKeyStore());
        PublicKeysController controller = new PublicKeysController(publicKeyService);

        PrivateChatKeyView keyView = controller.registerCurrentKeyPost(
                authenticatedUser("alice-id", "alice@example.com", "hash"),
                new RegisterPrivateChatKeyRequest("alice-key", "alice-spki", "RSA-OAEP", "spki")
        );

        assertThat(keyView.keyId()).isEqualTo("alice-key");
        assertThat(keyView.publicKey()).isEqualTo("alice-spki");
    }
}
