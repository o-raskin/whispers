package com.oraskin.user.profile;

public record UserProfileView(
        String userId,
        String username,
        String firstName,
        String lastName,
        String profileUrl,
        String provider
) {
}
