package com.oraskin.chat;

import com.oraskin.connection.dto.MessageRecord;

import java.util.List;

public interface ChatRepository {

    ChatRecord createChat(String firstUserId, String secondUserId);

    ChatRecord findChat(String chatId);

    List<ChatRecord> findChatsForUser(String userId);

    MessageRecord appendMessage(String chatId, String senderUserId, String text);

    List<MessageRecord> findMessages(String chatId);
}
