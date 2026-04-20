package com.oraskin.auth.oidc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OidcJwk(
        String kid,
        String kty,
        String alg,
        String use,
        String n,
        String e
) {
}
