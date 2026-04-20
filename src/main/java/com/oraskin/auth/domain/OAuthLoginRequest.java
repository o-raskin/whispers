package com.oraskin.auth.domain;

public record OAuthLoginRequest(
        String code,
        String redirectUri,
        String codeVerifier,
        String nonce
) {
}
