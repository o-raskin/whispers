package com.oraskin.chat.key.persistence;

import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;

public interface PrivateChatKeyStore {

    PrivateChatKeyRecord upsertKey(String userId, String keyId, String publicKey, String algorithm, String format);

    PrivateChatKeyRecord findKey(String userId, String keyId);

    PrivateChatKeyRecord findLatestKey(String userId);
}
