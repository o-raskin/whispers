package com.oraskin.chat.privatechat.value;

import com.oraskin.chat.key.value.PrivateChatKeyView;
import com.oraskin.chat.value.ChatType;

public record PrivateChatView(
        long chatId,
        String username,
        ChatType type,
        PrivateChatKeyView currentUserKey,
        PrivateChatKeyView counterpartKey
) {
}
