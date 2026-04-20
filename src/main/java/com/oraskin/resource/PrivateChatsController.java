package com.oraskin.resource;

import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.PrivateChatView;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;

import java.util.List;

@RestController("/private-chats")
public final class PrivateChatsController {

    private final PrivateChatService privateChatService;

    public PrivateChatsController(PrivateChatService privateChatService) {
        this.privateChatService = privateChatService;
    }

    @RequestMapping(method = "POST")
    public ChatSummary createPrivateChat(
            AuthenticatedUser user,
            @RequestParam("targetUserId") String targetUserId,
            @RequestParam("keyId") String keyId
    ) {
        return privateChatService.createChat(user.userId(), targetUserId, keyId);
    }

    @RequestMapping(method = "GET", value = "/{chatId}")
    public PrivateChatView getPrivateChat(
            AuthenticatedUser user,
            @PathVariable("chatId") long chatId,
            @RequestParam("keyId") String keyId
    ) {
        return privateChatService.getChat(user.userId(), chatId, keyId);
    }

    @RequestMapping(method = "GET", value = "/{chatId}/messages")
    public List<PrivateMessageView> getMessages(
            AuthenticatedUser user,
            @PathVariable("chatId") long chatId,
            @RequestParam("keyId") String keyId
    ) {
        return privateChatService.findMessages(user.userId(), chatId, keyId);
    }
}
