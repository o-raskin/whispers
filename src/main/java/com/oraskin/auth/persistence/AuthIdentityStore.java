package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.UserIdentityRecord;

public interface AuthIdentityStore {

    UserIdentityRecord findByProviderSubject(String provider, String providerSubject);

    UserIdentityRecord findByUserId(String userId);

    UserIdentityRecord findByEmail(String email);

    void save(UserIdentityRecord identityRecord);
}
