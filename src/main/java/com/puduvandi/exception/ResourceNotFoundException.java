package com.puduvandi.exception;

// ===== ResourceNotFoundException =====
// Thrown when an entity is not found in the database (HTTP 404)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}
