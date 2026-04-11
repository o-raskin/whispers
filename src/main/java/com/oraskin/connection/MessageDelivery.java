package com.oraskin.connection;

import com.oraskin.chat.repository.entity.MessageRecord;

public record MessageDelivery(String recipientUserId, MessageRecord message) {
}
