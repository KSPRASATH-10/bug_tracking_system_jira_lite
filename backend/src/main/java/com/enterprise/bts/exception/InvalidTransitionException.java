package com.enterprise.bts.exception;

/** An illegal workflow transition, or a missing/unexpected resolution. Maps to HTTP 400. */
public class InvalidTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTransitionException(String message) {
        super(message, null, false, false);
    }
}
