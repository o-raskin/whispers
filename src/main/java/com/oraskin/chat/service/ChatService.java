package com.oraskin.chat.service;

import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.connection.MessageDeleteEvent;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.connection.SendMessageCommand;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.session.persistence.SessionRegistry;
import com.oraskin.websocket.WebSocketMessageSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChatService {

    private final ChatRepository chatRepository;
    private final SessionRegistry sessionRegistry;
    private final UserStore userStore;
    private final WebSocketMessageSender webSocketMessageSender;

    public ChatService(
            ChatRepository chatRepository,
            SessionRegistry sessionRegistry,
            UserStore userStore,
            WebSocketMessageSender webSocketMessageSender
    ) {
        this.chatRepository = Objects.requireNonNull(chatRepository);
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
        this.userStore = Objects.requireNonNull(userStore);
        this.webSocketMessageSender = Objects.requireNonNull(webSocketMessageSender);
    }

    public List<ChatSummary> findChatsForUser(String userId, String keyId) {
        List<ChatRecord> visibleChats = chatRepository.findChatsForUser(userId).stream()
                .filter(chat -> shouldIncludeChat(chat, userId, keyId))
                .toList();
        Map<String, User> usersById = loadUsersById(otherUserIds(visibleChats, userId));

        return visibleChats.stream()
                .map(chat -> new ChatSummary(
                        chat.chatId(),
                        usernameForReference(chat.otherUserId(userId), usersById),
                        chat.type()
                ))
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
        List<MessageRecord> storedMessages = chatRepository.findMessages(chat.chatId());
        Map<String, User> usersById = loadUsersById(senderUserIds(storedMessages));
        return storedMessages.stream()
                .map(record -> mapExternalMessage(record, usersById.get(record.senderUserId())))
                .toList();
    }

    public void deleteMessage(String userId, long messageId) {
        MessageRecord storedMessage = chatRepository.findMessage(messageId);
        if (storedMessage == null) {
            throw new ChatException(HttpStatus.NOT_FOUND, "Message not found");
        }

        ChatRecord chat = requireChatParticipant(userId, storedMessage.chatId());
        requireChatType(chat, ChatType.DIRECT, "Use /private-chats/{chatId}/messages for PRIVATE chats");
        if (!storedMessage.senderUserId().equals(userId)) {
            throw new ChatException(HttpStatus.FORBIDDEN, "Only message owner can delete it");
        }

        chatRepository.deleteMessage(messageId);
        webSocketMessageSender.sendToUsers(
                List.of(chat.firstUserId(), chat.secondUserId()),
                new MessageDeleteEvent("MESSAGE_DELETE", chat.chatId(), messageId)
        );
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
        return mapExternalMessage(record, resolveUser(record.senderUserId()));
    }

    private MessageRecord mapExternalMessage(MessageRecord record, User sender) {
        String senderUsername = sender == null ? record.senderUserId() : sender.username();
        return new MessageRecord(record.messageId(), record.chatId(), senderUsername, record.text(), record.timestamp());
    }

    private List<String> otherUserIds(List<ChatRecord> chats, String currentUserId) {
        List<String> userIds = new ArrayList<>();
        for (ChatRecord chat : chats) {
            userIds.add(chat.otherUserId(currentUserId));
        }
        return userIds;
    }

    private List<String> senderUserIds(List<MessageRecord> messages) {
        List<String> userIds = new ArrayList<>();
        for (MessageRecord message : messages) {
            userIds.add(message.senderUserId());
        }
        return userIds;
    }

    private Map<String, User> loadUsersById(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, User> usersById = new HashMap<>();
        for (User user : userStore.findUsers(userIds)) {
            usersById.put(user.userId(), user);
        }
        return usersById;
    }

    private String usernameForReference(String userId, Map<String, User> usersById) {
        User user = usersById.get(userId);
        return user == null ? userId : user.username();
    }

    private User resolveUser(String userReference) {
        User user = userStore.findUser(userReference);
        if (user != null) {
            return user;
        }
        return userStore.findByUsername(userReference);
    }
}
