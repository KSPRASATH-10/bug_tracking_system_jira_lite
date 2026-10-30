package com.enterprise.bts.exception;

/** An upload failed validation (size, type, extension, content mismatch). Maps to HTTP 400. */
public class FileRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileRejectedException(String message) {
        super(message, null, false, false);
    }
}
