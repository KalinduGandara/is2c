package com.is2c.hrm.security.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.error.HrmException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.RefreshToken;
import com.is2c.hrm.security.domain.RefreshTokenRepository;

/** Login, token refresh, logout and password change (FR-001..003). */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final SecurityEvents events;
    private final PrincipalLoader principals;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
            PasswordEncoder passwordEncoder, SecurityEvents events, PrincipalLoader principals, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.principals = principals;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn) {
    }

    @Transactional(noRollbackFor = HrmException.class)
    public Tokens login(String username, String password) {
        Instant now = clock.instant();
        AppUser user = users.findByUsernameIgnoreCase(username == null ? "" : username.trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            events.publish(SecurityEvent.Type.LOGIN_FAILURE, null, username, "unknown user");
            throw invalidCredentials();
        }
        if (user.isLockedAt(now)) {
            events.publish(SecurityEvent.Type.LOGIN_FAILURE, user.getId(), user.getUsername(), "account locked");
            throw new HrmException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED",
                    "The account is temporarily locked. Try again later.");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            user.recordFailedLogin(now, MAX_FAILED_ATTEMPTS, LOCK_DURATION);
            if (user.isLockedAt(now)) {
                events.publish(SecurityEvent.Type.ACCOUNT_LOCKED, user.getId(), user.getUsername(),
                        "locked after " + MAX_FAILED_ATTEMPTS + " failed attempts");
            } else {
                events.publish(SecurityEvent.Type.LOGIN_FAILURE, user.getId(), user.getUsername(), "bad password");
            }
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            events.publish(SecurityEvent.Type.LOGIN_FAILURE, user.getId(), user.getUsername(), "inactive user");
            throw invalidCredentials();
        }
        user.recordSuccessfulLogin(now);
        events.publish(SecurityEvent.Type.LOGIN_SUCCESS, user.getId(), user.getUsername(), null);
        return new Tokens(tokens.issueAccessToken(user), tokens.issueRefreshToken(user), tokens.accessTokenTtlSeconds());
    }

    @Transactional(noRollbackFor = HrmException.class)
    public Tokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(TokenHashing.sha256(rawRefreshToken == null ? "" : rawRefreshToken))
                .orElseThrow(AuthService::invalidRefresh);
        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            events.publish(SecurityEvent.Type.TOKEN_REUSE_DETECTED, token.getUserId(), null,
                    "revoked refresh token presented; all sessions revoked");
            throw invalidRefresh();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw invalidRefresh();
        }
        AppUser user = users.findById(token.getUserId()).filter(AppUser::isActive).orElseThrow(AuthService::invalidRefresh);
        token.revoke(now);
        return new Tokens(tokens.issueAccessToken(user), tokens.issueRefreshToken(user), tokens.accessTokenTtlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return;
        }
        refreshTokens.findByTokenHash(TokenHashing.sha256(rawRefreshToken)).ifPresent(t -> {
            t.revoke(clock.instant());
            events.publish(SecurityEvent.Type.LOGOUT, t.getUserId(), null, null);
        });
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new HrmException(HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication required"));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new ValidationException("currentPassword", "Current password is incorrect");
        }
        PasswordPolicy.check("newPassword", newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ValidationException("newPassword", "New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        principals.evict(userId);
        events.publish(SecurityEvent.Type.PASSWORD_CHANGED, user.getId(), user.getUsername(), null);
    }

    private static HrmException invalidCredentials() {
        return new HrmException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    private static HrmException invalidRefresh() {
        return new HrmException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired. Sign in again.");
    }
}
