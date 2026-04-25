package com.oraskin.chat.repository;

import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.value.ChatType;

import java.util.List;

public interface ChatRepository {

    ChatRecord createChat(
            String firstUserId,
            String firstUserKeyId,
            String secondUserId,
            String secondUserKeyId,
            ChatType chatType
    );

    ChatRecord findChat(long chatId);

    List<ChatRecord> findChatsForUser(String userId);

    MessageRecord appendMessage(long chatId, String senderUserId, String text);

    MessageRecord findMessage(long messageId);

    List<MessageRecord> findMessages(long chatId);

    void deleteMessage(long messageId);

    PrivateMessageRecord appendPrivateMessage(long chatId, String senderUserId, EncryptedPrivateMessagePayload encryptedMessage);

    List<PrivateMessageRecord> findPrivateMessages(long chatId);

}
