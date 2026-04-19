package com.oraskin.resource;

import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;

import java.util.List;

@RestController("/messages")
public final class MessagesController {

    private final ChatService chatService;

    public MessagesController(ChatService chatService) {
        this.chatService = chatService;
    }

    @RequestMapping(method = "GET")
    public List<MessageRecord> getMessages(
            @RequestParam("userId") String userId,
            @RequestParam("chatId") long chatId
    ) {
        return chatService.findMessages(userId, chatId);
    }
}
