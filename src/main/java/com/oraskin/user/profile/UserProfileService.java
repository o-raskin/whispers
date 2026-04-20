package com.oraskin.user.profile;

import com.oraskin.auth.persistence.AuthIdentityStore;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;

import java.util.Objects;

public final class UserProfileService {

    private final UserStore userStore;
    private final AuthIdentityStore authIdentityStore;

    public UserProfileService(UserStore userStore, AuthIdentityStore authIdentityStore) {
        this.userStore = Objects.requireNonNull(userStore);
        this.authIdentityStore = Objects.requireNonNull(authIdentityStore);
    }

    public UserProfileView findProfile(String userReference) {
        User user = userStore.findUser(userReference);
        if (user == null) {
            user = userStore.findByUsername(userReference);
        }
        if (user == null) {
            throw new ChatException(HttpStatus.NOT_FOUND, "User not found");
        }

        UserIdentityRecord identityRecord = authIdentityStore.findByUserId(user.userId());
        if (identityRecord == null) {
            identityRecord = authIdentityStore.findByEmail(user.username());
        }

        return new UserProfileView(
                user.userId(),
                user.username(),
                user.firstName(),
                user.lastName(),
                identityRecord == null ? null : identityRecord.pictureUrl(),
                identityRecord == null ? null : identityRecord.provider()
        );
    }
}
