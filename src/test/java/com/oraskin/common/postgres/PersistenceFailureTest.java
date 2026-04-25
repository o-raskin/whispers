package com.oraskin.common.postgres;

import com.oraskin.auth.persistence.PostgresAccessTokenStore;
import com.oraskin.auth.persistence.PostgresAuthIdentityStore;
import com.oraskin.auth.persistence.PostgresRefreshTokenStore;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.chat.key.persistence.DatabasePrivateChatKeyStore;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.repository.DatabaseChatRepository;
import com.oraskin.chat.value.ChatType;
import com.oraskin.user.data.persistence.DatabaseUserStore;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistenceFailureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void postgresInfrastructureAndRepositoriesFailFastWhenTheDatabaseIsUnavailable() {
        PostgresConnectionFactory connectionFactory = unreachableConnectionFactory();
        DatabaseChatRepository chatRepository = new DatabaseChatRepository(connectionFactory, CLOCK);
        DatabasePrivateChatKeyStore privateChatKeyStore = new DatabasePrivateChatKeyStore(connectionFactory, CLOCK);
        DatabaseUserStore userStore = new DatabaseUserStore(connectionFactory, CLOCK);
        PostgresAccessTokenStore accessTokenStore = new PostgresAccessTokenStore(connectionFactory);
        PostgresRefreshTokenStore refreshTokenStore = new PostgresRefreshTokenStore(connectionFactory);
        PostgresAuthIdentityStore authIdentityStore = new PostgresAuthIdentityStore(connectionFactory);

        assertThrows(SQLException.class, connectionFactory::openConnection);
        assertThrows(IllegalStateException.class, new LiquibaseMigrationRunner(connectionFactory)::runMigrations);

        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.createChat("alice-id", null, "bob-id", null, ChatType.DIRECT)),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.findChat(1L)),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.findChatsForUser("alice-id")),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.appendMessage(1L, "alice-id", "hello")),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.findMessage(1L)),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.findMessages(1L)),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.deleteMessage(1L)),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.appendPrivateMessage(
                        1L,
                        "alice-id",
                        new EncryptedPrivateMessagePayload("v1", "AES-GCM", "RSA-OAEP", "ciphertext", "nonce", "alice-key", "sender-envelope", "bob-key", "recipient-envelope")
                )),
                () -> assertThrows(IllegalStateException.class, () -> chatRepository.findPrivateMessages(1L)),
                () -> assertThrows(IllegalStateException.class, () -> privateChatKeyStore.upsertKey("alice-id", "key-1", "spki", "RSA-OAEP", "spki")),
                () -> assertThrows(IllegalStateException.class, () -> privateChatKeyStore.findKey("alice-id", "key-1")),
                () -> assertThrows(IllegalStateException.class, () -> privateChatKeyStore.findLatestKey("alice-id")),
                () -> assertThrows(IllegalStateException.class, () -> userStore.ping("alice-id")),
                () -> assertThrows(IllegalStateException.class, () -> userStore.findUser("alice-id")),
                () -> assertThrows(IllegalStateException.class, () -> userStore.findUsers(List.of("alice-id"))),
                () -> assertThrows(IllegalStateException.class, () -> userStore.findByUsername("alice@example.com")),
                () -> assertThrows(IllegalStateException.class, () -> userStore.createUser("alice@example.com", "Alice", "Example")),
                () -> assertThrows(IllegalStateException.class, () -> userStore.updateUser("alice-id", "alice@example.com", "Alice", "Example")),
                () -> assertThrows(IllegalStateException.class, () -> accessTokenStore.createForUser("token-hash", "alice-id", "google", "subject", Instant.now(CLOCK), Instant.now(CLOCK).plusSeconds(60))),
                () -> assertThrows(IllegalStateException.class, () -> accessTokenStore.findActiveUserByTokenHash("token-hash", Instant.now(CLOCK))),
                () -> assertThrows(IllegalStateException.class, () -> accessTokenStore.revoke("token-hash", Instant.now(CLOCK))),
                () -> assertThrows(IllegalStateException.class, () -> refreshTokenStore.create("session-id", "token-hash", "alice-id", "google", "subject", Instant.now(CLOCK), Instant.now(CLOCK).plusSeconds(60))),
                () -> assertThrows(IllegalStateException.class, () -> refreshTokenStore.findActiveByTokenHash("token-hash", Instant.now(CLOCK))),
                () -> assertThrows(IllegalStateException.class, () -> refreshTokenStore.revokeSession("session-id", Instant.now(CLOCK))),
                () -> assertThrows(IllegalStateException.class, () -> authIdentityStore.findByProviderSubject("google", "subject")),
                () -> assertThrows(IllegalStateException.class, () -> authIdentityStore.findByUserId("alice-id")),
                () -> assertThrows(IllegalStateException.class, () -> authIdentityStore.findByEmail("alice@example.com")),
                () -> assertThrows(IllegalStateException.class, () -> authIdentityStore.save(new UserIdentityRecord("alice-id", "google", "subject", "alice@example.com", "https://example.com/a.png")))
        );
    }

    private PostgresConnectionFactory unreachableConnectionFactory() {
        return new PostgresConnectionFactory(
                new PostgresConfig(
                        "jdbc:postgresql://127.0.0.1:1/whispers?connectTimeout=1&socketTimeout=1",
                        "whispers",
                        "whispers"
                )
        );
    }
}
