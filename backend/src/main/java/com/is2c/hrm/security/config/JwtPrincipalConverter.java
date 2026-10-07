package com.is2c.hrm.security.config;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.is2c.hrm.security.service.PrincipalLoader;

/** Resolves a validated JWT to the current user's roles, permissions and scope. */
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalLoader principals;

    public JwtPrincipalConverter(PrincipalLoader principals) {
        this.principals = principals;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DisabledException("Invalid subject");
        }
        return principals.load(userId).map(HrmAuthenticationToken::new)
                .orElseThrow(() -> new DisabledException("User is inactive or unknown"));
    }
}
