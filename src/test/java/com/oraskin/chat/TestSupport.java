package com.oraskin.chat;

import com.oraskin.chat.key.persistence.PrivateChatKeyStore;
import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;
import com.oraskin.chat.key.value.PrivateChatKeyStatus;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.value.ChatType;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.session.ClientSession;
import com.oraskin.user.session.persistence.SessionRegistry;
import com.oraskin.websocket.WebSocketMessageSender;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TestSupport {

    private TestSupport() {
    }

    public static User user(String userId, String username) {
        return new User(userId, username, null, null, null);
    }

    public static final class InMemoryChatRepository implements ChatRepository {

        private final Map<Long, ChatRecord> chats = new LinkedHashMap<>();
        private final Map<Long, MessageRecord> messagesById = new LinkedHashMap<>();
        private final Map<Long, List<MessageRecord>> directMessages = new LinkedHashMap<>();
        private final Map<Long, List<PrivateMessageRecord>> privateMessages = new LinkedHashMap<>();
        private long nextChatId = 1L;
        private long nextMessageId = 1L;

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
            for (ChatRecord existing : chats.values()) {
                if (existing.firstUserId().equals(orderedFirstUserId)
                        && Objects.equals(existing.firstUserKeyId(), orderedFirstUserKeyId)
                        && existing.secondUserId().equals(orderedSecondUserId)
                        && Objects.equals(existing.secondUserKeyId(), orderedSecondUserKeyId)
                        && existing.type() == chatType) {
                    return existing;
                }
            }

            ChatRecord created = new ChatRecord(
                    nextChatId++,
                    orderedFirstUserId,
                    orderedSecondUserId,
                    chatType,
                    orderedFirstUserKeyId,
                    orderedSecondUserKeyId
            );
            chats.put(created.chatId(), created);
            return created;
        }

        @Override
        public ChatRecord findChat(long chatId) {
            return chats.get(chatId);
        }

        @Override
        public List<ChatRecord> findChatsForUser(String userId) {
            return chats.values().stream()
                    .filter(chat -> chat.hasParticipant(userId))
                    .toList();
        }

        @Override
        public void deleteChat(long chatId) {
            chats.remove(chatId);

            List<MessageRecord> removedMessages = directMessages.remove(chatId);
            if (removedMessages != null) {
                for (MessageRecord removedMessage : removedMessages) {
                    messagesById.remove(removedMessage.messageId());
                }
            }

            privateMessages.remove(chatId);
        }

        @Override
        public MessageRecord appendMessage(long chatId, String senderUserId, String text) {
            MessageRecord record = new MessageRecord(nextMessageId++, chatId, senderUserId, text, "2026-04-20T12:00:00Z", null);
            messagesById.put(record.messageId(), record);
            directMessages.computeIfAbsent(chatId, ignored -> new ArrayList<>()).add(record);
            return record;
        }

        @Override
        public MessageRecord findMessage(long messageId) {
            return messagesById.get(messageId);
        }

        @Override
        public List<MessageRecord> findMessages(long chatId) {
            return List.copyOf(directMessages.getOrDefault(chatId, List.of()));
        }

        @Override
        public MessageRecord updateMessage(long messageId, String text) {
            MessageRecord current = messagesById.get(messageId);
            if (current == null) {
                return null;
            }

            MessageRecord updated = new MessageRecord(
                    current.messageId(),
                    current.chatId(),
                    current.senderUserId(),
                    text,
                    current.timestamp(),
                    "2026-04-20T12:05:00Z"
            );
            messagesById.put(messageId, updated);

            List<MessageRecord> messages = directMessages.get(current.chatId());
            if (messages == null) {
                return updated;
            }

            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).messageId() == messageId) {
                    messages.set(i, updated);
                    break;
                }
            }
            return updated;
        }

        @Override
        public void deleteMessage(long messageId) {
            MessageRecord removed = messagesById.remove(messageId);
            if (removed == null) {
                return;
            }

            List<MessageRecord> messages = directMessages.get(removed.chatId());
            if (messages == null) {
                return;
            }

            messages.removeIf(message -> message.messageId() == messageId);
            if (messages.isEmpty()) {
                directMessages.remove(removed.chatId());
            }
        }

        @Override
        public PrivateMessageRecord appendPrivateMessage(long chatId, String senderUserId, EncryptedPrivateMessagePayload encryptedMessage) {
            PrivateMessageRecord record = new PrivateMessageRecord(
                    chatId,
                    senderUserId,
                    encryptedMessage,
                    "2026-04-20T12:00:00Z",
                    null
            );
            privateMessages.computeIfAbsent(chatId, ignored -> new ArrayList<>()).add(record);
            return record;
        }

        @Override
        public List<PrivateMessageRecord> findPrivateMessages(long chatId) {
            return List.copyOf(privateMessages.getOrDefault(chatId, List.of()));
        }
    }

    public static final class InMemoryPrivateChatKeyStore implements PrivateChatKeyStore {

        private final Map<String, Map<String, PrivateChatKeyRecord>> keysByUserId = new LinkedHashMap<>();

        @Override
        public PrivateChatKeyRecord upsertKey(String userId, String keyId, String publicKey, String algorithm, String format) {
            Map<String, PrivateChatKeyRecord> userKeys = keysByUserId.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
            PrivateChatKeyRecord existing = userKeys.get(keyId);
            PrivateChatKeyRecord current = new PrivateChatKeyRecord(
                    userId,
                    keyId,
                    publicKey,
                    algorithm,
                    format,
                    PrivateChatKeyStatus.ACTIVE,
                    existing == null ? "2026-04-20T12:00:00Z" : existing.createdAt(),
                    "2026-04-20T12:01:00Z"
            );
            userKeys.put(keyId, current);
            return current;
        }

        @Override
        public PrivateChatKeyRecord findKey(String userId, String keyId) {
            return keysByUserId.getOrDefault(userId, Map.of()).values().stream()
                    .filter(record -> record.status() == PrivateChatKeyStatus.ACTIVE)
                    .filter(record -> record.keyId().equals(keyId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public PrivateChatKeyRecord findLatestKey(String userId) {
            return keysByUserId.getOrDefault(userId, Map.of()).values().stream()
                    .filter(record -> record.status() == PrivateChatKeyStatus.ACTIVE)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        public PrivateChatKeyRecord findStoredKey(String userId, String keyId) {
            return keysByUserId.getOrDefault(userId, Map.of()).get(keyId);
        }
    }

    public static final class InMemoryUserStore implements UserStore {

        private final Map<String, User> usersById = new LinkedHashMap<>();
        private final Map<String, User> usersByUsername = new LinkedHashMap<>();

        public void add(User user) {
            usersById.put(user.userId(), user);
            usersByUsername.put(user.username(), user);
        }

        @Override
        public User ping(String userId) {
            User current = usersById.get(userId);
            if (current == null) {
                return null;
            }
            User updated = new User(
                    current.userId(),
                    current.username(),
                    current.firstName(),
                    current.lastName(),
                    LocalDateTime.of(2026, 4, 20, 12, 0)
            );
            add(updated);
            return updated;
        }

        @Override
        public User findUser(String userId) {
            return usersById.get(userId);
        }

        @Override
        public List<User> findUsers(Collection<String> userIds) {
            return userIds.stream()
                    .map(usersById::get)
                    .filter(user -> user != null)
                    .toList();
        }

        @Override
        public User findByUsername(String username) {
            return usersByUsername.get(username);
        }

        @Override
        public String createUser(String username, String firstName, String lastName) {
            String userId = UUID.randomUUID().toString();
            add(new User(userId, username, firstName, lastName, null));
            return userId;
        }

        @Override
        public void updateUser(String userId, String username, String firstName, String lastName) {
            add(new User(userId, username, firstName, lastName, null));
        }
    }

    public static final class NoopSessionRegistry implements SessionRegistry {

        @Override
        public boolean createUserSession(String userId, ClientSession session) {
            return true;
        }

        @Override
        public ClientSession findSession(String userId) {
            return null;
        }

        @Override
        public void remove(String userId) {
        }

        @Override
        public boolean isConnected(String userId) {
            return false;
        }
    }

    public static final class NoopWebSocketMessageSender implements WebSocketMessageSender {

        @Override
        public void sendToUser(String userId, Object payload) {
        }

        @Override
        public void sendToUsers(Collection<String> userIds, Object payload) {
        }
    }
}
