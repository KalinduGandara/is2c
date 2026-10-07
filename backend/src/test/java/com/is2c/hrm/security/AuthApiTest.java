package com.is2c.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import com.is2c.hrm.common.config.HrmProperties;
import com.is2c.hrm.security.config.JwtConfigAccess;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.security.service.AdminBootstrap;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.TestData;

import tools.jackson.databind.JsonNode;

class AuthApiTest extends ApiTestBase {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired
    AppUserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    AdminBootstrap bootstrap;

    /** Each test logs in from its own IP so the login rate limit does not interfere. */
    private final String ip = "10.1." + (IP.get() / 250) + "." + (IP.getAndIncrement() % 250 + 1);

    private RequestPostProcessor fromIp() {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(fromIp()).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("username", username, "password", password))));
    }

    private JsonNode loginOk(String username) throws Exception {
        return body(login(username, TestData.PASSWORD).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("FR-001 valid credentials return access and refresh tokens usable on /me")
    void loginSuccess() throws Exception {
        data.user("alice", "HR_MANAGER");
        JsonNode tokens = loginOk("ALICE");
        assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(tokens.get("refreshToken").asString()).isNotBlank();

        getAs("Bearer " + tokens.get("accessToken").asString(), "/api/v1/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles", hasItem("HR_MANAGER")))
                .andExpect(jsonPath("$.permissions", hasItem("EMPLOYEE:VIEW")));
    }

    @Test
    @DisplayName("FR-001 wrong password and unknown user give the same generic 401")
    void invalidCredentials() throws Exception {
        data.user("bob", "EMPLOYEE");
        login("bob", "Wrong#Password1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        login("nobody", "Wrong#Password1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    @DisplayName("FR-001 five failures lock the account for 15 minutes, even for the right password")
    void lockout() throws Exception {
        data.user("carol", "EMPLOYEE");
        for (int i = 0; i < 5; i++) {
            login("carol", "Wrong#Password1").andExpect(status().isUnauthorized());
        }
        login("carol", TestData.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        clock.advance(Duration.ofMinutes(14));
        login("carol", TestData.PASSWORD).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        clock.advance(Duration.ofMinutes(2));
        login("carol", TestData.PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-001 a successful login resets the failure counter")
    void successResetsCounter() throws Exception {
        data.user("dan", "EMPLOYEE");
        for (int i = 0; i < 4; i++) {
            login("dan", "Wrong#Password1");
        }
        loginOk("dan");
        for (int i = 0; i < 4; i++) {
            login("dan", "Wrong#Password1");
        }
        loginOk("dan");
    }

    @Test
    @DisplayName("FR-001 deactivated users cannot log in and their tokens stop working")
    void inactiveUser() throws Exception {
        AppUser u = data.user("erin", "EMPLOYEE");
        String bearer = data.bearer(u);
        tx.executeWithoutResult(s -> users.findById(u.getId()).orElseThrow().setActive(false));
        login("erin", TestData.PASSWORD).andExpect(status().isUnauthorized());
        data.bearer(u); // evicts cached principal
        getAs(bearer, "/api/v1/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-001 API calls without a valid token get 401 problem+json")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        getAs("Bearer not-a-jwt", "/api/v1/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-002 refresh rotates tokens and revokes the presented one")
    void refreshRotation() throws Exception {
        data.user("fay", "EMPLOYEE");
        String refresh1 = loginOk("fay").get("refreshToken").asString();
        JsonNode second = body(postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh1))
                .andExpect(status().isOk()));
        String refresh2 = second.get("refreshToken").asString();
        assertThat(refresh2).isNotEqualTo(refresh1);
        getAs("Bearer " + second.get("accessToken").asString(), "/api/v1/auth/me").andExpect(status().isOk());

        // Reusing the revoked token is rejected and revokes every session of the user.
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh1)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh2)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-002 expired refresh tokens are rejected")
    void refreshExpired() throws Exception {
        data.user("gus", "EMPLOYEE");
        String refresh = loginOk("gus").get("refreshToken").asString();
        clock.advance(Duration.ofDays(7).plusSeconds(1));
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-002 logout revokes the refresh token; tokens are stored only as SHA-256 hashes")
    void logoutAndHashing() throws Exception {
        data.user("hal", "EMPLOYEE");
        String refresh = loginOk("hal").get("refreshToken").asString();
        Integer raw = jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class, refresh);
        assertThat(raw).isZero();
        assertThat(jdbc.queryForObject("SELECT length(token_hash) FROM refresh_token", Integer.class)).isEqualTo(64);

        postAs(null, "/api/v1/auth/logout", Map.of("refreshToken", refresh)).andExpect(status().isNoContent());
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-003 password change enforces the policy and requires the current password")
    void changePassword() throws Exception {
        AppUser u = data.user("ivy", "EMPLOYEE");
        String bearer = data.bearer(u);
        postAs(bearer, "/api/v1/auth/change-password", Map.of("currentPassword", "nope", "newPassword", "New#Passw0rd!"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        postAs(bearer, "/api/v1/auth/change-password", Map.of("currentPassword", TestData.PASSWORD, "newPassword", "short"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].message", hasItem("must be at least 10 characters")));
        postAs(bearer, "/api/v1/auth/change-password",
                Map.of("currentPassword", TestData.PASSWORD, "newPassword", "alllowercase1!"))
                .andExpect(jsonPath("$.errors[*].message", hasItem("must contain an upper-case letter")));
        postAs(bearer, "/api/v1/auth/change-password",
                Map.of("currentPassword", TestData.PASSWORD, "newPassword", "NoSymbols123"))
                .andExpect(jsonPath("$.errors[*].message", hasItem("must contain a non-alphanumeric character")));
        postAs(bearer, "/api/v1/auth/change-password",
                Map.of("currentPassword", TestData.PASSWORD, "newPassword", "New#Passw0rd!"))
                .andExpect(status().isNoContent());
        login("ivy", "New#Passw0rd!").andExpect(status().isOk());
        String hash = users.findById(u.getId()).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    @DisplayName("FR-003 must-change-password blocks other APIs until the password is changed")
    void mustChangeGate() throws Exception {
        AppUser u = data.user("jay", "HR_MANAGER");
        tx.executeWithoutResult(s -> users.findById(u.getId()).orElseThrow().setMustChangePassword(true));
        String bearer = data.bearer(u);
        getAs(bearer, "/api/v1/test-secured/employee-view").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        getAs(bearer, "/api/v1/auth/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        postAs(bearer, "/api/v1/auth/change-password",
                Map.of("currentPassword", TestData.PASSWORD, "newPassword", "New#Passw0rd!"))
                .andExpect(status().isNoContent());
        getAs(bearer, "/api/v1/test-secured/employee-view").andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-004 first start creates the super admin with forced password change")
    void bootstrapAdmin() throws Exception {
        bootstrap.run(null);
        AppUser admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getRoles()).extracting("code").containsExactly("SUPER_ADMIN");
        bootstrap.run(null); // idempotent once users exist
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-004 start-up fails clearly when no users exist and no admin credentials are configured")
    void bootstrapRequiresCredentials() {
        HrmProperties props = new HrmProperties(null, null, null, null, null, null, null, false);
        AdminBootstrap noCreds = new AdminBootstrap(users, null, null, props);
        assertThatThrownBy(() -> noCreds.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_ADMIN_USERNAME");
    }

    @Test
    @DisplayName("NFR-002 prod profile refuses to start without a strong JWT secret")
    void prodRequiresSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        HrmProperties weak = new HrmProperties(null, null, new HrmProperties.Jwt("short", null, null), null, null,
                null, null, false);
        assertThatThrownBy(() -> JwtConfigAccess.signingKey(weak, env)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_JWT_SECRET");
        assertThat(JwtConfigAccess.signingKey(weak, new MockEnvironment())).isNotNull();
    }

    @Test
    @DisplayName("NFR-019 security headers are set on API responses")
    void securityHeaders() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    @DisplayName("NFR-019 more than 20 login attempts per minute from one IP get 429")
    void loginRateLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            login("nobody", "x").andExpect(status().isUnauthorized());
        }
        login("nobody", "x").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        clock.advance(Duration.ofSeconds(61));
        login("nobody", "x").andExpect(status().isUnauthorized());
    }
}
