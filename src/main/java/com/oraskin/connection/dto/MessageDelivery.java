package com.oraskin.connection.dto;

public record MessageDelivery(String recipientUserId, MessageRecord message) {
}
