package com.oraskin.auth.service;

import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.persistence.AuthIdentityStore;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;

import java.util.Objects;

public final class IdentityProvisioningService {

    private final AuthIdentityStore authIdentityStore;
    private final UserStore userStore;

    public IdentityProvisioningService(AuthIdentityStore authIdentityStore, UserStore userStore) {
        this.authIdentityStore = Objects.requireNonNull(authIdentityStore);
        this.userStore = Objects.requireNonNull(userStore);
    }

    public ProvisionedIdentity provision(ExternalUserIdentity externalUserIdentity) {
        UserIdentityRecord existingIdentity = authIdentityStore.findByProviderSubject(
                externalUserIdentity.provider(),
                externalUserIdentity.providerSubject()
        );

        String userId;
        if (existingIdentity != null) {
            userId = existingIdentity.userId();
            userStore.updateUser(userId, externalUserIdentity.username(), externalUserIdentity.firstName(), externalUserIdentity.lastName());
        } else {
            User existingByEmail = userStore.findByUsername(externalUserIdentity.username());
            if (existingByEmail != null) {
                userId = existingByEmail.userId();
                userStore.updateUser(userId, externalUserIdentity.username(), externalUserIdentity.firstName(), externalUserIdentity.lastName());
            } else {
                userId = userStore.createUser(
                        externalUserIdentity.username(),
                        externalUserIdentity.firstName(),
                        externalUserIdentity.lastName()
                );
            }
        }

        authIdentityStore.save(new UserIdentityRecord(
                userId,
                externalUserIdentity.provider(),
                externalUserIdentity.providerSubject(),
                externalUserIdentity.email(),
                externalUserIdentity.pictureUrl()
        ));
        return new ProvisionedIdentity(userId, externalUserIdentity);
    }

    public record ProvisionedIdentity(String userId, ExternalUserIdentity externalIdentity) {
    }
}
