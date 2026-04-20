package com.oraskin.connection;

import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;

public record SendPrivateMessageCommand(Long chatId, EncryptedPrivateMessagePayload encryptedMessage) {
}
