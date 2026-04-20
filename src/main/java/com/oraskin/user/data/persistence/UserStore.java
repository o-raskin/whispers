package com.oraskin.user.data.persistence;

import com.oraskin.user.data.domain.User;

import java.util.Collection;
import java.util.List;

public interface UserStore {

    User ping(String userId);

    User findUser(String userId);

    List<User> findUsers(Collection<String> userIds);

    User findByUsername(String username);

    String createUser(String username, String firstName, String lastName);

    void updateUser(String userId, String username, String firstName, String lastName);
}
