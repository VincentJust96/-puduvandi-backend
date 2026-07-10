package com.puduvandi.exception;

/**
 * Thrown when an authenticated user tries to access a resource
 * they do not have permission for. (HTTP 403)
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
