package com.enterprise.bts.security;

import com.enterprise.bts.config.BtsProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The access token lives ONLY in an HttpOnly, SameSite=Strict (and Secure) cookie, so page JavaScript
 * (and therefore any XSS payload) can never read it. The cookie is scoped to /api.
 */
@Component
public class AuthCookieService {

    private static final String COOKIE_PATH = "/api";

    private final BtsProperties.Security securityProperties;
    private final JwtService jwtService;

    public AuthCookieService(BtsProperties properties, JwtService jwtService) {
        this.securityProperties = properties.security();
        this.jwtService = jwtService;
    }

    public void writeAuthCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, jwtService.ttl()).toString());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public Optional<String> extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (securityProperties.cookieName().equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(securityProperties.cookieName(), value)
                .httpOnly(true)
                .secure(securityProperties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
