package com.oraskin.auth.service;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.user.data.domain.User;
import org.junit.jupiter.api.Test;

import static com.oraskin.auth.AuthTestSupport.externalIdentity;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityProvisioningServiceTest {

    @Test
    void provisionReusesExistingIdentityAndEmailMatchesBeforeCreatingUsers() {
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        userStore.add(new User("existing-id", "old@example.com", "Old", "Name", null));
        authIdentityStore.save(new UserIdentityRecord("existing-id", "google", "subject-1", "old@example.com", "https://example.com/old.png"));
        IdentityProvisioningService service = new IdentityProvisioningService(authIdentityStore, userStore);

        IdentityProvisioningService.ProvisionedIdentity existingIdentity = service.provision(
                externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/picture.png")
        );

        assertThat(existingIdentity.userId()).isEqualTo("existing-id");
        assertThat(userStore.updatedUserIds()).containsExactly("existing-id");

        AuthTestSupport.RecordingUserStore emailMatchedUserStore = new AuthTestSupport.RecordingUserStore();
        emailMatchedUserStore.add(new User("email-id", "alice@example.com", "Alice", "Example", null));
        IdentityProvisioningService emailMatchedService = new IdentityProvisioningService(
                new AuthTestSupport.InMemoryAuthIdentityStore(),
                emailMatchedUserStore
        );

        IdentityProvisioningService.ProvisionedIdentity emailMatchedIdentity = emailMatchedService.provision(
                externalIdentity("google", "subject-2", "alice@example.com", "Alice", "Example", "https://example.com/picture.png")
        );

        assertThat(emailMatchedIdentity.userId()).isEqualTo("email-id");
        assertThat(emailMatchedUserStore.updatedUserIds()).containsExactly("email-id");
    }

    @Test
    void provisionCreatesNewUsersWhenNoIdentityMatchExists() {
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        IdentityProvisioningService service = new IdentityProvisioningService(new AuthTestSupport.InMemoryAuthIdentityStore(), userStore);

        IdentityProvisioningService.ProvisionedIdentity provisionedIdentity = service.provision(
                externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/picture.png")
        );

        assertThat(userStore.createdUserIds()).hasSize(1);
        assertThat(provisionedIdentity.userId()).isEqualTo(userStore.createdUserIds().getFirst());
        assertThat(userStore.findUser(provisionedIdentity.userId()).username()).isEqualTo("alice@example.com");
    }
}
