package com.oraskin.chat.service;

import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.connection.SendMessageCommand;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.session.persistence.SessionRegistry;

import java.util.List;
import java.util.Objects;

public final class ChatService {

    private final ChatRepository chatRepository;
    private final SessionRegistry sessionRegistry;
    private final UserStore userStore;

    public ChatService(ChatRepository chatRepository, SessionRegistry sessionRegistry, UserStore userStore) {
        this.chatRepository = Objects.requireNonNull(chatRepository);
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
        this.userStore = Objects.requireNonNull(userStore);
    }

    public List<ChatSummary> findChatsForUser(String userId) {
        return chatRepository.findChatsForUser(userId).stream()
                .map(chat -> new ChatSummary(chat.chatId(), chat.otherUserId(userId)))
                .toList();
    }

    public List<User> findUsersForUser(String userId) {
        List<String> usernames = chatRepository.findChatsForUser(userId).stream()
                .map(chat -> chat.otherUserId(userId))
                .distinct()
                .toList();
        return userStore.findUsers(usernames);
    }

    public List<MessageRecord> findMessages(String userId, long chatId) {
        ChatRecord chat = requireChatParticipant(userId, chatId);
        return chatRepository.findMessages(chat.chatId());
    }

    public ChatSummary createChat(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Cannot create chat with the same user");
        }
        if (!sessionRegistry.isConnected(targetUserId)) {
            throw new ChatException(HttpStatus.CONFLICT, "Target user is not connected");
        }

        ChatRecord chat = chatRepository.createChat(userId, targetUserId);
        userStore.remember(targetUserId);
        return new ChatSummary(chat.chatId(), chat.otherUserId(userId));
    }

    public MessageDelivery sendMessage(String senderUserId, SendMessageCommand command) {
        if (command == null || command.chatId() == null
                || command.text() == null || command.text().isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "use {\"chatId\":123,\"text\":\"...\"}");
        }

        ChatRecord chat = requireChatParticipant(senderUserId, command.chatId());
        String recipientUserId = chat.otherUserId(senderUserId);
        if (!sessionRegistry.isConnected(recipientUserId)) {
            throw new ChatException(HttpStatus.CONFLICT, "user '" + recipientUserId + "' is not connected.");
        }

        MessageRecord storedMessage = chatRepository.appendMessage(chat.chatId(), senderUserId, command.text());
        return new MessageDelivery(recipientUserId, storedMessage);
    }

    public String findChatRecipientUserId(String userId, long chatId) {
        return requireChatParticipant(userId, chatId).otherUserId(userId);
    }

    public PresenceEvent acceptPing(String userId) {
        var user = userStore.ping(userId);
        return new PresenceEvent("presence", user.username(), user.lastPingTime());
    }

    public List<String> findPresenceSubscriberUserIds(String userId) {
        return chatRepository.findChatsForUser(userId).stream()
                .map(chat -> chat.otherUserId(userId))
                .distinct()
                .toList();
    }

    private ChatRecord requireChatParticipant(String userId, long chatId) {
        ChatRecord chat = chatRepository.findChat(chatId);
        if (chat == null || !chat.hasParticipant(userId)) {
            throw new ChatException(HttpStatus.NOT_FOUND, "Chat not found");
        }
        return chat;
    }
}
