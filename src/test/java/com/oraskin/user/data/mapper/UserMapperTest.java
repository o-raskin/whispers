package com.oraskin.user.data.mapper;

import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.entity.UserData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    @Test
    void toDomainMapsPersistenceRowsToDomainUsers() {
        UserMapper mapper = new UserMapper();
        UserData userData = new UserData("alice-id", "alice@example.com", "Alice", "Example", LocalDateTime.of(2026, 4, 21, 10, 15));

        assertThat(mapper.toDomain(userData))
                .isEqualTo(new User("alice-id", "alice@example.com", "Alice", "Example", LocalDateTime.of(2026, 4, 21, 10, 15)));
    }
}
