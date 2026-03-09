package com.ecommerce.unittesting.exception;

// Custom exception for scenarios where a resource cannot be deleted
// because other resources depend on it (e.g., user has products)
// Caught by GlobalExceptionHandler → returns 409 Conflict response
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
