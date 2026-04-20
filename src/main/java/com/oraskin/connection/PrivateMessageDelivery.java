package com.oraskin.connection;

import com.oraskin.chat.privatechat.value.PrivateMessageView;

public record PrivateMessageDelivery(String recipientUserId, PrivateMessageView message) {
}
