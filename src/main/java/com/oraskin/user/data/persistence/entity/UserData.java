package com.oraskin.user.data.persistence.entity;


import java.time.LocalDateTime;
import java.util.Objects;

public class UserData {

    private final String username;
    private final String firstName;
    private final String lastName;
    private final LocalDateTime lastPingTime;

    public UserData(String username, String firstName, String lastName, LocalDateTime lastPingTime) {
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.lastPingTime = lastPingTime;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDateTime getLastPingTime() {
        return lastPingTime;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserData userData)) return false;
        return Objects.equals(username, userData.username);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(username);
    }
}
