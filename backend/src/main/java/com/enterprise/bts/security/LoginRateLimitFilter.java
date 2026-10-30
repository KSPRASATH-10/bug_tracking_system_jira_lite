package com.enterprise.bts.security;

import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory token bucket per client address, applied to POST /api/auth/login only. Each attempt costs one
 * token; tokens refill continuously (capacity per refill period). An exhausted bucket yields HTTP 429
 * with a Retry-After header. The key is the TCP peer address: X-Forwarded-For is intentionally not
 * trusted, because clients could forge it to dodge the limit.
 *
 * Instantiated by SecurityConfig (not a @Component) so it runs only inside the security filter chain.
 * Limitation, by design of the brief: state is per JVM instance (use a shared store for multi-node).
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";
    private static final int MAX_TRACKED_CLIENTS = 50_000;
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    private final int capacity;
    private final double refillPerNano;
    private final ObjectMapper objectMapper;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(int capacity, long refillPeriodSeconds, ObjectMapper objectMapper) {
        this(capacity, refillPeriodSeconds, objectMapper, System::nanoTime);
    }

    /** Test seam: inject a controllable clock. */
    public LoginRateLimitFilter(int capacity, long refillPeriodSeconds, ObjectMapper objectMapper,
                                LongSupplier nanoClock) {
        if (capacity < 1 || refillPeriodSeconds < 1) {
            throw new IllegalArgumentException("capacity and refillPeriodSeconds must be >= 1");
        }
        this.capacity = capacity;
        this.refillPerNano = (double) capacity / TimeUnit.SECONDS.toNanos(refillPeriodSeconds);
        this.objectMapper = objectMapper;
        this.nanoClock = nanoClock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = nanoClock.getAsLong();
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isFull(now, capacity, refillPerNano));
        }
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), key -> new Bucket(capacity, now));
        long waitNanos = bucket.tryConsume(now, capacity, refillPerNano);
        if (waitNanos == 0) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L);
        log.warn("Login rate limit exceeded for client {}", request.getRemoteAddr());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "RATE_LIMITED", "Too many login attempts. Try again later.",
                CorrelationIdFilter.current(), request.getRequestURI(), null));
    }

    /** Visible for tests. */
    int trackedClients() {
        return buckets.size();
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefill;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        /** Returns 0 if a token was consumed, otherwise the nanoseconds until one becomes available. */
        synchronized long tryConsume(long now, int capacity, double refillPerNano) {
            refill(now, capacity, refillPerNano);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return 0L;
            }
            return (long) Math.ceil((1.0 - tokens) / refillPerNano);
        }

        synchronized boolean isFull(long now, int capacity, double refillPerNano) {
            refill(now, capacity, refillPerNano);
            return tokens >= capacity;
        }

        private void refill(long now, int capacity, double refillPerNano) {
            long elapsed = Math.max(0L, now - lastRefill);
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefill = now;
        }
    }
}
