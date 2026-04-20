package com.oraskin.user.data.mapper;

import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.entity.UserData;

public final class UserMapper {

    public User toDomain(UserData userData) {
        return new User(
                userData.getUserId(),
                userData.getUsername(),
                userData.getFirstName(),
                userData.getLastName(),
                userData.getLastPingTime()
        );
    }
}
