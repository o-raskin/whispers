package com.oraskin.chat;

public record ChatRecord(String chatId, String firstUserId, String secondUserId) {

    public String otherUserId(String currentUserId) {
        return currentUserId.equals(firstUserId) ? secondUserId : firstUserId;
    }

    public boolean hasParticipant(String userId) {
        return userId.equals(firstUserId) || userId.equals(secondUserId);
    }
}
