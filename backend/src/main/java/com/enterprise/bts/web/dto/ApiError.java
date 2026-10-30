package com.enterprise.bts.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Uniform error envelope (RFC 7807-inspired). Never carries stack traces, SQL, or exception class names;
 * the correlationId lets support staff find the matching server-side log line.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String correlationId,
        String path,
        Map<String, String> fieldErrors) {
}
