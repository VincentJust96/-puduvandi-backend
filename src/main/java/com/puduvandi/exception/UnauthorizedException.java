package com.puduvandi.exception;

/**
 * Thrown when authentication fails or token is invalid. (HTTP 401)
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
