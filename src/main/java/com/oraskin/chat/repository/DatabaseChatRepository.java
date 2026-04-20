package com.oraskin.chat.repository;

import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
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
    public ChatRecord createChat(String firstUserId, String secondUserId) {
        String orderedFirstUserId = firstUserId.compareTo(secondUserId) <= 0 ? firstUserId : secondUserId;
        String orderedSecondUserId = firstUserId.compareTo(secondUserId) <= 0 ? secondUserId : firstUserId;
        String sql = """
                INSERT INTO chats (first_user_id, second_user_id)
                VALUES (?, ?)
                ON CONFLICT (first_user_id, second_user_id) DO UPDATE
                    SET first_user_id = EXCLUDED.first_user_id
                RETURNING chat_id, first_user_id, second_user_id
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderedFirstUserId);
            statement.setString(2, orderedSecondUserId);
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
                SELECT chat_id, first_user_id, second_user_id
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

    private ChatRecord findChat(Connection connection, long chatId) throws SQLException {
        String sql = """
                SELECT chat_id, first_user_id, second_user_id
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
                resultSet.getString("second_user_id")
        );
    }

    private MessageRecord mapMessage(ResultSet resultSet) throws SQLException {
        OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
        if (createdAt != null) {
            return new MessageRecord(
                    resultSet.getLong("chat_id"),
                    resultSet.getString("sender_user_id"),
                    resultSet.getString("text"),
                    createdAt.toInstant().toString()
            );
        }

        Timestamp timestamp = resultSet.getTimestamp("created_at");
        String value = timestamp == null ? null : timestamp.toInstant().toString();
        return new MessageRecord(
                resultSet.getLong("chat_id"),
                resultSet.getString("sender_user_id"),
                resultSet.getString("text"),
                value
        );
    }
}
