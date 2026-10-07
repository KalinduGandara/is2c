package com.is2c.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.is2c.hrm.security.config.LoginRateLimitFilter;
import com.is2c.hrm.security.config.ProblemResponseWriter;
import com.is2c.testsupport.MutableClock;

import tools.jackson.databind.json.JsonMapper;

class LoginRateLimitFilterTest {

    @Test
    @DisplayName("NFR-019 the login rate limiter forgets clients whose window has expired (bounded memory)")
    void prunesExpiredClients() throws Exception {
        MutableClock clock = new MutableClock();
        LoginRateLimitFilter filter = new LoginRateLimitFilter(20, clock, new ProblemResponseWriter(JsonMapper.builder().build()));
        for (int i = 0; i < 5_000; i++) {
            filter.doFilter(login("10.0." + (i / 250) + "." + (i % 250)), new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(filter.trackedClients()).isEqualTo(5_000);
        clock.set(Instant.parse("2026-03-16T04:32:00Z"));
        filter.doFilter(login("192.168.1.1"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(filter.trackedClients()).isEqualTo(1);
    }

    private static MockHttpServletRequest login(String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        r.setRemoteAddr(ip);
        return r;
    }
}
