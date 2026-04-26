package com.oraskin.chat.persistence;

import com.oraskin.chat.key.persistence.DatabasePrivateChatKeyStore;
import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;
import com.oraskin.chat.key.value.PrivateChatKeyStatus;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.repository.DatabaseChatRepository;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.mapper.UserMapper;
import com.oraskin.user.data.persistence.DatabaseUserStore;
import com.oraskin.user.data.persistence.entity.UserData;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPersistenceStoreTest {

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
    void privateChatKeyStoreUpsertsAndFindsKeys() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("key_id")).thenReturn("key-1");
        when(resultSet.getString("public_key")).thenReturn("spki");
        when(resultSet.getString("algorithm")).thenReturn("RSA-OAEP");
        when(resultSet.getString("key_format")).thenReturn("spki");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));
        when(resultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));
        DatabasePrivateChatKeyStore store = new DatabasePrivateChatKeyStore(connectionFactoryBackedBy(connection), CLOCK);

        PrivateChatKeyRecord upserted = store.upsertKey("alice-id", "key-1", "spki", "RSA-OAEP", "spki");
        PrivateChatKeyRecord latest = store.findLatestKey("alice-id");

        assertThat(upserted).isEqualTo(new PrivateChatKeyRecord("alice-id", "key-1", "spki", "RSA-OAEP", "spki", PrivateChatKeyStatus.ACTIVE, "2026-04-21T10:15:30Z", "2026-04-21T10:15:30Z"));
        assertThat(latest).isEqualTo(upserted);
        verify(statement, times(2)).setString(1, "alice-id");
        verify(statement).setString(2, "key-1");
        verify(statement).setString(3, "spki");
        verify(statement).setString(4, "RSA-OAEP");
        verify(statement).setString(5, "spki");
        verify(statement).setString(6, "ACTIVE");
        verify(statement).setObject(eq(7), any(java.time.OffsetDateTime.class));
        verify(statement).setObject(eq(8), any(java.time.OffsetDateTime.class));
    }

    @Test
    void privateChatKeyStoreFindKeyFallsBackToSqlTimestamps() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("key_id")).thenReturn("key-1");
        when(resultSet.getString("public_key")).thenReturn("spki");
        when(resultSet.getString("algorithm")).thenReturn("RSA-OAEP");
        when(resultSet.getString("key_format")).thenReturn("spki");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(resultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-21T10:15:30Z")));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-21T10:16:30Z")));
        DatabasePrivateChatKeyStore store = new DatabasePrivateChatKeyStore(connectionFactoryBackedBy(connection), CLOCK);

        PrivateChatKeyRecord key = store.findKey("alice-id", "key-1");

        assertThat(key).isEqualTo(new PrivateChatKeyRecord("alice-id", "key-1", "spki", "RSA-OAEP", "spki", PrivateChatKeyStatus.ACTIVE, "2026-04-21T10:15:30Z", "2026-04-21T10:16:30Z"));
        verify(statement).setString(1, "alice-id");
        verify(statement).setString(2, "key-1");
        verify(statement).setString(3, "ACTIVE");
    }

    @Test
    void chatRepositoryOrdersUsersAndMapsPrivateMessages() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getLong("chat_id")).thenReturn(42L);
        when(resultSet.getString("first_user_id")).thenReturn("alice-id");
        when(resultSet.getString("second_user_id")).thenReturn("bob-id");
        when(resultSet.getString("chat_type")).thenReturn("PRIVATE");
        when(resultSet.getString("first_user_key_id")).thenReturn("alice-key");
        when(resultSet.getString("second_user_key_id")).thenReturn("bob-key");
        when(resultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(resultSet.getString("protocol_version")).thenReturn("v1");
        when(resultSet.getString("encryption_algorithm")).thenReturn("AES-GCM");
        when(resultSet.getString("key_wrap_algorithm")).thenReturn("RSA-OAEP");
        when(resultSet.getString("ciphertext")).thenReturn("ciphertext");
        when(resultSet.getString("nonce")).thenReturn("nonce");
        when(resultSet.getString("sender_key_id")).thenReturn("alice-key");
        when(resultSet.getString("sender_message_key_envelope")).thenReturn("sender-envelope");
        when(resultSet.getString("recipient_key_id")).thenReturn("bob-key");
        when(resultSet.getString("recipient_message_key_envelope")).thenReturn("recipient-envelope");
        when(resultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));
        when(resultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);
        DatabaseChatRepository repository = new DatabaseChatRepository(connectionFactoryBackedBy(connection), CLOCK);

        ChatRecord chatRecord = repository.createChat("bob-id", "bob-key", "alice-id", "alice-key", ChatType.PRIVATE);
        PrivateMessageRecord privateMessageRecord = repository.appendPrivateMessage(
                42L,
                "alice-id",
                new EncryptedPrivateMessagePayload("v1", "AES-GCM", "RSA-OAEP", "ciphertext", "nonce", "alice-key", "sender-envelope", "bob-key", "recipient-envelope")
        );

        assertThat(chatRecord).isEqualTo(new ChatRecord(42L, "alice-id", "bob-id", ChatType.PRIVATE, "alice-key", "bob-key"));
        assertThat(privateMessageRecord.encryptedMessage().ciphertext()).isEqualTo("ciphertext");
        assertThat(privateMessageRecord.updatedAt()).isNull();
        verify(statement).setString(1, "alice-id");
        verify(statement).setString(2, "alice-key");
        verify(statement).setString(3, "bob-id");
        verify(statement).setString(4, "bob-key");
        verify(statement).setString(5, "PRIVATE");
    }

    @Test
    void chatRepositoryFindersAndPlaintextMessagesMapRows() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement findChatStatement = mock(PreparedStatement.class);
        PreparedStatement findChatsStatement = mock(PreparedStatement.class);
        PreparedStatement appendMessageStatement = mock(PreparedStatement.class);
        PreparedStatement findMessageStatement = mock(PreparedStatement.class);
        PreparedStatement findMessagesStatement = mock(PreparedStatement.class);
        PreparedStatement deleteMessageStatement = mock(PreparedStatement.class);
        ResultSet findChatResultSet = mock(ResultSet.class);
        ResultSet findChatsResultSet = mock(ResultSet.class);
        ResultSet appendMessageResultSet = mock(ResultSet.class);
        ResultSet findMessageResultSet = mock(ResultSet.class);
        ResultSet findMessagesResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(
                findChatStatement,
                findChatsStatement,
                appendMessageStatement,
                findMessageStatement,
                findMessagesStatement,
                deleteMessageStatement
        );
        when(findChatStatement.executeQuery()).thenReturn(findChatResultSet);
        when(findChatResultSet.next()).thenReturn(true);
        when(findChatResultSet.getLong("chat_id")).thenReturn(42L);
        when(findChatResultSet.getString("first_user_id")).thenReturn("alice-id");
        when(findChatResultSet.getString("second_user_id")).thenReturn("bob-id");
        when(findChatResultSet.getString("chat_type")).thenReturn("DIRECT");

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
        when(appendMessageResultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);

        when(findMessageStatement.executeQuery()).thenReturn(findMessageResultSet);
        when(findMessageResultSet.next()).thenReturn(true);
        when(findMessageResultSet.getLong("id")).thenReturn(100L);
        when(findMessageResultSet.getLong("chat_id")).thenReturn(42L);
        when(findMessageResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(findMessageResultSet.getString("text")).thenReturn("hello");
        when(findMessageResultSet.getObject("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:45Z"));
        when(findMessageResultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);

        when(findMessagesStatement.executeQuery()).thenReturn(findMessagesResultSet);
        when(findMessagesResultSet.next()).thenReturn(true, false);
        when(findMessagesResultSet.getLong("id")).thenReturn(100L);
        when(findMessagesResultSet.getLong("chat_id")).thenReturn(42L);
        when(findMessagesResultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(findMessagesResultSet.getString("text")).thenReturn("hello");
        when(findMessagesResultSet.getObject("created_at", java.time.OffsetDateTime.class))
                .thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:16:30Z"));
        when(findMessagesResultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);
        DatabaseChatRepository repository = new DatabaseChatRepository(connectionFactoryBackedBy(connection), CLOCK);

        ChatRecord chat = repository.findChat(42L);
        List<ChatRecord> chats = repository.findChatsForUser("alice-id");
        var appendedMessage = repository.appendMessage(42L, "alice-id", "hello");
        var foundMessage = repository.findMessage(100L);
        List<com.oraskin.chat.repository.entity.MessageRecord> messages = repository.findMessages(42L);
        repository.deleteMessage(100L);

        assertThat(chat).isEqualTo(new ChatRecord(42L, "alice-id", "bob-id", ChatType.DIRECT, null, null));
        assertThat(chats).containsExactly(chat);
        assertThat(appendedMessage.messageId()).isEqualTo(100L);
        assertThat(foundMessage.timestamp()).isEqualTo("2026-04-21T10:15:45Z");
        assertThat(appendedMessage.text()).isEqualTo("hello");
        assertThat(appendedMessage.updatedAt()).isNull();
        assertThat(foundMessage.updatedAt()).isNull();
        assertThat(messages).extracting(com.oraskin.chat.repository.entity.MessageRecord::timestamp).containsExactly("2026-04-21T10:16:30Z");
        assertThat(messages).extracting(com.oraskin.chat.repository.entity.MessageRecord::updatedAt).containsExactly((String) null);
        verify(findChatStatement).setLong(1, 42L);
        verify(findChatsStatement).setString(1, "alice-id");
        verify(findChatsStatement).setString(2, "alice-id");
        verify(appendMessageStatement).setLong(1, 42L);
        verify(appendMessageStatement).setString(2, "alice-id");
        verify(appendMessageStatement).setString(3, "hello");
        verify(findMessageStatement).setLong(1, 100L);
        verify(findMessagesStatement).setLong(1, 42L);
        verify(deleteMessageStatement).setLong(1, 100L);
    }

    @Test
    void chatRepositoryFindPrivateMessagesFallsBackToSqlTimestamps() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("chat_id")).thenReturn(42L);
        when(resultSet.getString("sender_user_id")).thenReturn("alice-id");
        when(resultSet.getString("protocol_version")).thenReturn("v1");
        when(resultSet.getString("encryption_algorithm")).thenReturn("AES-GCM");
        when(resultSet.getString("key_wrap_algorithm")).thenReturn("RSA-OAEP");
        when(resultSet.getString("ciphertext")).thenReturn("ciphertext");
        when(resultSet.getString("nonce")).thenReturn("nonce");
        when(resultSet.getString("sender_key_id")).thenReturn("alice-key");
        when(resultSet.getString("sender_message_key_envelope")).thenReturn("sender-envelope");
        when(resultSet.getString("recipient_key_id")).thenReturn("bob-key");
        when(resultSet.getString("recipient_message_key_envelope")).thenReturn("recipient-envelope");
        when(resultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(resultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-21T10:15:30Z")));
        DatabaseChatRepository repository = new DatabaseChatRepository(connectionFactoryBackedBy(connection), CLOCK);

        List<PrivateMessageRecord> messages = repository.findPrivateMessages(42L);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().timestamp()).isEqualTo("2026-04-21T10:15:30Z");
        assertThat(messages.getFirst().encryptedMessage().recipientKeyId()).isEqualTo("bob-key");
        assertThat(messages.getFirst().updatedAt()).isNull();
        verify(statement).setLong(1, 42L);
    }

    @Test
    void userStoreBatchesFindsAndNormalizesUsernames() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("user_id")).thenReturn("bob-id", "alice-id");
        when(resultSet.getString("username")).thenReturn("bob@example.com", "alice@example.com");
        when(resultSet.getString("first_name")).thenReturn("Bob", "Alice");
        when(resultSet.getString("last_name")).thenReturn("Example", "Example");
        when(resultSet.getTimestamp("last_ping_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 16)), Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 15)));
        DatabaseUserStore userStore = new DatabaseUserStore(connectionFactoryBackedBy(connection), CLOCK);

        List<User> users = userStore.findUsers(java.util.Arrays.asList("alice-id", null, "bob-id", "alice-id"));
        userStore.findByUsername(" Alice@Example.com ");
        userStore.updateUser("alice-id", " Alice@Example.com ", "Alice", "Example");

        assertThat(users).extracting(User::userId).containsExactly("alice-id", "bob-id");
        verify(statement).setString(1, "alice-id");
        verify(statement).setString(2, "bob-id");
        verify(statement, times(2)).setString(1, "alice@example.com");
        verify(statement).setString(4, "alice-id");
    }

    @Test
    void userStorePingsFindsAndCreatesUsers() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement pingStatement = mock(PreparedStatement.class);
        PreparedStatement findStatement = mock(PreparedStatement.class);
        PreparedStatement createStatement = mock(PreparedStatement.class);
        ResultSet pingResultSet = mock(ResultSet.class);
        ResultSet findResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(pingStatement, findStatement, createStatement);
        when(pingStatement.executeQuery()).thenReturn(pingResultSet);
        when(pingResultSet.next()).thenReturn(true);
        when(pingResultSet.getString("user_id")).thenReturn("alice-id");
        when(pingResultSet.getString("username")).thenReturn("alice@example.com");
        when(pingResultSet.getString("first_name")).thenReturn("Alice");
        when(pingResultSet.getString("last_name")).thenReturn("Example");
        when(pingResultSet.getTimestamp("last_ping_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 15)));
        when(findStatement.executeQuery()).thenReturn(findResultSet);
        when(findResultSet.next()).thenReturn(true);
        when(findResultSet.getString("user_id")).thenReturn("bob-id");
        when(findResultSet.getString("username")).thenReturn("bob@example.com");
        when(findResultSet.getString("first_name")).thenReturn("Bob");
        when(findResultSet.getString("last_name")).thenReturn("Example");
        when(findResultSet.getTimestamp("last_ping_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 16)));
        DatabaseUserStore userStore = new DatabaseUserStore(connectionFactoryBackedBy(connection), CLOCK);

        User pingedUser = userStore.ping("alice-id");
        User foundUser = userStore.findUser("bob-id");
        String createdUserId = userStore.createUser(" Alice@Example.com ", "Alice", "Example");

        assertThat(pingedUser).isEqualTo(new User("alice-id", "alice@example.com", "Alice", "Example", LocalDateTime.of(2026, 4, 21, 10, 15)));
        assertThat(foundUser).isEqualTo(new User("bob-id", "bob@example.com", "Bob", "Example", LocalDateTime.of(2026, 4, 21, 10, 16)));
        assertThat(createdUserId).isNotBlank();
        verify(pingStatement).setString(2, "alice-id");
        verify(findStatement).setString(1, "bob-id");
        verify(createStatement).setString(1, "alice@example.com");
        verify(createStatement).setString(eq(2), any(String.class));
    }

    @Test
    void userMapperMapsPersistenceRowsToDomainUsers() {
        UserMapper userMapper = new UserMapper();
        UserData userData = new UserData("alice-id", "alice@example.com", "Alice", "Example", LocalDateTime.of(2026, 4, 21, 10, 15));

        assertThat(userMapper.toDomain(userData))
                .isEqualTo(new User("alice-id", "alice@example.com", "Alice", "Example", LocalDateTime.of(2026, 4, 21, 10, 15)));
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
