package com.is2c.hrm.security.api;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.is2c.hrm.common.error.HrmException;

/** Static accessor for the authenticated principal of the current request/thread. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser get() {
        return find().orElseThrow(() -> new HrmException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication required"));
    }
}
