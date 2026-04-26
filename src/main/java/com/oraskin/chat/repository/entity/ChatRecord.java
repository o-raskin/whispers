package com.oraskin.chat.repository.entity;

import com.oraskin.chat.value.ChatType;

import java.util.List;

public record ChatRecord(
        long chatId,
        String firstUserId,
        String secondUserId,
        ChatType type,
        String firstUserKeyId,
        String secondUserKeyId
) {

    public String otherUserId(String currentUserId) {
        return currentUserId.equals(firstUserId) ? secondUserId : firstUserId;
    }

    public boolean hasParticipant(String userId) {
        return userId.equals(firstUserId) || userId.equals(secondUserId);
    }

    public List<String> participantUserIds() {
        return List.of(firstUserId, secondUserId);
    }

    public String keyIdForUser(String userId) {
        return userId.equals(firstUserId) ? firstUserKeyId : secondUserKeyId;
    }

    public String otherUserKeyId(String currentUserId) {
        return currentUserId.equals(firstUserId) ? secondUserKeyId : firstUserKeyId;
    }
}
