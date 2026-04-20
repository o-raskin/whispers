package com.oraskin.auth.oidc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OidcIdTokenClaims(
        String iss,
        String sub,
        String aud,
        long exp,
        long iat,
        String nonce,
        String email,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        String picture
) {
}
