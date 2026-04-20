package com.oraskin.chat.privatechat.service;

import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.PrivateChatKeyView;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.privatechat.value.PrivateChatView;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.connection.PrivateMessageDelivery;
import com.oraskin.connection.SendPrivateMessageCommand;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;

import java.util.List;
import java.util.Objects;

public final class PrivateChatService {

    private final ChatRepository chatRepository;
    private final UserStore userStore;
    private final PublicKeyService publicKeyService;

    public PrivateChatService(
            ChatRepository chatRepository,
            UserStore userStore,
            PublicKeyService publicKeyService
    ) {
        this.chatRepository = Objects.requireNonNull(chatRepository);
        this.userStore = Objects.requireNonNull(userStore);
        this.publicKeyService = Objects.requireNonNull(publicKeyService);
    }

    public ChatSummary createChat(String userId, String targetUsername, String keyId) {
        User targetUser = userStore.findByUsername(targetUsername);
        if (targetUser == null) {
            throw new ChatException(HttpStatus.NOT_FOUND, "Target user was not found");
        }
        if (userId.equals(targetUser.userId())) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Cannot create private chat with the same user");
        }

        PrivateChatKeyView currentUserKey = publicKeyService.requireKey(
                userId,
                keyId,
                "Register the current browser key before creating a PRIVATE chat"
        );
        PrivateChatKeyView counterpartKey = publicKeyService.requireLatestKey(
                targetUser.userId(),
                "Target user has not registered a browser key for PRIVATE chats"
        );

        ChatRecord chat = chatRepository.createChat(
                userId,
                currentUserKey.keyId(),
                targetUser.userId(),
                counterpartKey.keyId(),
                ChatType.PRIVATE
        );
        return new ChatSummary(chat.chatId(), targetUser.username(), chat.type());
    }

    public PrivateChatView getChat(String userId, long chatId, String keyId) {
        ChatRecord chat = requirePrivateChatParticipant(userId, chatId);
        requireBoundBrowserKey(chat, userId, keyId);
        User counterpart = resolveUser(chat.otherUserId(userId));
        String username = counterpart == null ? chat.otherUserId(userId) : counterpart.username();

        PrivateChatKeyView currentUserKey = publicKeyService.requireKey(
                userId,
                chat.keyIdForUser(userId),
                "Current browser key for this PRIVATE chat is not registered"
        );
        PrivateChatKeyView counterpartKey = publicKeyService.requireKey(
                chat.otherUserId(userId),
                chat.otherUserKeyId(userId),
                "Counterpart browser key for this PRIVATE chat is not registered"
        );

        return new PrivateChatView(chat.chatId(), username, chat.type(), currentUserKey, counterpartKey);
    }

    public List<PrivateMessageView> findMessages(String userId, long chatId, String keyId) {
        ChatRecord chat = requirePrivateChatParticipant(userId, chatId);
        requireBoundBrowserKey(chat, userId, keyId);
        return chatRepository.findPrivateMessages(chat.chatId()).stream()
                .map(this::mapExternalMessage)
                .toList();
    }

    public PrivateMessageDelivery sendMessage(String senderUserId, SendPrivateMessageCommand command) {
        if (command == null || command.chatId() == null || command.encryptedMessage() == null) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Use {\"type\":\"PRIVATE_MESSAGE\",\"chatId\":123,\"privateMessage\":{...}}");
        }

        EncryptedPrivateMessagePayload encryptedMessage = command.encryptedMessage();
        validateEncryptedMessage(encryptedMessage);

        ChatRecord chat = requirePrivateChatParticipant(senderUserId, command.chatId());
        String recipientUserId = chat.otherUserId(senderUserId);

        PrivateChatKeyView senderKey = publicKeyService.requireKey(
                senderUserId,
                encryptedMessage.senderKeyId(),
                "Register the current browser key before sending PRIVATE messages"
        );
        PrivateChatKeyView recipientKey = publicKeyService.requireKey(
                recipientUserId,
                encryptedMessage.recipientKeyId(),
                "Recipient browser key for this PRIVATE chat is not registered"
        );

        if (!senderKey.keyId().equals(chat.keyIdForUser(senderUserId))) {
            throw new ChatException(HttpStatus.CONFLICT, "PRIVATE chat is bound to another browser key for the sender");
        }
        if (!recipientKey.keyId().equals(chat.otherUserKeyId(senderUserId))) {
            throw new ChatException(HttpStatus.CONFLICT, "PRIVATE chat is bound to another browser key for the recipient");
        }

        PrivateMessageRecord storedMessage = chatRepository.appendPrivateMessage(chat.chatId(), senderUserId, encryptedMessage);
        return new PrivateMessageDelivery(recipientUserId, mapExternalMessage(storedMessage));
    }

    private ChatRecord requirePrivateChatParticipant(String userId, long chatId) {
        ChatRecord chat = chatRepository.findChat(chatId);
        if (chat == null || !chat.hasParticipant(userId)) {
            throw new ChatException(HttpStatus.NOT_FOUND, "Chat not found");
        }
        if (chat.type() != ChatType.PRIVATE) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Chat is not PRIVATE");
        }
        return chat;
    }

    private void requireBoundBrowserKey(ChatRecord chat, String userId, String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "keyId is required for PRIVATE chats");
        }
        if (!keyId.equals(chat.keyIdForUser(userId))) {
            throw new ChatException(HttpStatus.CONFLICT, "PRIVATE chat is bound to another browser key");
        }
        publicKeyService.requireKey(userId, keyId, "Current browser key for this PRIVATE chat is not registered");
    }

    private void validateEncryptedMessage(EncryptedPrivateMessagePayload encryptedMessage) {
        if (isBlank(encryptedMessage.protocolVersion())
                || isBlank(encryptedMessage.encryptionAlgorithm())
                || isBlank(encryptedMessage.keyWrapAlgorithm())
                || isBlank(encryptedMessage.ciphertext())
                || isBlank(encryptedMessage.nonce())
                || isBlank(encryptedMessage.senderKeyId())
                || isBlank(encryptedMessage.senderMessageKeyEnvelope())
                || isBlank(encryptedMessage.recipientKeyId())
                || isBlank(encryptedMessage.recipientMessageKeyEnvelope())) {
            throw new ChatException(
                    HttpStatus.BAD_REQUEST,
                    "PRIVATE messages require protocolVersion, encryptionAlgorithm, keyWrapAlgorithm, ciphertext, nonce, senderKeyId, senderMessageKeyEnvelope, recipientKeyId, and recipientMessageKeyEnvelope"
            );
        }
    }

    private PrivateMessageView mapExternalMessage(PrivateMessageRecord record) {
        User sender = resolveUser(record.senderUserId());
        String senderUsername = sender == null ? record.senderUserId() : sender.username();
        return new PrivateMessageView(
                record.chatId(),
                senderUsername,
                ChatType.PRIVATE,
                record.encryptedMessage(),
                record.timestamp()
        );
    }

    private User resolveUser(String userReference) {
        User user = userStore.findUser(userReference);
        if (user != null) {
            return user;
        }
        return userStore.findByUsername(userReference);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
