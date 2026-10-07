package com.is2c.hrm.security.config;

import java.util.stream.Stream;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.is2c.hrm.security.api.AuthenticatedUser;

/** Authentication whose principal is the loaded {@link AuthenticatedUser}; name = user id. */
public class HrmAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;

    public HrmAuthenticationToken(AuthenticatedUser user) {
        super(Stream.concat(user.permissions().stream(), user.roles().stream().map(r -> "ROLE_" + r))
                .map(SimpleGrantedAuthority::new).toList());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.id().toString();
    }
}
