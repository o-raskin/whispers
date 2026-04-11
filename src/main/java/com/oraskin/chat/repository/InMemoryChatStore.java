package com.oraskin.chat.repository;

import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryChatStore implements ChatRepository {

    private final Map<String, ChatMetadata> chatsById = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> messagesByChatId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> chatsByUserId = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryChatStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ChatRecord createChat(String firstUserId, String secondUserId) {
        String chatId = buildChatId(firstUserId, secondUserId);
        ChatMetadata metadata = new ChatMetadata(chatId, firstUserId, secondUserId);

        chatsById.putIfAbsent(chatId, metadata);
        chatsByUserId.computeIfAbsent(firstUserId, ignored -> ConcurrentHashMap.newKeySet()).add(chatId);
        chatsByUserId.computeIfAbsent(secondUserId, ignored -> ConcurrentHashMap.newKeySet()).add(chatId);
        messagesByChatId.computeIfAbsent(chatId, ignored -> Collections.synchronizedList(new ArrayList<>()));
        return chatsById.get(chatId).toRecord();
    }

    @Override
    public ChatRecord findChat(String chatId) {
        ChatMetadata chat = chatsById.get(chatId);
        return chat == null ? null : chat.toRecord();
    }

    @Override
    public List<ChatRecord> findChatsForUser(String userId) {
        Set<String> chatIds = chatsByUserId.getOrDefault(userId, Set.of());
        return chatIds.stream()
                .map(chatsById::get)
                .filter(chat -> chat != null)
                .sorted(Comparator.comparing(ChatMetadata::chatId))
                .map(ChatMetadata::toRecord)
                .toList();
    }

    @Override
    public MessageRecord appendMessage(String chatId, String senderUserId, String text) {
        ChatMessage message = new ChatMessage(chatId, senderUserId, text, Instant.now(clock).toString());
        List<ChatMessage> history = messagesByChatId.computeIfAbsent(chatId, ignored -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (history) {
            history.add(message);
        }
        return message.toRecord();
    }

    @Override
    public List<MessageRecord> findMessages(String chatId) {
        List<ChatMessage> history = messagesByChatId.getOrDefault(chatId, List.of());
        synchronized (history) {
            return history.stream().map(ChatMessage::toRecord).toList();
        }
    }

    public static String buildChatId(String firstUserId, String secondUserId) {
        return firstUserId.compareTo(secondUserId) <= 0
                ? firstUserId + "__" + secondUserId
                : secondUserId + "__" + firstUserId;
    }

    public record ChatMetadata(String chatId, String firstUserId, String secondUserId) {
        private ChatRecord toRecord() {
            return new ChatRecord(chatId, firstUserId, secondUserId);
        }
    }

    public record ChatMessage(String chatId, String senderUserId, String text, String timestamp) {
        private MessageRecord toRecord() {
            return new MessageRecord(chatId, senderUserId, text, timestamp);
        }
    }
}
