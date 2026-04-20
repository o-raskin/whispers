package com.oraskin.chat.service;

import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.connection.SendMessageCommand;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.session.persistence.SessionRegistry;

import java.util.List;
import java.util.Objects;

public final class ChatService {

    private final ChatRepository chatRepository;
    private final SessionRegistry sessionRegistry;
    private final UserStore userStore;

    public ChatService(
            ChatRepository chatRepository,
            SessionRegistry sessionRegistry,
            UserStore userStore
    ) {
        this.chatRepository = Objects.requireNonNull(chatRepository);
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
        this.userStore = Objects.requireNonNull(userStore);
    }

    public List<ChatSummary> findChatsForUser(String userId, String keyId) {
        return chatRepository.findChatsForUser(userId).stream()
                .filter(chat -> shouldIncludeChat(chat, userId, keyId))
                .map(chat -> {
                    User other = resolveUser(chat.otherUserId(userId));
                    String username = other == null ? chat.otherUserId(userId) : other.username();
                    return new ChatSummary(
                            chat.chatId(),
                            username,
                            chat.type()
                    );
                })
                .toList();
    }

    public List<User> findUsersForUser(String userId) {
        List<String> userIds = chatRepository.findChatsForUser(userId).stream()
                .map(chat -> chat.otherUserId(userId))
                .distinct()
                .toList();
        return userStore.findUsers(userIds);
    }

    public List<MessageRecord> findMessages(String userId, long chatId) {
        ChatRecord chat = requireChatParticipant(userId, chatId);
        requireChatType(chat, ChatType.DIRECT, "Use /private-chats/{chatId}/messages for PRIVATE chats");
        return chatRepository.findMessages(chat.chatId()).stream()
                .map(this::mapExternalMessage)
                .toList();
    }

    public ChatSummary createChat(String userId, String targetUsername) {
        User targetUser = userStore.findByUsername(targetUsername);
        if (targetUser == null) {
            throw new ChatException(HttpStatus.NOT_FOUND, "Target user was not found");
        }
        if (userId.equals(targetUser.userId())) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Cannot create chat with the same user");
        }

        ChatRecord chat = chatRepository.createChat(userId, null, targetUser.userId(), null, ChatType.DIRECT);
        return new ChatSummary(
                chat.chatId(),
                targetUser.username(),
                chat.type()
        );
    }

    public MessageDelivery sendMessage(String senderUserId, SendMessageCommand command) {
        if (command == null || command.chatId() == null || command.text() == null || command.text().isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "use {\"chatId\":123,\"text\":\"...\"}");
        }

        ChatRecord chat = requireChatParticipant(senderUserId, command.chatId());
        requireChatType(chat, ChatType.DIRECT, "Use PRIVATE_MESSAGE for PRIVATE chats");
        String recipientUserId = chat.otherUserId(senderUserId);

        MessageRecord storedMessage = chatRepository.appendMessage(chat.chatId(), senderUserId, command.text());
        return new MessageDelivery(recipientUserId, mapExternalMessage(storedMessage));
    }

    public String findChatRecipientUserId(String userId, long chatId) {
        return requireChatParticipant(userId, chatId).otherUserId(userId);
    }

    public PresenceEvent acceptPing(String userId) {
        User user = userStore.ping(userId);
        return new PresenceEvent("presence", user.username(), user.lastPingTime());
    }

    public List<String> findPresenceSubscriberUserIds(String userId) {
        return chatRepository.findChatsForUser(userId).stream()
                .map(chat -> chat.otherUserId(userId))
                .distinct()
                .toList();
    }

    public String usernameForUserId(String userId) {
        User user = resolveUser(userId);
        return user == null ? userId : user.username();
    }

    private ChatRecord requireChatParticipant(String userId, long chatId) {
        ChatRecord chat = chatRepository.findChat(chatId);
        if (chat == null || !chat.hasParticipant(userId)) {
            throw new ChatException(HttpStatus.NOT_FOUND, "Chat not found");
        }
        return chat;
    }

    private void requireChatType(ChatRecord chat, ChatType expectedType, String message) {
        if (chat.type() != expectedType) {
            throw new ChatException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean shouldIncludeChat(ChatRecord chat, String userId, String keyId) {
        if (chat.type() != ChatType.PRIVATE) {
            return true;
        }
        return keyId != null && !keyId.isBlank() && keyId.equals(chat.keyIdForUser(userId));
    }

    private MessageRecord mapExternalMessage(MessageRecord record) {
        User sender = resolveUser(record.senderUserId());
        String senderUsername = sender == null ? record.senderUserId() : sender.username();
        return new MessageRecord(record.chatId(), senderUsername, record.text(), record.timestamp());
    }

    private User resolveUser(String userReference) {
        User user = userStore.findUser(userReference);
        if (user != null) {
            return user;
        }
        return userStore.findByUsername(userReference);
    }
}
