package com.enterprise.bts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed, validated application configuration. The application refuses to start
 * if a required secret is missing or too weak.
 */
@Validated
@ConfigurationProperties(prefix = "bts")
public record BtsProperties(
        @Valid @DefaultValue Security security,
        @Valid @DefaultValue RateLimit rateLimit,
        @Valid @DefaultValue Storage storage) {

    public record Security(
            @NotBlank(message = "bts.security.jwt-secret (BTS_JWT_SECRET) must be set")
            @Size(min = 32, message = "bts.security.jwt-secret must be at least 32 characters (256 bits)")
            String jwtSecret,
            @Min(1) @Max(1440) @DefaultValue("30") long jwtTtlMinutes,
            @DefaultValue("true") boolean cookieSecure,
            @NotBlank @DefaultValue("BTS_AUTH") String cookieName,
            @NotBlank @DefaultValue("http://localhost:5173") String corsAllowedOrigin) {
    }

    public record RateLimit(
            @Min(1) @DefaultValue("5") int loginCapacity,
            @Min(1) @DefaultValue("60") long loginRefillPeriodSeconds) {
    }

    public record Storage(
            @NotBlank @DefaultValue("./var/uploads") String uploadDir,
            @Min(1) @Max(5242880) @DefaultValue("5242880") long maxFileBytes) {
    }
}
