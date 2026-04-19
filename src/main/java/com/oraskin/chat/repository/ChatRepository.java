package com.oraskin.chat.repository;

import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;

import java.util.List;

public interface ChatRepository {

    ChatRecord createChat(String firstUserId, String secondUserId);

    ChatRecord findChat(long chatId);

    List<ChatRecord> findChatsForUser(String userId);

    MessageRecord appendMessage(long chatId, String senderUserId, String text);

    List<MessageRecord> findMessages(long chatId);
}
