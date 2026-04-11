package com.oraskin.resource;

import com.oraskin.chat.service.ChatService;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;
import com.oraskin.user.data.domain.User;

import java.util.List;

@RestController("/users")
public final class UsersController {

    private final ChatService chatService;

    public UsersController(ChatService chatService) {
        this.chatService = chatService;
    }

    @RequestMapping(method = "GET")
    public List<User> getUsers(@RequestParam("userId") String userId) {
        return chatService.findUsersForUser(userId);
    }
}
