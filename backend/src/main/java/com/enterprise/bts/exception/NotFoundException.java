package com.enterprise.bts.exception;

/**
 * The resource does not exist, or is not visible inside the scope the caller was authorised for.
 * Maps to HTTP 404. Deliberately identical for "missing" and "belongs to another project", so ids
 * cannot be probed across project boundaries.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message, null, false, false);   // no stack trace: this is an expected, cheap condition
    }
}
