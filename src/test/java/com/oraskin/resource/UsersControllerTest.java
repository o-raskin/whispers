package com.oraskin.resource;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.service.ChatService;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.profile.UserProfileService;
import com.oraskin.user.profile.UserProfileView;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static com.oraskin.chat.TestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class UsersControllerTest {

    @Test
    void getUsersAndUserProfileDelegateToServiceLayer() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new InMemorySessionRegistry(), userStore, new NoopWebSocketMessageSender());
        chatService.createChat("alice-id", "bob@example.com");
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        authIdentityStore.save(new UserIdentityRecord("bob-id", "google", "subject-1", "bob@example.com", "https://example.com/bob.png"));
        UsersController controller = new UsersController(chatService, new UserProfileService(userStore, authIdentityStore));

        List<User> users = controller.getUsers(authenticatedUser("alice-id", "alice@example.com", "hash"));
        UserProfileView profile = controller.getUserProfile(authenticatedUser("alice-id", "alice@example.com", "hash"), "bob-id");

        assertThat(users).extracting(User::username).containsExactly("bob@example.com");
        assertThat(profile.username()).isEqualTo("bob@example.com");
        assertThat(profile.profileUrl()).isEqualTo("https://example.com/bob.png");
    }
}
