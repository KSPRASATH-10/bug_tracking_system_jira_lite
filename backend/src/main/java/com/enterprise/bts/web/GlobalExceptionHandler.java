package com.enterprise.bts.web;

import com.enterprise.bts.exception.FileRejectedException;
import com.enterprise.bts.exception.InvalidTransitionException;
import com.enterprise.bts.exception.NotFoundException;
import com.enterprise.bts.service.AuditService;
import com.enterprise.bts.web.dto.ApiError;
import com.enterprise.bts.web.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single place where exceptions become HTTP responses. Every body is the {@link ApiError} envelope with
 * the request's correlation id. No stack trace, SQL, exception class name or framework message ever
 * reaches the client; the full detail is logged server-side next to the same correlation id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    // ------------------------------------------------------------ 400: validation and malformed input

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent("_", error.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            fieldErrors.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
        });
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex,
                                                           HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, null);
    }

    /** Unparseable JSON, unknown properties (fail-on-unknown-properties) and invalid enum values all land here. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getClass().getSimpleName());
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed",
                request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing", request, null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex,
                                                      HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PART",
                "Required part '" + ex.getRequestPartName() + "' is missing", request, null);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidTransitionException ex,
                                                            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_TRANSITION", ex.getMessage(), request, null);
    }

    @ExceptionHandler(FileRejectedException.class)
    public ResponseEntity<ApiError> handleFileRejected(FileRejectedException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "FILE_REJECTED", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                         HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The upload exceeds the maximum allowed size of 5 MB", request, null);
    }

    // ------------------------------------------------------------ 401 / 403

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required", request, null);
    }

    /**
     * Raised by @PreAuthorize (BOLA/IDOR gate) and by service-level role checks. Handled here because
     * @RestControllerAdvice sees the exception before the security filter chain would. Every denial is
     * written to the append-only audit log.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String method = clean(request.getMethod(), 10);
        String path = clean(request.getRequestURI(), 200);
        log.warn("Access denied: {} {}", method, path);
        try {
            auditService.record("ACCESS_DENIED", "HTTP", null, Map.of("method", method, "path", path));
        } catch (RuntimeException auditFailure) {
            log.error("Could not write ACCESS_DENIED audit record", auditFailure);
        }
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action", request, null);
    }

    // ------------------------------------------------------------ 404 / 409

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    /** Optimistic locking: the caller edited a stale version of the bug. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest request) {
        log.info("Optimistic lock conflict on {} {}", clean(request.getMethod(), 10),
                clean(request.getRequestURI(), 200));
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This item was changed by someone else. Reload the latest version and try again.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Constraint names and SQL text stay in the server log only.
        log.warn("Data integrity violation ({})", ex.getMostSpecificCause().getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data", request, null);
    }

    /** Business-rule violations raised by the services (400 / 409), always with a service-authored reason. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            return handleUnexpected(ex, request);
        }
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        String code = switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case CONFLICT -> "CONFLICT";
            default -> status.name();
        };
        return respond(status, code, reason, request, null);
    }

    // ------------------------------------------------------------ catch-all

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Framework 4xx exceptions (404 for unknown URLs, 405, 406, 415, ...) keep their status, with a generic text.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            HttpStatus effective = status != null ? status : HttpStatus.BAD_REQUEST;
            return respond(effective, effective.name(), effective.getReasonPhrase(), request, null);
        }
        log.error("Unhandled exception on {} {}", clean(request.getMethod(), 10),
                clean(request.getRequestURI(), 200), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the correlation id when contacting support.", request, null);
    }

    // ------------------------------------------------------------ helpers

    private static ResponseEntity<ApiError> respond(HttpStatus status, String code, String message,
                                                    HttpServletRequest request, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                CorrelationIdFilter.current(), request.getRequestURI(),
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** Strips control characters (log forging) and truncates untrusted text before logging or auditing it. */
    private static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String safe = value.replaceAll("[\\r\\n\\t]", "_");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
