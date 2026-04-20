package com.oraskin.auth.domain;

public record ExternalUserIdentity(
        String provider,
        String providerSubject,
        String email,
        String username,
        String displayName,
        String firstName,
        String lastName,
        String pictureUrl
) {
}
