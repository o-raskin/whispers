package com.oraskin.user.data.persistence;

import com.oraskin.user.data.domain.User;

import java.util.Collection;
import java.util.List;

public interface UserStore {

    void remember(String username);

    User ping(String username);

    User findUser(String username);

    List<User> findUsers(Collection<String> usernames);
}
