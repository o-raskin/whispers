package com.oraskin.auth.domain;

public record AuthUserProfile(
        String userId,
        String username,
        String email,
        String displayName,
        String firstName,
        String lastName,
        String pictureUrl,
        String provider
) {
}
