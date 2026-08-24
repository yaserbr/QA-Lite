package com.mobily.qalite.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int LOGIN_LIMIT = 10;
    private static final int REGISTER_LIMIT = 5;

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthenticationRateLimitFilter() {
        this(Clock.systemUTC());
    }

    AuthenticationRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int limit = limitFor(request);

        if (limit > 0 && isRateLimited(request, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain");
            response.getWriter().write("Too many attempts. Please try again later.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(HttpServletRequest request, int limit) {
        String key = request.getRemoteAddr() + ":" + request.getServletPath();
        long now = clock.millis();
        AttemptWindow window = attempts.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAt() >= WINDOW_MILLIS) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(existing.startedAt(), existing.count() + 1);
        });

        return window.count() > limit;
    }

    private static int limitFor(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return 0;
        }

        return switch (request.getServletPath()) {
            case "/login" -> LOGIN_LIMIT;
            case "/register" -> REGISTER_LIMIT;
            default -> 0;
        };
    }

    private record AttemptWindow(long startedAt, int count) {
    }
}
