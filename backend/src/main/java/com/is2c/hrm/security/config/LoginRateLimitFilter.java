package com.is2c.hrm.security.config;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** NFR-019: at most {@code limitPerMinute} login attempts per client IP in a sliding minute, else 429. */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final long WINDOW_MILLIS = 60_000;

    private final int limitPerMinute;
    private final Clock clock;
    private final ProblemResponseWriter writer;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(int limitPerMinute, Clock clock, ProblemResponseWriter writer) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        Deque<Long> window = hits.computeIfAbsent(request.getRemoteAddr(), k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= now - WINDOW_MILLIS) {
                window.pollFirst();
            }
            allowed = window.size() < limitPerMinute;
            if (allowed) {
                window.addLast(now);
            }
        }
        if (!allowed) {
            response.setHeader("Retry-After", "60");
            writer.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many login attempts. Try again in a minute.");
            return;
        }
        chain.doFilter(request, response);
    }
}
