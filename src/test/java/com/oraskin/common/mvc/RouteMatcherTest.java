package com.oraskin.common.mvc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteMatcherTest {

    @Test
    void matchHandlesRootPathsVariablesAndMismatches() {
        assertThat(RouteMatcher.match("/", "/")).isEqualTo(Map.of());
        assertThat(RouteMatcher.match("/private-chats/{chatId}", "/private-chats/42")).isEqualTo(Map.of("chatId", "42"));
        assertThat(RouteMatcher.match("/users/{userId}/chats/{chatId}", "/users/alice/chats/42"))
                .isEqualTo(Map.of("userId", "alice", "chatId", "42"));
        assertThat(RouteMatcher.match("/users", "/users/")).isEqualTo(Map.of());
        assertThat(RouteMatcher.match("/users/{userId}", "/messages/1")).isNull();
    }
}
