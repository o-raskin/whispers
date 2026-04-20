package com.oraskin.auth.persistence.entity;

public record UserIdentityRecord(
        String userId,
        String provider,
        String providerSubject,
        String email,
        String pictureUrl
) {
}
