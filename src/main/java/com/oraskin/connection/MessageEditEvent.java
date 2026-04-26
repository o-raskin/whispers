package com.oraskin.connection;

import com.oraskin.chat.repository.entity.MessageRecord;

public record MessageEditEvent(String type, MessageRecord message) {
}
