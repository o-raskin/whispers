package com.oraskin.common.auth;

public record AuthenticatedUser(
        String userId,
        String username,
        String provider,
        String providerSubject,
        String email,
        String displayName,
        String firstName,
        String lastName,
        String pictureUrl,
        String accessTokenHash
) {
}
