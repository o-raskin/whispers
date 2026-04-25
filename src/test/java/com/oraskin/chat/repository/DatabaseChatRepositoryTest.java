package com.oraskin.chat.repository;

import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseChatRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void createFindAndAppendPlaintextMessagesMapRows() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement createStatement = mock(PreparedStatement.class);
        PreparedStatement findStatement = mock(PreparedStatement.class);
        PreparedStatement findChatsStatement = mock(PreparedStatement.class);
        PreparedStatement appendMessageStatement = mock(PreparedStatement.class);
        PreparedStatement findMessageStatement = mock(PreparedStatement.class);
        PreparedStatement findMessagesStatement = mock(PreparedStatement.class);
        PreparedStatement deleteMessageStatement = mock(PreparedStatement.class);
        ResultSet createResultSet = mock(ResultSet.class);
        ResultSet findResultSet = mock(ResultSet.class);
        ResultSet findChatsResultSet = mock(ResultSet.class);
        ResultSet appendMessageResultSet = mock(ResultSet.class);
        ResultSet findMessageResultSet = mock(ResultSet.class);
        ResultSet findMessagesResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(
                createStatement,
                findStatement,
                findChatsStatement,
                appendMessageStatement,
                findMessageStatement,
                findMessagesStatement,
                deleteMessageStatement
        );
        when(createStatement.executeQuery()).thenReturn(createResultSet);
        when(createResultSet.next()).thenReturn(true);
        when(createResultSet.getLong("chat_id")).thenReturn(42L);
        when(createResultSet.getString("first_user_id")).thenReturn("alice-id");
        when(createResultSet.getString("second_user_id")).thenReturn("bob-id");
        when(createResultSet.getString("chat_type")).thenReturn("DIRECT");

        when(findStatement.executeQuery()).thenReturn(findResultSet);
        when(findResultSet.next()).thenReturn(true);
        when(findResultSet.getLong("chat_id")).thenReturn(42L);
        when(findResultSet.getString("first_user_id")).thenReturn("alice-id");
        when(findResultSet.getString("second_user_id")).thenReturn("bob-id");
        when(findResultSet.getString("chat_type")).thenReturn("DIRECT");

        when(findChatsStatement.executeQuery()).thenReturn(findChatsResultSet);
        when(findChatsResultSet.next()).thenReturn(true, false);
        when(findChatsResultSet.getLong("chat_id")).thenReturn(42L);
        when(findChatsResultSet.getString("first_user_id")).thenReturn("alice-id");
        when(findChatsResultSet.getString("second_user_id")).thenReturn("bob-id");
        when(findChatsResultSet.getString("chat_type")).thenReturn("DIRECT");

        when(appendMessageStatement.executeQuery()).thenReturn(appendMessageResultSet);
        when(appendMessageResultSet.next()).thenReturn(true);
        when(appendMessageResultSet.getLong("id")).thenReturn(100L);
        when(appendMessageResultSet.getLong("chat_id")).thenReturn(42L);
        when(appendMessageResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(appendMessageResultSet.getString("text")).thenReturn("hello");
        when(appendMessageResultSet.getObject("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));

        when(findMessageStatement.executeQuery()).thenReturn(findMessageResultSet);
        when(findMessageResultSet.next()).thenReturn(true);
        when(findMessageResultSet.getLong("id")).thenReturn(100L);
        when(findMessageResultSet.getLong("chat_id")).thenReturn(42L);
        when(findMessageResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(findMessageResultSet.getString("text")).thenReturn("hello");
        when(findMessageResultSet.getObject("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:45Z"));

        when(findMessagesStatement.executeQuery()).thenReturn(findMessagesResultSet);
        when(findMessagesResultSet.next()).thenReturn(true, false);
        when(findMessagesResultSet.getLong("id")).thenReturn(100L);
        when(findMessagesResultSet.getLong("chat_id")).thenReturn(42L);
        when(findMessagesResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(findMessagesResultSet.getString("text")).thenReturn("hello");
        when(findMessagesResultSet.getObject("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:16:30Z"));
        DatabaseChatRepository repository = new DatabaseChatRepository(connectionFactoryBackedBy(connection), CLOCK);

        ChatRecord createdChat = repository.createChat("bob-id", null, "alice-id", null, ChatType.DIRECT);
        ChatRecord foundChat = repository.findChat(42L);
        List<ChatRecord> chats = repository.findChatsForUser("alice-id");
        MessageRecord appendedMessage = repository.appendMessage(42L, "alice-id", "hello");
        MessageRecord foundMessage = repository.findMessage(100L);
        List<MessageRecord> messages = repository.findMessages(42L);
        repository.deleteMessage(100L);

        assertThat(createdChat).isEqualTo(new ChatRecord(42L, "alice-id", "bob-id", ChatType.DIRECT, null, null));
        assertThat(foundChat).isEqualTo(createdChat);
        assertThat(chats).containsExactly(createdChat);
        assertThat(appendedMessage.messageId()).isEqualTo(100L);
        assertThat(foundMessage.timestamp()).isEqualTo("2026-04-21T10:15:45Z");
        assertThat(appendedMessage.text()).isEqualTo("hello");
        assertThat(messages).extracting(MessageRecord::timestamp).containsExactly("2026-04-21T10:16:30Z");
        verify(createStatement).setString(1, "alice-id");
        verify(createStatement).setString(3, "bob-id");
        verify(findStatement).setLong(1, 42L);
        verify(findChatsStatement).setString(1, "alice-id");
        verify(findChatsStatement).setString(2, "alice-id");
        verify(appendMessageStatement).setLong(1, 42L);
        verify(findMessageStatement).setLong(1, 100L);
        verify(findMessagesStatement).setLong(1, 42L);
        verify(deleteMessageStatement).setLong(1, 100L);
    }

    @Test
    void appendAndFindPrivateMessagesMapEncryptedPayloads() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement appendStatement = mock(PreparedStatement.class);
        PreparedStatement findStatement = mock(PreparedStatement.class);
        ResultSet appendResultSet = mock(ResultSet.class);
        ResultSet findResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(appendStatement, findStatement);
        when(appendStatement.executeQuery()).thenReturn(appendResultSet);
        when(appendResultSet.next()).thenReturn(true);
        when(appendResultSet.getLong("chat_id")).thenReturn(42L);
        when(appendResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(appendResultSet.getString("protocol_version")).thenReturn("v1");
        when(appendResultSet.getString("encryption_algorithm")).thenReturn("AES-GCM");
        when(appendResultSet.getString("key_wrap_algorithm")).thenReturn("RSA-OAEP");
        when(appendResultSet.getString("ciphertext")).thenReturn("ciphertext");
        when(appendResultSet.getString("nonce")).thenReturn("nonce");
        when(appendResultSet.getString("sender_key_id")).thenReturn("alice-key");
        when(appendResultSet.getString("sender_message_key_envelope")).thenReturn("sender-envelope");
        when(appendResultSet.getString("recipient_key_id")).thenReturn("bob-key");
        when(appendResultSet.getString("recipient_message_key_envelope")).thenReturn("recipient-envelope");
        when(appendResultSet.getObject("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));

        when(findStatement.executeQuery()).thenReturn(findResultSet);
        when(findResultSet.next()).thenReturn(true, false);
        when(findResultSet.getLong("chat_id")).thenReturn(42L);
        when(findResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(findResultSet.getString("protocol_version")).thenReturn("v1");
        when(findResultSet.getString("encryption_algorithm")).thenReturn("AES-GCM");
        when(findResultSet.getString("key_wrap_algorithm")).thenReturn("RSA-OAEP");
        when(findResultSet.getString("ciphertext")).thenReturn("ciphertext");
        when(findResultSet.getString("nonce")).thenReturn("nonce");
        when(findResultSet.getString("sender_key_id")).thenReturn("alice-key");
        when(findResultSet.getString("sender_message_key_envelope")).thenReturn("sender-envelope");
        when(findResultSet.getString("recipient_key_id")).thenReturn("bob-key");
        when(findResultSet.getString("recipient_message_key_envelope")).thenReturn("recipient-envelope");
        when(findResultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(findResultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-21T10:16:30Z")));
        DatabaseChatRepository repository = new DatabaseChatRepository(connectionFactoryBackedBy(connection), CLOCK);
        EncryptedPrivateMessagePayload encryptedMessage = new EncryptedPrivateMessagePayload(
                "v1", "AES-GCM", "RSA-OAEP", "ciphertext", "nonce", "alice-key", "sender-envelope", "bob-key", "recipient-envelope"
        );

        PrivateMessageRecord appended = repository.appendPrivateMessage(42L, "alice-id", encryptedMessage);
        List<PrivateMessageRecord> foundMessages = repository.findPrivateMessages(42L);

        assertThat(appended.encryptedMessage().ciphertext()).isEqualTo("ciphertext");
        assertThat(foundMessages).hasSize(1);
        assertThat(foundMessages.getFirst().timestamp()).isEqualTo("2026-04-21T10:16:30Z");
        verify(findStatement).setLong(1, 42L);
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:chat-" + registeredDrivers.size();
        Driver driver = new SingleConnectionDriver(jdbcUrl, connection);
        DriverManager.registerDriver(driver);
        registeredDrivers.add(driver);
        return new PostgresConnectionFactory(new PostgresConfig(jdbcUrl, "user", "password"));
    }

    private record SingleConnectionDriver(String jdbcUrl, Connection connection) implements Driver {

        @Override
        public Connection connect(String url, Properties info) {
            return acceptsURL(url) ? connection : null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return jdbcUrl.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
