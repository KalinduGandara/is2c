package com.is2c.biostarsync;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The UI has no login, so it must not be driven by other web sites the operator visits: requests must name an allowed
 * host (blocks DNS rebinding) and form posts must come from this page (blocks cross-site request forgery).
 */
@Component
class HostGuardFilter extends OncePerRequestFilter {

    private final Set<String> allowedHosts;

    HostGuardFilter(@Value("${sync.allowed-hosts}") String allowedHosts) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(",")).map(h -> h.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String host = req.getServerName().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Host not allowed; see sync.allowed-hosts");
            return;
        }
        if (!"GET".equals(req.getMethod()) && !sameOrigin(req)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site request refused");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Browsers always send Origin on form posts; a missing header means a non-browser client, which is allowed. */
    private static boolean sameOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin == null) {
            return true;
        }
        try {
            URI o = URI.create(origin);
            int port = o.getPort() == -1 ? ("https".equals(o.getScheme()) ? 443 : 80) : o.getPort();
            return req.getServerName().equalsIgnoreCase(o.getHost()) && req.getServerPort() == port;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
