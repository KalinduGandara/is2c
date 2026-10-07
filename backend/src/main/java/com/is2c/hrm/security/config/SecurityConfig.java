package com.is2c.hrm.security.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.is2c.hrm.common.config.HrmProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtPrincipalConverter converter,
            ProblemResponseWriter problems, Clock clock,
            @Value("${hrm.security.login-rate-limit-per-minute:20}") int loginRateLimit) throws Exception {
        http
                .csrf(c -> c.disable()) // stateless bearer-token API; no cookies are used for auth
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/integrations/**").permitAll() // API-key filter guards these
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()) // static SPA assets
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> problems.write(res, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Authentication required")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problems.write(res, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> problems.write(res, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "You do not have permission to perform this action")))
                .addFilterBefore(new LoginRateLimitFilter(loginRateLimit, clock, problems),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new PasswordChangeGateFilter(problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(HrmProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!props.cors().origins().isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(props.cors().origins());
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.addExposedHeader("X-Correlation-Id");
            config.addExposedHeader("Content-Disposition");
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
