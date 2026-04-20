package com.oraskin.chat.repository;

import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.postgres.PostgresConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DatabaseChatRepository implements ChatRepository {

    private final PostgresConnectionFactory connectionFactory;
    private final Clock clock;

    public DatabaseChatRepository(PostgresConnectionFactory connectionFactory, Clock clock) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ChatRecord createChat(
            String firstUserId,
            String firstUserKeyId,
            String secondUserId,
            String secondUserKeyId,
            ChatType chatType
    ) {
        boolean firstComesFirst = firstUserId.compareTo(secondUserId) <= 0;
        String orderedFirstUserId = firstComesFirst ? firstUserId : secondUserId;
        String orderedSecondUserId = firstComesFirst ? secondUserId : firstUserId;
        String orderedFirstUserKeyId = firstComesFirst ? firstUserKeyId : secondUserKeyId;
        String orderedSecondUserKeyId = firstComesFirst ? secondUserKeyId : firstUserKeyId;
        String sql = """
                INSERT INTO chats (first_user_id, first_user_key_id, second_user_id, second_user_key_id, chat_type)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (first_user_id, first_user_key_id, second_user_id, second_user_key_id, chat_type) DO UPDATE
                    SET first_user_id = EXCLUDED.first_user_id
                RETURNING chat_id, first_user_id, first_user_key_id, second_user_id, second_user_key_id, chat_type
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderedFirstUserId);
            statement.setString(2, orderedFirstUserKeyId);
            statement.setString(3, orderedSecondUserId);
            statement.setString(4, orderedSecondUserKeyId);
            statement.setString(5, chatType.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Chat insert did not return a row");
                }
                return mapChat(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create chat", e);
        }
    }

    @Override
    public ChatRecord findChat(long chatId) {
        try (Connection connection = connectionFactory.openConnection()) {
            return findChat(connection, chatId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find chat", e);
        }
    }

    @Override
    public List<ChatRecord> findChatsForUser(String userId) {
        String sql = """
                SELECT chat_id, first_user_id, first_user_key_id, second_user_id, second_user_key_id, chat_type
                FROM chats
                WHERE first_user_id = ? OR second_user_id = ?
                ORDER BY chat_id
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<ChatRecord> chats = new ArrayList<>();
                while (resultSet.next()) {
                    chats.add(mapChat(resultSet));
                }
                return chats;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find chats for user", e);
        }
    }

    @Override
    public MessageRecord appendMessage(long chatId, String senderUserId, String text) {
        String sql = """
                INSERT INTO messages (chat_id, sender_user_id, text, created_at)
                VALUES (?, ?, ?, ?)
                RETURNING chat_id, sender_user_id, text, created_at
                """;

        Instant createdAt = Instant.now(clock);
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setString(2, senderUserId);
            statement.setString(3, text);
            statement.setObject(4, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Message insert did not return a row");
                }
                return mapMessage(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to append message", e);
        }
    }

    @Override
    public List<MessageRecord> findMessages(long chatId) {
        String sql = """
                SELECT chat_id, sender_user_id, text, created_at
                FROM messages
                WHERE chat_id = ?
                ORDER BY id
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<MessageRecord> messages = new ArrayList<>();
                while (resultSet.next()) {
                    messages.add(mapMessage(resultSet));
                }
                return messages;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find messages", e);
        }
    }

    @Override
    public PrivateMessageRecord appendPrivateMessage(long chatId, String senderUserId, EncryptedPrivateMessagePayload encryptedMessage) {
        String sql = """
                INSERT INTO private_messages (
                    chat_id,
                    sender_user_id,
                    protocol_version,
                    encryption_algorithm,
                    key_wrap_algorithm,
                    ciphertext,
                    nonce,
                    sender_key_id,
                    sender_message_key_envelope,
                    recipient_key_id,
                    recipient_message_key_envelope,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING
                    chat_id,
                    sender_user_id,
                    protocol_version,
                    encryption_algorithm,
                    key_wrap_algorithm,
                    ciphertext,
                    nonce,
                    sender_key_id,
                    sender_message_key_envelope,
                    recipient_key_id,
                    recipient_message_key_envelope,
                    created_at
                """;

        Instant createdAt = Instant.now(clock);
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setString(2, senderUserId);
            statement.setString(3, encryptedMessage.protocolVersion());
            statement.setString(4, encryptedMessage.encryptionAlgorithm());
            statement.setString(5, encryptedMessage.keyWrapAlgorithm());
            statement.setString(6, encryptedMessage.ciphertext());
            statement.setString(7, encryptedMessage.nonce());
            statement.setString(8, encryptedMessage.senderKeyId());
            statement.setString(9, encryptedMessage.senderMessageKeyEnvelope());
            statement.setString(10, encryptedMessage.recipientKeyId());
            statement.setString(11, encryptedMessage.recipientMessageKeyEnvelope());
            statement.setObject(12, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Private message insert did not return a row");
                }
                return mapPrivateMessage(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to append private message", e);
        }
    }

    @Override
    public List<PrivateMessageRecord> findPrivateMessages(long chatId) {
        String sql = """
                SELECT
                    chat_id,
                    sender_user_id,
                    protocol_version,
                    encryption_algorithm,
                    key_wrap_algorithm,
                    ciphertext,
                    nonce,
                    sender_key_id,
                    sender_message_key_envelope,
                    recipient_key_id,
                    recipient_message_key_envelope,
                    created_at
                FROM private_messages
                WHERE chat_id = ?
                ORDER BY id
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<PrivateMessageRecord> messages = new ArrayList<>();
                while (resultSet.next()) {
                    messages.add(mapPrivateMessage(resultSet));
                }
                return messages;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find private messages", e);
        }
    }

    private ChatRecord findChat(Connection connection, long chatId) throws SQLException {
        String sql = """
                SELECT chat_id, first_user_id, first_user_key_id, second_user_id, second_user_key_id, chat_type
                FROM chats
                WHERE chat_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapChat(resultSet);
            }
        }
    }

    private ChatRecord mapChat(ResultSet resultSet) throws SQLException {
        return new ChatRecord(
                resultSet.getLong("chat_id"),
                resultSet.getString("first_user_id"),
                resultSet.getString("second_user_id"),
                ChatType.valueOf(resultSet.getString("chat_type")),
                resultSet.getString("first_user_key_id"),
                resultSet.getString("second_user_key_id")
        );
    }

    private MessageRecord mapMessage(ResultSet resultSet) throws SQLException {
        return new MessageRecord(
                resultSet.getLong("chat_id"),
                resultSet.getString("sender_user_id"),
                resultSet.getString("text"),
                mapTimestamp(resultSet)
        );
    }

    private PrivateMessageRecord mapPrivateMessage(ResultSet resultSet) throws SQLException {
        EncryptedPrivateMessagePayload encryptedMessage = new EncryptedPrivateMessagePayload(
                resultSet.getString("protocol_version"),
                resultSet.getString("encryption_algorithm"),
                resultSet.getString("key_wrap_algorithm"),
                resultSet.getString("ciphertext"),
                resultSet.getString("nonce"),
                resultSet.getString("sender_key_id"),
                resultSet.getString("sender_message_key_envelope"),
                resultSet.getString("recipient_key_id"),
                resultSet.getString("recipient_message_key_envelope")
        );

        return new PrivateMessageRecord(
                resultSet.getLong("chat_id"),
                resultSet.getString("sender_user_id"),
                encryptedMessage,
                mapTimestamp(resultSet)
        );
    }

    private String mapTimestamp(ResultSet resultSet) throws SQLException {
        OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
        if (createdAt != null) {
            return createdAt.toInstant().toString();
        }

        Timestamp timestamp = resultSet.getTimestamp("created_at");
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
