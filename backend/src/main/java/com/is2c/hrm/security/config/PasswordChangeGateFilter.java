package com.is2c.hrm.security.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.is2c.hrm.security.api.CurrentUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** FR-003: while a password change is required, only change-password, logout and me are allowed. */
public class PasswordChangeGateFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("/api/v1/auth/change-password", "/api/v1/auth/logout",
            "/api/v1/auth/me");

    private final ProblemResponseWriter writer;

    public PasswordChangeGateFilter(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var user = CurrentUser.find();
        if (user.isPresent() && user.get().mustChangePassword() && !ALLOWED.contains(request.getRequestURI())) {
            writer.write(response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "You must change your password before continuing");
            return;
        }
        chain.doFilter(request, response);
    }
}
