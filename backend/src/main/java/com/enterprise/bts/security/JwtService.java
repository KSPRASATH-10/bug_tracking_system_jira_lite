package com.enterprise.bts.security;

import com.enterprise.bts.config.BtsProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies short-lived HS256 access tokens. The token carries only the user id (subject);
 * authorities are re-read from the database on each request, so a stale token can never retain
 * privileges that were revoked.
 */
@Service
public class JwtService {

    public static final String ISSUER = "enterprise-jira-lite";
    private static final String REQUIRED_ALGORITHM = "HS256";
    private static final int MAX_TOKEN_LENGTH = 2048;
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;
    private final JwtParser parser;

    @Autowired
    public JwtService(BtsProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtService(BtsProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(properties.security().jwtTtlMinutes());
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(5)
                .build();
    }

    public String issueToken(UUID userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Returns the user id if (and only if) the token is well-formed, correctly signed, unexpired and HS256. */
    public Optional<UUID> parseUserId(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!REQUIRED_ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            String subject = jws.getPayload().getSubject();
            return subject == null ? Optional.empty() : Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            // Class name only: never log the token or the parser's message.
            log.debug("Rejected JWT ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }
}
