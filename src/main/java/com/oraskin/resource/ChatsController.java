package com.oraskin.resource;

import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;

import java.util.List;

@RestController("/chats")
public final class ChatsController {

    private final ChatService chatService;

    public ChatsController(ChatService chatService) {
        this.chatService = chatService;
    }

    @RequestMapping(method = "GET")
    public List<ChatSummary> getChats(@RequestParam("userId") String userId) {
        return chatService.findChatsForUser(userId);
    }

    @RequestMapping(method = "POST")
    public ChatSummary createChat(
            @RequestParam("userId") String userId,
            @RequestParam("targetUserId") String targetUserId
    ) {
        return chatService.createChat(userId, targetUserId);
    }
}
