package com.oraskin.user.profile;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.user.data.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserProfileServiceTest {

    @Test
    void findProfileUsesLinkedIdentityAndFallsBackToEmailLookup() {
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        userStore.add(new User("alice-id", "alice@example.com", "Alice", "Example", null));
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        UserProfileService userProfileService = new UserProfileService(userStore, authIdentityStore);

        authIdentityStore.save(new UserIdentityRecord("alice-id", "google", "subject-1", "alice@example.com", "https://example.com/alice.png"));
        UserProfileView linkedIdentityProfile = userProfileService.findProfile("alice-id");

        assertEquals("alice-id", linkedIdentityProfile.userId());
        assertEquals("https://example.com/alice.png", linkedIdentityProfile.profileUrl());
        assertEquals("google", linkedIdentityProfile.provider());

        AuthTestSupport.RecordingUserStore emailFallbackStore = new AuthTestSupport.RecordingUserStore();
        emailFallbackStore.add(new User("bob-id", "bob@example.com", "Bob", "Example", null));
        AuthTestSupport.InMemoryAuthIdentityStore emailFallbackIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        emailFallbackIdentityStore.save(new UserIdentityRecord("other-id", "google", "subject-2", "bob@example.com", "https://example.com/bob.png"));
        UserProfileService emailFallbackService = new UserProfileService(emailFallbackStore, emailFallbackIdentityStore);

        UserProfileView emailFallbackProfile = emailFallbackService.findProfile("bob@example.com");

        assertEquals("bob-id", emailFallbackProfile.userId());
        assertEquals("https://example.com/bob.png", emailFallbackProfile.profileUrl());
        assertEquals("google", emailFallbackProfile.provider());
    }

    @Test
    void findProfileReturnsNullIdentityFieldsWhenNoLinkedIdentityExists() {
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        userStore.add(new User("alice-id", "alice@example.com", "Alice", "Example", null));
        UserProfileService userProfileService = new UserProfileService(userStore, new AuthTestSupport.InMemoryAuthIdentityStore());

        UserProfileView profileView = userProfileService.findProfile("alice-id");

        assertEquals("alice-id", profileView.userId());
        assertNull(profileView.profileUrl());
        assertNull(profileView.provider());
    }

    @Test
    void findProfileRejectsUnknownUsers() {
        UserProfileService userProfileService = new UserProfileService(
                new AuthTestSupport.RecordingUserStore(),
                new AuthTestSupport.InMemoryAuthIdentityStore()
        );

        ChatException exception = assertThrows(ChatException.class, () -> userProfileService.findProfile("missing-user"));

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
        assertEquals("User not found", exception.getMessage());
    }
}
