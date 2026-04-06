package com.oraskin.connection.util;

import com.oraskin.chat.ChatSummary;
import com.oraskin.connection.dto.MessageRecord;
import com.oraskin.connection.dto.SendMessageCommand;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonCodec {

    private JsonCodec() {
    }

    public static String error(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
    }

    public static String chatSummary(ChatSummary summary) {
        return "{\"chatId\":\"" + escape(summary.chatId()) + "\",\"username\":\"" + escape(summary.username()) + "\"}";
    }

    public static String chatSummaries(List<ChatSummary> chats) {
        return join(chats.stream().map(JsonCodec::chatSummary).toList());
    }

    public static String message(MessageRecord message) {
        return "{\"chatId\":\"" + escape(message.chatId()) + "\","
                + "\"senderUserId\":\"" + escape(message.senderUserId()) + "\","
                + "\"text\":\"" + escape(message.text()) + "\","
                + "\"timestamp\":\"" + escape(message.timestamp()) + "\"}";
    }

    public static String messages(List<MessageRecord> messages) {
        return join(messages.stream().map(JsonCodec::message).toList());
    }

    public static SendMessageCommand parseSendMessage(String raw) {
        String chatId = extractStringValue(raw, "chatId");
        String text = extractStringValue(raw, "text");
        if (chatId == null || text == null) {
            return null;
        }
        return new SendMessageCommand(chatId, text);
    }

    private static String join(List<String> values) {
        return "[" + String.join(",", values) + "]";
    }

    private static String extractStringValue(String raw, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
