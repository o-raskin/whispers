package com.oraskin.chat;

import com.oraskin.connection.dto.MessageDelivery;
import com.oraskin.connection.dto.MessageRecord;
import com.oraskin.connection.dto.SendMessageCommand;
import com.oraskin.user.ClientSession;
import com.oraskin.user.SessionRegistry;

import java.util.List;
import java.util.Objects;

public final class ChatService {

    private final ChatRepository chatRepository;
    private final SessionRegistry sessionRegistry;

    public ChatService(ChatRepository chatRepository, SessionRegistry sessionRegistry) {
        this.chatRepository = Objects.requireNonNull(chatRepository);
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
    }

    public boolean registerSession(String userId, ClientSession session) {
        return sessionRegistry.register(userId, session);
    }

    public void terminateSession(String userId) {
        sessionRegistry.remove(userId);
    }

    public ClientSession findSession(String userId) {
        return sessionRegistry.findSession(userId);
    }

    public List<ChatSummary> findChatsForUser(String userId) {
        return chatRepository.findChatsForUser(userId).stream()
                .map(chat -> new ChatSummary(chat.chatId(), chat.otherUserId(userId)))
                .toList();
    }

    public List<MessageRecord> findMessages(String userId, String chatId) {
        ChatRecord chat = requireChatParticipant(userId, chatId);
        return chatRepository.findMessages(chat.chatId());
    }

    public ChatSummary createChat(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new ChatException(400, "Cannot create chat with the same user");
        }
        if (!sessionRegistry.isConnected(targetUserId)) {
            throw new ChatException(409, "Target user is not connected");
        }

        ChatRecord chat = chatRepository.createChat(userId, targetUserId);
        return new ChatSummary(chat.chatId(), chat.otherUserId(userId));
    }

    public MessageDelivery sendMessage(String senderUserId, SendMessageCommand command) {
        if (command == null || command.chatId() == null || command.chatId().isBlank()
                || command.text() == null || command.text().isBlank()) {
            throw new ChatException(400, "use {\"chatId\":\"...\",\"text\":\"...\"}");
        }

        ChatRecord chat = requireChatParticipant(senderUserId, command.chatId());
        String recipientUserId = chat.otherUserId(senderUserId);
        if (!sessionRegistry.isConnected(recipientUserId)) {
            throw new ChatException(409, "user '" + recipientUserId + "' is not connected.");
        }

        MessageRecord storedMessage = chatRepository.appendMessage(chat.chatId(), senderUserId, command.text());
        return new MessageDelivery(recipientUserId, storedMessage);
    }

    private ChatRecord requireChatParticipant(String userId, String chatId) {
        ChatRecord chat = chatRepository.findChat(chatId);
        if (chat == null || !chat.hasParticipant(userId)) {
            throw new ChatException(404, "Chat not found");
        }
        return chat;
    }
}
