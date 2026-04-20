package com.oraskin.auth.oidc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OidcJwkSet(List<OidcJwk> keys) {
}
