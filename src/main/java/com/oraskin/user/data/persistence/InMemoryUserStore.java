package com.oraskin.user.data.persistence;

import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.mapper.UserMapper;
import com.oraskin.user.data.persistence.entity.UserData;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserStore implements UserStore {

    private final Map<String, UserData> users = new ConcurrentHashMap<>();
    private final Clock clock;
    private final UserMapper userMapper = new UserMapper();

    public InMemoryUserStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void remember(String username) {
        users.computeIfAbsent(username, key -> new UserData(username, null, null, null));
    }

    @Override
    public User ping(String username) {
        UserData updated = users.compute(username, (key, current) -> {
            UserData existing = current == null ? new UserData(username, null, null, null) : current;
            return new UserData(
                    existing.getUsername(),
                    existing.getFirstName(),
                    existing.getLastName(),
                    LocalDateTime.now(clock)
            );
        });
        return userMapper.toDomain(updated);
    }

    @Override
    public User findUser(String username) {
        UserData userData = users.computeIfAbsent(username, key -> new UserData(username, null, null, null));
        return userMapper.toDomain(userData);
    }

    @Override
    public List<User> findUsers(Collection<String> usernames) {
        return usernames.stream()
                .map(this::findUser)
                .toList();
    }
}
