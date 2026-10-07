package com.is2c.testsupport;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.domain.RoleRepository;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.service.PrincipalLoader;
import com.is2c.hrm.security.service.TokenService;

/** Builders for test fixtures. All users share {@link #PASSWORD}; its hash is computed once. */
@Component
public class TestData {

    public static final String PASSWORD = "Str0ng#Password";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final PrincipalLoader principals;
    private final TransactionTemplate tx;
    private String passwordHash;

    public TestData(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder, TokenService tokens,
            PrincipalLoader principals, TransactionTemplate tx) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.tokens = tokens;
        this.principals = principals;
        this.tx = tx;
    }

    public AppUser user(String username, String... roleCodes) {
        return user(username, ScopeType.ALL, Set.of(), roleCodes);
    }

    public AppUser user(String username, ScopeType scope, Set<ScopeItem> scopeItems, String... roleCodes) {
        return tx.execute(s -> {
            AppUser u = new AppUser(username, username + " name", passwordHash());
            u.setScopeType(scope);
            u.getScopeItems().addAll(scopeItems);
            u.getRoles().addAll(roles.findByCodeIn(Arrays.asList(roleCodes)));
            return users.save(u);
        });
    }

    public AppUser linkEmployee(AppUser user, UUID employeeId) {
        return tx.execute(s -> {
            AppUser u = users.findById(user.getId()).orElseThrow();
            u.setEmployeeId(employeeId);
            principals.evict(u.getId());
            return users.save(u);
        });
    }

    private synchronized String passwordHash() {
        if (passwordHash == null) {
            passwordHash = encoder.encode(PASSWORD);
        }
        return passwordHash;
    }

    /** Runs code with the given user as the authenticated principal (for calling services directly). */
    public <T> T runAs(AppUser user, java.util.function.Supplier<T> action) {
        principals.evict(user.getId());
        var principal = principals.load(user.getId()).orElseThrow();
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new com.is2c.hrm.security.config.HrmAuthenticationToken(principal));
        try {
            return action.get();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    /** Bearer header value for the user without going through the login endpoint. */
    public String bearer(AppUser user) {
        principals.evict(user.getId());
        return "Bearer " + tokens.issueAccessToken(user);
    }

    public String bearerFor(String username, String... roleCodes) {
        return bearer(user(username, roleCodes));
    }
}
