package com.puduvandi.exception;

/**
 * Thrown when a business rule is violated.
 * Example: Booking a bike that is already reserved. (HTTP 400)
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
