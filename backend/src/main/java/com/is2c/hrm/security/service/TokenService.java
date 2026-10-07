package com.is2c.hrm.security.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.is2c.hrm.common.config.HrmProperties;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.RefreshToken;
import com.is2c.hrm.security.domain.RefreshTokenRepository;

@Service
public class TokenService {

    public static final String ISSUER = "hrm";

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final HrmProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, HrmProperties props, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.clock = clock;
    }

    public String issueAccessToken(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTokenTtl()))
                .claim("name", user.getUsername())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Creates and stores (hashed) a new refresh token; returns the raw value. */
    public String issueRefreshToken(AppUser user) {
        Instant now = clock.instant();
        String raw = TokenHashing.randomToken(32);
        refreshTokens.save(new RefreshToken(user.getId(), TokenHashing.sha256(raw), now,
                now.plus(props.jwt().refreshTokenTtl())));
        return raw;
    }

    public long accessTokenTtlSeconds() {
        return props.jwt().accessTokenTtl().toSeconds();
    }
}
