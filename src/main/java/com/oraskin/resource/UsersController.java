package com.oraskin.resource;

import com.oraskin.chat.service.ChatService;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RestController;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.profile.UserProfileService;
import com.oraskin.user.profile.UserProfileView;

import java.util.List;

@RestController("/users")
public final class UsersController {

    private final ChatService chatService;
    private final UserProfileService userProfileService;

    public UsersController(ChatService chatService, UserProfileService userProfileService) {
        this.chatService = chatService;
        this.userProfileService = userProfileService;
    }

    @RequestMapping(method = "GET")
    public List<User> getUsers(AuthenticatedUser user) {
        return chatService.findUsersForUser(user.userId());
    }

    @RequestMapping(method = "GET", value = "/{userId}")
    public UserProfileView getUserProfile(
            AuthenticatedUser authenticatedUser,
            @PathVariable("userId") String userId
    ) {
        return userProfileService.findProfile(userId);
    }
}
