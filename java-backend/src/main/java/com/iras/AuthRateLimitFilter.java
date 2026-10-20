package com.iras;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/api/auth/login", "/api/auth/register",
            "/IRAS/UserLogin/", "/IRAS/UserRegister/", "/IRAS/AdminLogin/");
    private record Window(long expiresAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final int limit;
    private final int capacity;
    private final Clock clock;

    AuthRateLimitFilter(int limit, int capacity, Clock clock) {
        if (limit < 1 || capacity < 1) throw new IllegalArgumentException("Invalid authentication rate limit");
        this.limit = limit;
        this.capacity = capacity;
        this.clock = clock;
    }

    synchronized long retryAfter(String address) {
        long now = clock.millis();
        var window = windows.get(address);
        if (window == null || window.expiresAt() <= now) {
            if (windows.size() >= capacity) windows.values().removeIf(value -> value.expiresAt() <= now);
            if (window == null && windows.size() >= capacity) return 60;
            windows.put(address, new Window(now + 60_000, 1));
            return 0;
        }
        if (window.count() >= limit) return Math.max(1, (window.expiresAt() - now + 999) / 1000);
        windows.put(address, new Window(window.expiresAt(), window.count() + 1));
        return 0;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !request.getMethod().equals("POST") || !PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retry = retryAfter(request.getRemoteAddr());
        if (retry == 0) { chain.doFilter(request, response); return; }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setContentType("application/json");
        response.getWriter().write("{\"msg\":\"error\",\"code\":429,\"detail\":\"Too many authentication requests; retry later\"}");
    }
}