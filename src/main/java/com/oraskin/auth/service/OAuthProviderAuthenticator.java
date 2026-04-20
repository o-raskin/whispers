package com.oraskin.auth.service;

import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.domain.OAuthLoginRequest;

public interface OAuthProviderAuthenticator {

    String provider();

    ExternalUserIdentity authenticate(OAuthLoginRequest request);
}
