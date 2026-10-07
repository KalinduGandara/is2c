package com.is2c.hrm.security.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.ScopeItem;

/**
 * Loads the request principal (roles, permissions, scope) for a user id with a short cache, so permission
 * changes take effect quickly without a DB round-trip per request.
 */
@Component
public class PrincipalLoader {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final AppUserRepository users;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();

    public PrincipalLoader(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** Empty when the user no longer exists or is inactive. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> load(UUID userId) {
        Instant now = clock.instant();
        Cached cached = cache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.user();
        }
        Optional<AuthenticatedUser> loaded = users.findById(userId).filter(AppUser::isActive).map(PrincipalLoader::toPrincipal);
        cache.put(userId, new Cached(loaded, now.plus(TTL)));
        return loaded;
    }

    public void evict(UUID userId) {
        cache.remove(userId);
    }

    public void evictAll() {
        cache.clear();
    }

    static AuthenticatedUser toPrincipal(AppUser u) {
        Set<String> roles = u.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
        Set<String> perms = roles.contains(Role.SUPER_ADMIN)
                ? Permissions.allAsSet()
                : u.getRoles().stream().flatMap(r -> r.getPermissions().stream())
                        .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmployeeId(),
                u.isMustChangePassword(), roles, perms, u.getScopeType(),
                ids(u, ScopeItem.ScopeItemType.COMPANY), ids(u, ScopeItem.ScopeItemType.BRANCH),
                ids(u, ScopeItem.ScopeItemType.DEPARTMENT));
    }

    private static Set<UUID> ids(AppUser u, ScopeItem.ScopeItemType type) {
        Set<UUID> result = new HashSet<>();
        u.getScopeItems().stream().filter(i -> i.type() == type).forEach(i -> result.add(i.itemId()));
        return Set.copyOf(result);
    }

    private record Cached(Optional<AuthenticatedUser> user, Instant expiresAt) {
    }
}
