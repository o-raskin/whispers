package com.oraskin.auth;

import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.auth.persistence.AccessTokenStore;
import com.oraskin.auth.persistence.AuthIdentityStore;
import com.oraskin.auth.persistence.RefreshTokenStore;
import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.auth.service.OAuthProviderAuthenticator;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.QueryParams;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static AuthConfig authConfig() {
        return new AuthConfig(
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                "whispers",
                URI.create("http://localhost:5173/")
        );
    }

    public static AuthenticatedUser authenticatedUser(String userId, String username, String accessTokenHash) {
        return new AuthenticatedUser(
                userId,
                username,
                "google",
                "google-subject",
                username,
                username,
                "Alice",
                "Example",
                "https://example.com/picture.png",
                accessTokenHash
        );
    }

    public static ExternalUserIdentity externalIdentity(
            String provider,
            String providerSubject,
            String email,
            String firstName,
            String lastName,
            String pictureUrl
    ) {
        return new ExternalUserIdentity(
                provider,
                providerSubject,
                email,
                email,
                email,
                firstName,
                lastName,
                pictureUrl
        );
    }

    public static OAuthLoginRequest oauthLoginRequest() {
        return new OAuthLoginRequest(
                "authorization-code",
                "http://localhost:5173/auth/callback/google",
                "code-verifier",
                "nonce"
        );
    }

    public static HttpRequest request(String method, String target, Map<String, String> headers) {
        return new HttpRequest(method, URI.create(target).getPath(), QueryParams.fromTarget(target), headers, null, null);
    }

    public static User user(String userId, String username) {
        return new User(userId, username, null, null, null);
    }

    public static final class RecordingAccessTokenStore implements AccessTokenStore {

        private final Map<String, StoredAccessToken> createdTokensByHash = new LinkedHashMap<>();
        private final Map<String, AuthenticatedUser> activeUsersByHash = new LinkedHashMap<>();
        private final Map<String, Instant> revokedAtByHash = new LinkedHashMap<>();

        @Override
        public void createForUser(String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt) {
            createdTokensByHash.put(tokenHash, new StoredAccessToken(tokenHash, userId, provider, providerSubject, issuedAt, expiresAt));
        }

        @Override
        public AuthenticatedUser findActiveUserByTokenHash(String tokenHash, Instant now) {
            return activeUsersByHash.get(tokenHash);
        }

        @Override
        public void revoke(String tokenHash, Instant revokedAt) {
            revokedAtByHash.put(tokenHash, revokedAt);
        }

        public void activate(String tokenHash, AuthenticatedUser user) {
            activeUsersByHash.put(tokenHash, user);
        }

        public StoredAccessToken createdToken(String tokenHash) {
            return createdTokensByHash.get(tokenHash);
        }

        public Map<String, StoredAccessToken> createdTokensByHash() {
            return createdTokensByHash;
        }

        public Map<String, Instant> revokedAtByHash() {
            return revokedAtByHash;
        }
    }

    public static final class RecordingRefreshTokenStore implements RefreshTokenStore {

        private final Map<String, StoredRefreshSession> createdSessionsById = new LinkedHashMap<>();
        private final Map<String, String> sessionIdByTokenHash = new LinkedHashMap<>();
        private final Map<String, Instant> revokedAtBySessionId = new LinkedHashMap<>();

        @Override
        public void create(String sessionId, String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt) {
            createdSessionsById.put(
                    sessionId,
                    new StoredRefreshSession(sessionId, tokenHash, userId, provider, providerSubject, issuedAt, expiresAt, null)
            );
            sessionIdByTokenHash.put(tokenHash, sessionId);
        }

        @Override
        public RefreshSessionRecord findActiveByTokenHash(String tokenHash, Instant now) {
            String sessionId = sessionIdByTokenHash.get(tokenHash);
            if (sessionId == null) {
                return null;
            }
            StoredRefreshSession storedSession = createdSessionsById.get(sessionId);
            if (storedSession == null || storedSession.revokedAt() != null || !storedSession.expiresAt().isAfter(now)) {
                return null;
            }
            return storedSession.toRecord();
        }

        @Override
        public void revokeSession(String sessionId, Instant revokedAt) {
            StoredRefreshSession current = createdSessionsById.get(sessionId);
            if (current == null) {
                return;
            }
            createdSessionsById.put(
                    sessionId,
                    new StoredRefreshSession(
                            current.sessionId(),
                            current.tokenHash(),
                            current.userId(),
                            current.provider(),
                            current.providerSubject(),
                            current.issuedAt(),
                            current.expiresAt(),
                            revokedAt
                    )
            );
            revokedAtBySessionId.put(sessionId, revokedAt);
        }

        public StoredRefreshSession createdSession(String sessionId) {
            return createdSessionsById.get(sessionId);
        }

        public Map<String, StoredRefreshSession> createdSessionsById() {
            return createdSessionsById;
        }

        public Map<String, Instant> revokedAtBySessionId() {
            return revokedAtBySessionId;
        }
    }

    public static final class InMemoryAuthIdentityStore implements AuthIdentityStore {

        private final Map<String, UserIdentityRecord> identitiesByProviderSubject = new LinkedHashMap<>();
        private final List<UserIdentityRecord> saveOrder = new ArrayList<>();

        @Override
        public UserIdentityRecord findByProviderSubject(String provider, String providerSubject) {
            return identitiesByProviderSubject.get(providerSubjectKey(provider, providerSubject));
        }

        @Override
        public UserIdentityRecord findByUserId(String userId) {
            UserIdentityRecord latest = null;
            for (UserIdentityRecord identityRecord : saveOrder) {
                if (identityRecord.userId().equals(userId)) {
                    latest = identityRecord;
                }
            }
            return latest;
        }

        @Override
        public UserIdentityRecord findByEmail(String email) {
            if (email == null) {
                return null;
            }
            String normalizedEmail = email.toLowerCase(Locale.ROOT);
            UserIdentityRecord latest = null;
            for (UserIdentityRecord identityRecord : saveOrder) {
                if (identityRecord.email() != null && identityRecord.email().toLowerCase(Locale.ROOT).equals(normalizedEmail)) {
                    latest = identityRecord;
                }
            }
            return latest;
        }

        @Override
        public void save(UserIdentityRecord identityRecord) {
            identitiesByProviderSubject.put(providerSubjectKey(identityRecord.provider(), identityRecord.providerSubject()), identityRecord);
            saveOrder.add(identityRecord);
        }

        private String providerSubjectKey(String provider, String providerSubject) {
            return provider + ":" + providerSubject;
        }
    }

    public static final class RecordingUserStore implements UserStore {

        private final Map<String, User> usersById = new LinkedHashMap<>();
        private final Map<String, User> usersByUsername = new LinkedHashMap<>();
        private final List<String> createdUserIds = new ArrayList<>();
        private final List<String> updatedUserIds = new ArrayList<>();
        private int nextUserId = 1;

        public void add(User user) {
            usersById.put(user.userId(), user);
            usersByUsername.put(normalize(user.username()), user);
        }

        @Override
        public User ping(String userId) {
            return usersById.get(userId);
        }

        @Override
        public User findUser(String userId) {
            return usersById.get(userId);
        }

        @Override
        public List<User> findUsers(Collection<String> userIds) {
            List<User> users = new ArrayList<>();
            for (String userId : userIds) {
                User user = usersById.get(userId);
                if (user != null) {
                    users.add(user);
                }
            }
            return users;
        }

        @Override
        public User findByUsername(String username) {
            return usersByUsername.get(normalize(username));
        }

        @Override
        public String createUser(String username, String firstName, String lastName) {
            String userId = "user-" + nextUserId++;
            createdUserIds.add(userId);
            add(new User(userId, normalize(username), firstName, lastName, null));
            return userId;
        }

        @Override
        public void updateUser(String userId, String username, String firstName, String lastName) {
            updatedUserIds.add(userId);
            add(new User(userId, normalize(username), firstName, lastName, null));
        }

        public List<String> createdUserIds() {
            return createdUserIds;
        }

        public List<String> updatedUserIds() {
            return updatedUserIds;
        }

        private String normalize(String username) {
            return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
        }
    }

    public static final class FixedOAuthProviderAuthenticator implements OAuthProviderAuthenticator {

        private final String provider;
        private final ExternalUserIdentity identity;
        private OAuthLoginRequest lastRequest;

        public FixedOAuthProviderAuthenticator(String provider, ExternalUserIdentity identity) {
            this.provider = provider;
            this.identity = identity;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public ExternalUserIdentity authenticate(OAuthLoginRequest request) {
            this.lastRequest = request;
            return identity;
        }

        public OAuthLoginRequest lastRequest() {
            return lastRequest;
        }
    }

    public record StoredAccessToken(
            String tokenHash,
            String userId,
            String provider,
            String providerSubject,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }

    public record StoredRefreshSession(
            String sessionId,
            String tokenHash,
            String userId,
            String provider,
            String providerSubject,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt
    ) {

        public RefreshSessionRecord toRecord() {
            return new RefreshSessionRecord(sessionId, userId, provider, providerSubject, expiresAt, revokedAt);
        }
    }
}
