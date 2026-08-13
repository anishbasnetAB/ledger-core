package com.anish.banking.bank.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Fixed-window rate limit on POST /api/auth/login and POST /api/transfers only — every
 *  other request passes straight through without touching Redis.
 *
 *  NOT a @Component, for the same reason as JwtAuthFilter: SecurityConfig constructs and
 *  wires it into the security chain directly (addFilterAfter), so it runs after
 *  JwtAuthFilter has had a chance to authenticate the request. Auto-registering it as a
 *  bean would run it a second time, unordered, outside the security chain. */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int loginMaxRequests;
    private final Duration loginWindow;
    private final int transferMaxRequests;
    private final Duration transferWindow;

    public RateLimitFilter(StringRedisTemplate redis, ObjectMapper objectMapper,
                            int loginMaxRequests, Duration loginWindow,
                            int transferMaxRequests, Duration transferWindow) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.loginMaxRequests = loginMaxRequests;
        this.loginWindow = loginWindow;
        this.transferMaxRequests = transferMaxRequests;
        this.transferWindow = transferWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ruleFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        String windowStart = String.valueOf(Instant.now().getEpochSecond() / rule.window().toSeconds());
        String key = "ratelimit:" + identity(request) + ":" + rule.endpoint() + ":" + windowStart;

        long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count == 1) {
                // Only on the window's first request — an INCR on every request would keep
                // pushing the expiry out and turn this into a sliding window.
                redis.expire(key, rule.window());
            }
        } catch (Exception ex) {
            // Fail OPEN: rate limiting is defense in depth, not a correctness guarantee this
            // API depends on. A Redis outage blocking every login/transfer would be a
            // self-inflicted denial of service — worse than just skipping the check.
            log.error("Rate limit check failed for {}, allowing the request through", key, ex);
            chain.doFilter(request, response);
            return;
        }

        if (count > rule.maxRequests()) {
            writeTooManyRequests(response, request.getRequestURI());
            return;
        }

        chain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if ("/api/auth/login".equals(uri)) {
            return new Rule("login", loginMaxRequests, loginWindow);
        }
        if ("/api/transfers".equals(uri)) {
            return new Rule("transfers", transferMaxRequests, transferWindow);
        }
        return null;
    }

    // Authenticated caller (transfers) -> user identity; anonymous caller (login, or a
    // transfer request with no/invalid token) -> IP, so unauthenticated traffic still gets
    // its own bucket instead of sharing one.
    private String identity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        // ponytail: direct remote address only, no X-Forwarded-For handling — behind a
        // reverse proxy (e.g. Render in prod) this would see the proxy's IP, not the
        // client's. Add trusted-proxy header parsing (or Spring's ForwardedHeaderFilter) if
        // this ever needs to be accurate behind one.
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(OffsetDateTime.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "Rate limit exceeded, try again later", path, null);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    private record Rule(String endpoint, int maxRequests, Duration window) {}
}
