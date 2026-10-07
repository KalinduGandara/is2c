package com.is2c.hrm.security.config;

import javax.crypto.SecretKey;

import org.springframework.core.env.Environment;

import com.is2c.hrm.common.config.HrmProperties;

/** Test access to the package-private signing-key factory. */
public final class JwtConfigAccess {

    private JwtConfigAccess() {
    }

    public static SecretKey signingKey(HrmProperties props, Environment env) {
        return new JwtConfig().jwtSigningKey(props, env);
    }
}
